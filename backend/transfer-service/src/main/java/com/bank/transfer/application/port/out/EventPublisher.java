package com.bank.transfer.application.port.out;

import com.bank.transfer.domain.model.Transfer;

/**
 * Outbound port for reliable domain event publication via the Transactional Outbox.
 *
 * <p>Implementations write an event row to the {@code transfer_outbox} table
 * within the same DB transaction as the business write. A separate
 * {@code @Scheduled} poller then relays unpublished rows to Kafka (ADR-014).
 *
 * <p>This port is intentionally narrow — callers specify event type; the
 * adapter resolves the Kafka topic and Avro schema version.
 */
public interface EventPublisher {

    /**
     * Writes a {@code TransferRequested} event row to the outbox table.
     *
     * @param transfer the newly created transfer aggregate
     */
    void publishTransferRequested(Transfer transfer);

    /**
     * Writes a {@code TransferCompleted} event row to the outbox table.
     *
     * @param transfer the completed transfer aggregate
     */
    void publishTransferCompleted(Transfer transfer);

    /**
     * Writes a {@code TransferFailed} event row to the outbox table.
     *
     * @param transfer  the failed transfer aggregate
     * @param reason    machine-readable failure reason code
     */
    void publishTransferFailed(Transfer transfer, String reason);
}
