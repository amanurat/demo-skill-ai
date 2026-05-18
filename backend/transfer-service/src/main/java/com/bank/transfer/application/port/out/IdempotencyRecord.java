package com.bank.transfer.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable data object representing a stored idempotency entry.
 *
 * <p>Returned by {@link IdempotencyRepository#findValid} when a matching key is found.
 *
 * @param keyHash           SHA-256 hex of the raw Idempotency-Key
 * @param ownerCustomerId   customer UUID (key is scoped per customer)
 * @param transferId        the resulting transfer UUID (may be null while PENDING)
 * @param requestChecksum   SHA-256 hex of the canonical request payload
 * @param resultStatus      current processing status (PENDING/COMPLETED/FAILED/REJECTED)
 * @param cachedResponseCode HTTP status code of the cached response
 * @param cachedResponseBody serialized JSON body of the cached response
 * @param expiresAt         TTL timestamp; records past this are treated as expired
 */
public record IdempotencyRecord(
    String keyHash,
    UUID ownerCustomerId,
    UUID transferId,
    String requestChecksum,
    String resultStatus,
    int cachedResponseCode,
    String cachedResponseBody,
    Instant expiresAt
) {}
