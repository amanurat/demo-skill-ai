package com.bank.transfer.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for idempotency key storage.
 *
 * <p>All operations must be executed within the same DB transaction as the
 * business write to guarantee atomicity (ADR-013).
 */
public interface IdempotencyRepository {

    /**
     * Looks up a non-expired idempotency record by (keyHash, ownerCustomerId).
     *
     * @param keyHash         SHA-256 hex of the raw Idempotency-Key
     * @param ownerCustomerId customer UUID scoping the key
     * @return the record, or empty if not found or expired
     */
    Optional<IdempotencyRecord> findValid(String keyHash, UUID ownerCustomerId);

    /**
     * Inserts a placeholder record with {@code resultStatus=PENDING}.
     * Called at the start of the use-case transaction before business logic runs.
     *
     * @param keyHash         SHA-256 hex of the key
     * @param ownerCustomerId customer UUID
     * @param requestChecksum SHA-256 hex of the canonical request body
     * @param correlationId   trace / correlation id
     * @param ttl             how long to keep the record (typically 24h)
     */
    void insertPending(
        String keyHash,
        UUID ownerCustomerId,
        String requestChecksum,
        String correlationId,
        Duration ttl
    );

    /**
     * Updates the placeholder to the final state (COMPLETED, FAILED, or REJECTED)
     * and stores the response body for future replays.
     *
     * @param keyHash            SHA-256 hex of the key
     * @param ownerCustomerId    customer UUID
     * @param transferId         the resulting transfer UUID
     * @param resultStatus       final result status
     * @param cachedResponseCode HTTP status code
     * @param cachedResponseBody JSON serialized response body
     */
    void updateResult(
        String keyHash,
        UUID ownerCustomerId,
        UUID transferId,
        String resultStatus,
        int cachedResponseCode,
        String cachedResponseBody
    );
}
