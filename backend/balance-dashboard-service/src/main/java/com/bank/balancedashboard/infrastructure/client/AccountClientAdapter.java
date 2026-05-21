package com.bank.balancedashboard.infrastructure.client;

import com.bank.account.client.AccountClientLib;
import com.bank.account.client.AccountInfo;
import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.domain.exception.DashboardUnavailableException;
import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.policy.EligibilityPolicy;
import com.bank.balancedashboard.infrastructure.rest.LogMasking;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Resilience4j-wrapped adapter for {@code AccountClientLib} implementing {@link AccountPort}.
 *
 * <p>Resilience4j operator order (MANDATORY — impl-notes §3):
 * <pre>
 * TimeLimiter (outer) → CircuitBreaker → Retry → Bulkhead (innermost)
 * </pre>
 * In Decorators builder: register innermost first (Bulkhead), then Retry, then CB, then TimeLimiter.
 *
 * <p>Failure mode mapping (all → {@link UpstreamUnavailableException} → HTTP 503):
 * <ul>
 *   <li>TimeoutException — timeLimiter.timeoutDuration exceeded (300ms)</li>
 *   <li>CallNotPermittedException — circuit breaker OPEN</li>
 *   <li>BulkheadFullException — 20 concurrent calls exhausted</li>
 *   <li>HTTP 5xx after retries — wrapped as UpstreamUnavailableException</li>
 * </ul>
 *
 * <p>HTTP 4xx: ignoreExceptions in Resilience4j config — do NOT retry; propagate as-is.
 *
 * <p>This adapter also applies EligibilityPolicy (status=ACTIVE + type filtering) and
 * maps AccountInfo → AccountView, including server-side isStale computation (now - balanceAsOf > 60s).
 */
@Component
public class AccountClientAdapter implements AccountPort {

    private static final Logger log = LoggerFactory.getLogger(AccountClientAdapter.class);
    private static final long STALE_THRESHOLD_SECONDS = 60L;

    private final AccountClientLib accountClientLib;
    private final EligibilityPolicy eligibilityPolicy;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final ScheduledExecutorService scheduler;

    public AccountClientAdapter(
            AccountClientLib accountClientLib,
            EligibilityPolicy eligibilityPolicy,
            TimeLimiter timeLimiter,
            CircuitBreaker circuitBreaker,
            Retry retry,
            Bulkhead bulkhead,
            ScheduledExecutorService scheduler) {
        this.accountClientLib = accountClientLib;
        this.eligibilityPolicy = eligibilityPolicy;
        this.timeLimiter = timeLimiter;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.bulkhead = bulkhead;
        this.scheduler = scheduler;
    }

    /**
     * Fetches, filters, and maps eligible accounts for the customer.
     *
     * <p>Resilience4j operator order (inner to outer in Decorators builder):
     * Bulkhead → Retry → CircuitBreaker → TimeLimiter
     *
     * @param customerId authenticated customer UUID (JWT sub)
     * @return eligibility-filtered, mapped AccountView list (may be empty)
     * @throws UpstreamUnavailableException on any Resilience4j failure outcome
     */
    @Override
    public List<AccountView> fetchAccounts(UUID customerId) {
        try {
            // MANDATORY operator order: TimeLimiter(outer) → CB → Retry → Bulkhead(inner)
            // In Decorators: register innermost first, outermost last.
            List<AccountInfo> rawAccounts = Decorators
                    .ofSupplier(() -> accountClientLib.listAccountsByCustomer(customerId))
                    .withBulkhead(bulkhead)
                    .withRetry(retry)
                    .withCircuitBreaker(circuitBreaker)
                    .withTimeLimiter(timeLimiter, scheduler)
                    .get();

            return mapAndFilter(rawAccounts);

        } catch (TimeoutException e) {
            log.warn("account-client timeout customerId={}", LogMasking.maskId(customerId), e);
            throw new DashboardUnavailableException("account-service timed out", e);
        } catch (CallNotPermittedException e) {
            log.warn("account-client circuit-breaker OPEN customerId={}", LogMasking.maskId(customerId), e);
            throw new DashboardUnavailableException("account-service circuit breaker is open", e);
        } catch (BulkheadFullException e) {
            log.warn("account-client bulkhead full customerId={}", LogMasking.maskId(customerId), e);
            throw new DashboardUnavailableException("account-service bulkhead full", e);
        } catch (UpstreamUnavailableException e) {
            // Wrap infra exception into domain exception before propagating
            throw new DashboardUnavailableException("Account service unavailable", e);
        } catch (RuntimeException e) {
            log.warn("account-client unexpected error customerId={}", LogMasking.maskId(customerId), e);
            throw new DashboardUnavailableException("account-service unavailable", e);
        } catch (Exception e) {
            log.warn("account-client checked exception customerId={}", LogMasking.maskId(customerId), e);
            throw new DashboardUnavailableException("account-service unavailable", e);
        }
    }

    /**
     * Applies EligibilityPolicy and maps AccountInfo → AccountView.
     * isStale is server-computed: now() - balanceAsOf > 60s (BR-013).
     * rank is set to 0 here; Ranker assigns final 1-based rank.
     */
    private List<AccountView> mapAndFilter(List<AccountInfo> rawAccounts) {
        Instant now = Instant.now();
        return rawAccounts.stream()
                .filter(ai -> {
                    try {
                        AccountType type = AccountType.valueOf(ai.getAccountType());
                        return eligibilityPolicy.isEligible(ai.getStatus(), type);
                    } catch (IllegalArgumentException e) {
                        // Unknown accountType (e.g., LOAN, CREDIT_CARD) → exclude
                        return false;
                    }
                })
                .map(ai -> {
                    AccountType type = AccountType.valueOf(ai.getAccountType());
                    Instant balanceAsOf = parseInstantOrNull(ai.getBalanceAsOf(), ai.getAccountId());
                    boolean isStale = balanceAsOf != null
                            && now.getEpochSecond() - balanceAsOf.getEpochSecond() > STALE_THRESHOLD_SECONDS;

                    if (ai.getBalanceAsOf() == null) {
                        log.warn("account.balanceAsOf.null accountId={}", LogMasking.maskId(ai.getAccountId()));
                    }

                    return new AccountView(
                            1, // placeholder rank; Ranker assigns final rank
                            ai.getAccountId(),
                            ai.getAccountNumberMasked(),
                            type,
                            new BigDecimal(ai.getBalance()),
                            ai.getCurrency(),
                            balanceAsOf,
                            isStale,
                            toDisplayLabel(type)
                    );
                })
                .collect(Collectors.toList());
    }

    private Instant parseInstantOrNull(String balanceAsOf, UUID accountId) {
        if (balanceAsOf == null) {
            return null;
        }
        try {
            return Instant.parse(balanceAsOf);
        } catch (Exception e) {
            log.warn("account.balanceAsOf.parse.failed accountId={} value={}", LogMasking.maskId(accountId), balanceAsOf, e);
            return null;
        }
    }

    private String toDisplayLabel(AccountType type) {
        return switch (type) {
            case SAVINGS -> "account.type.savings";
            case CURRENT -> "account.type.current";
            case FIXED_DEPOSIT -> "account.type.fixedDeposit";
        };
    }
}
