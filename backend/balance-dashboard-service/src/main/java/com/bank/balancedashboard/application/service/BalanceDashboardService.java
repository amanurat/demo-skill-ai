package com.bank.balancedashboard.application.service;

import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.application.port.out.CachePort;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Channel;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.model.RankedDashboard;
import com.bank.balancedashboard.domain.policy.Ranker;
import com.bank.balancedashboard.domain.port.in.LoadDashboardUseCase;
import com.bank.balancedashboard.infrastructure.client.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing the {@link LoadDashboardUseCase} primary port.
 *
 * <p>Orchestration sequence (mandatory per task-plan §Layer 3):
 * <ol>
 *   <li>CachePort.get(customerId) — HIT: return cached dashboard + set cacheHit=true</li>
 *   <li>On MISS: AccountPort.fetchAccounts(customerId) [Resilience4j in adapter]</li>
 *   <li>Ranker.rank(accounts)</li>
 *   <li>CachePort.put(customerId, rankedDashboard)</li>
 *   <li>AuditEventPublisher.publish(SUCCESS) [BR-014 — ALWAYS, even on cache hit]</li>
 *   <li>Return RankedDashboard</li>
 * </ol>
 *
 * <p>Error path: upstream throws UpstreamUnavailableException
 * → audit ERROR → re-throw (controller maps to 503).
 *
 * <p>Redis fail-open: if CachePort.get() throws RuntimeException → log + treat as MISS.
 * The fail-open is handled in RedisCacheRepository; this service sees empty Optional.
 *
 * <p>Application layer: only @Service annotation — no @RestController, no Redis/Kafka imports.
 */
@Service
public class BalanceDashboardService implements LoadDashboardUseCase {

    private static final Logger log = LoggerFactory.getLogger(BalanceDashboardService.class);

    private final AccountPort accountPort;
    private final CachePort cachePort;
    private final AuditEventPublisher auditEventPublisher;
    private final Ranker ranker;

    public BalanceDashboardService(AccountPort accountPort,
                                   CachePort cachePort,
                                   AuditEventPublisher auditEventPublisher,
                                   Ranker ranker) {
        this.accountPort = accountPort;
        this.cachePort = cachePort;
        this.auditEventPublisher = auditEventPublisher;
        this.ranker = ranker;
    }

    /**
     * Loads the ranked balance dashboard for the given customer.
     *
     * <p>customerId is always derived from JWT sub by CustomerIdResolver — never from any header.
     *
     * @param customerId authenticated customer UUID (from JWT sub — C-3 compliant)
     * @return ranked dashboard with freshness and correlationId metadata
     * @throws UpstreamUnavailableException if account-service unreachable after Resilience4j exhaustion
     */
    @Override
    public RankedDashboard loadDashboard(UUID customerId) {
        // Generate a correlation ID for this request (replaced by OTel trace ID at controller layer)
        String correlationId = UUID.randomUUID().toString();
        Channel channel = Channel.MOBILE_BANKING; // default for v1; controller can pass via context

        // Step 1: Check cache (fail-open: RedisCacheRepository catches RedisException internally)
        Optional<RankedDashboard> cached = tryGetFromCache(customerId);

        if (cached.isPresent()) {
            // Cache HIT — rebuild meta with current correlationId (BR-014: audit regardless)
            RankedDashboard cachedDashboard = cached.get();
            RankedDashboard withUpdatedMeta = new RankedDashboard(
                    cachedDashboard.accounts(),
                    "snapshot",
                    true,
                    correlationId
            );

            // Step 4 (cache hit path): audit ALWAYS emitted (BR-014)
            auditEventPublisher.publish(
                    AuditEventRecord.success(customerId, correlationId, channel, true,
                            withUpdatedMeta.accountCount())
            );

            log.debug("balance-dashboard cache=HIT customerId={} accountCount={}",
                    customerId, withUpdatedMeta.accountCount());
            return withUpdatedMeta;
        }

        // Cache MISS — fetch from AccountClient (Resilience4j in adapter)
        try {
            // Step 2: fetch from upstream
            List<AccountView> accounts = accountPort.fetchAccounts(customerId);

            // Step 3: rank
            List<AccountView> ranked = ranker.rank(accounts);

            // Build dashboard
            RankedDashboard dashboard = new RankedDashboard(ranked, "live", false, correlationId);

            // Step 4: write to cache (atomic SETEX in RedisCacheRepository)
            cachePort.put(customerId, dashboard);

            // Step 5: audit SUCCESS (BR-014 — cache miss path)
            auditEventPublisher.publish(
                    AuditEventRecord.success(customerId, correlationId, channel, false,
                            dashboard.accountCount())
            );

            log.debug("balance-dashboard cache=MISS customerId={} accountCount={}",
                    customerId, dashboard.accountCount());
            return dashboard;

        } catch (UpstreamUnavailableException e) {
            // Error path: audit ERROR before re-throwing
            auditEventPublisher.publish(
                    AuditEventRecord.error(customerId, correlationId, channel)
            );
            log.warn("balance-dashboard upstream=UNAVAILABLE customerId={} reason={}",
                    customerId, e.reason(), e);
            throw e; // controller maps to 503 Problem-Detail
        }
    }

    /**
     * Wraps CachePort.get() with additional defensive handling.
     * RedisCacheRepository already catches RedisException, but this guards against
     * any unexpected RuntimeException from the cache adapter.
     */
    private Optional<RankedDashboard> tryGetFromCache(UUID customerId) {
        try {
            return cachePort.get(customerId);
        } catch (RuntimeException e) {
            // Redis fail-open (test case 5: redisFailure_failOpen_fetchesAccountClient_emitsAudit)
            log.warn("balance-dashboard cache.get.unexpected-error customerId={} — failing open",
                    customerId, e);
            return Optional.empty();
        }
    }
}
