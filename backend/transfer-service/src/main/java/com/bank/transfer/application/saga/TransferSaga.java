package com.bank.transfer.application.saga;

import com.bank.transfer.application.port.out.AccountClient;
import com.bank.transfer.application.port.out.AccountInfo;
import com.bank.transfer.application.port.out.EventPublisher;
import com.bank.transfer.domain.exception.AccountFrozenException;
import com.bank.transfer.domain.exception.InsufficientFundsException;
import com.bank.transfer.domain.model.Money;
import com.bank.transfer.domain.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the intra-bank transfer saga.
 *
 * <p><strong>v1 scope:</strong> This is a single-service saga stub. The saga steps are:
 * <ol>
 *   <li>STARTED — validate accounts (stub: always ACTIVE with 10M THB balance)</li>
 *   <li>LIMIT_CHECK_DONE — per-tx limit check (stub: always passes in v1)</li>
 *   <li>DEBITED — debit source (stub: logged, no real account-service call)</li>
 *   <li>CREDITED — credit destination (stub: logged)</li>
 *   <li>COMPLETED — emit domain event via outbox</li>
 * </ol>
 *
 * <p>Compensation steps are defined but not exercised in v1 (no real credit step).
 * A full Resilience4j-decorated implementation will replace this when account-service
 * is deployed (US-006).
 *
 * <p>Saga state persistence (saga_state table) is designed but not written in v1
 * to keep scope to US-001 + US-003. The saga coordinator reads from saga_state
 * on recovery in a future iteration.
 */
@Component
public class TransferSaga {

    private static final Logger log = LoggerFactory.getLogger(TransferSaga.class);

    private final AccountClient accountClient;
    private final EventPublisher eventPublisher;

    /**
     * Constructor injection — no field injection.
     *
     * @param accountClient  stub in v1; real Feign client in US-006
     * @param eventPublisher writes outbox rows in same transaction
     */
    public TransferSaga(final AccountClient accountClient, final EventPublisher eventPublisher) {
        this.accountClient = accountClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes the saga for a new transfer.
     *
     * <p>On success: transitions transfer to COMPLETED and emits TransferCompleted event.
     * On business rule failure (frozen, insufficient funds): transitions to FAILED and
     * emits TransferFailed event. Does not throw — returns the mutated transfer.
     *
     * @param transfer the PENDING transfer aggregate to process
     * @return the transfer with updated status (COMPLETED or FAILED)
     */
    public Transfer execute(final Transfer transfer) {
        log.info("saga.started transfer_id={} src={}",
            transfer.getTransferId(), transfer.getSourceAccountId());

        try {
            // Step 1: Validate source account
            AccountInfo source = accountClient.getAccountInfo(
                transfer.getSourceAccountId().getId());
            log.info("saga.step=STARTED account_status={}", source.status());

            if (source.isFrozen()) {
                transfer.markFailed("SOURCE_ACCOUNT_FROZEN");
                eventPublisher.publishTransferFailed(transfer, "SOURCE_ACCOUNT_FROZEN");
                log.warn("saga.failed reason=SOURCE_ACCOUNT_FROZEN transfer_id={}",
                    transfer.getTransferId());
                return transfer;
            }

            // Step 2: Check balance (limit check is stub-passed in v1)
            Money requestedAmount = transfer.getAmount();
            Money availableBalance = Money.of(source.availableBalance(), source.currency());

            if (requestedAmount.isGreaterThan(availableBalance)) {
                transfer.markFailed(InsufficientFundsException.CODE);
                eventPublisher.publishTransferFailed(transfer, InsufficientFundsException.CODE);
                log.warn("saga.failed reason=INSUFFICIENT_FUNDS transfer_id={}",
                    transfer.getTransferId());
                return transfer;
            }

            log.info("saga.step=LIMIT_CHECK_DONE transfer_id={}", transfer.getTransferId());

            // Step 3: Validate destination account
            AccountInfo destination = accountClient.getAccountInfo(
                transfer.getDestinationAccountId().getId());

            if (destination.isFrozen()) {
                transfer.markFailed("ACCOUNT_FROZEN");
                eventPublisher.publishTransferFailed(transfer, "ACCOUNT_FROZEN");
                log.warn("saga.failed reason=ACCOUNT_FROZEN transfer_id={}",
                    transfer.getTransferId());
                return transfer;
            }

            // Step 4: Debit (stub — real call to account-service in US-006)
            log.info("saga.step=DEBITED transfer_id={} [stub: no real account-service call in v1]",
                transfer.getTransferId());

            // Step 5: Credit (stub)
            log.info("saga.step=CREDITED transfer_id={} [stub: no real account-service call in v1]",
                transfer.getTransferId());

            // Step 6: Complete
            transfer.markCompleted();
            eventPublisher.publishTransferCompleted(transfer);
            log.info("saga.completed transfer_id={}", transfer.getTransferId());

        } catch (com.bank.transfer.domain.exception.DependencyUnavailableException e) {
            // Let dependency unavailable propagate — controller maps to 503
            throw e;
        } catch (AccountFrozenException e) {
            transfer.markFailed(e.getErrorCode());
            eventPublisher.publishTransferFailed(transfer, e.getErrorCode());
        }

        return transfer;
    }
}
