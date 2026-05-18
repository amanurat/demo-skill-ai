package com.bank.transfer.application.port.in;

import java.util.UUID;

/**
 * Inbound port for the "create transfer" and "get transfer" use cases.
 *
 * <p>Implementations must be {@code @Transactional} at the use-case level.
 * The controller calls this port; it never touches infrastructure directly.
 */
public interface CreateTransferPort {

    /**
     * Executes the create-transfer use case.
     *
     * <p>First checks the idempotency store. On a matching replay, returns the
     * cached response. On a conflict (same key, different payload), throws
     * {@link com.bank.transfer.domain.exception.IdempotencyKeyConflictException}.
     * On a new request, runs the transfer saga and stores the result.
     *
     * @param command all data required to create a transfer, including idempotency key
     * @return transfer result (FIRST_WRITE or IDEMPOTENT_REPLAY)
     */
    TransferResult execute(CreateTransferCommand command);

    /**
     * Fetches the current state of a transfer by its server-assigned id.
     *
     * @param transferId the transfer UUID
     * @param customerId the requesting customer UUID (for ownership check)
     * @return transfer result
     * @throws com.bank.transfer.domain.exception.TransferNotFoundException if not found
     */
    TransferResult findById(UUID transferId, UUID customerId);
}
