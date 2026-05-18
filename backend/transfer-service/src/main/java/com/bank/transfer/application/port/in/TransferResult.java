package com.bank.transfer.application.port.in;

import com.bank.transfer.domain.model.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable result object returned from the create-transfer use case.
 *
 * <p>Lives in the application layer. The interfaces layer maps this to the
 * HTTP response DTO via MapStruct.
 *
 * @param transferId           server-assigned transfer UUID
 * @param referenceNumber      human-readable reference (TRF-YYYYMMDD-XXXXXXXX)
 * @param status               current transfer status
 * @param amount               transfer amount as BigDecimal (serialized as string on wire, ADR-016)
 * @param currency             ISO 4217 currency code
 * @param sourceAccountId      UUID of the debited account
 * @param destinationAccountId UUID of the credited account
 * @param memo                 optional free-text memo
 * @param completedAt          terminal timestamp, null if still in flight
 * @param failureReason        machine-readable reason code on failure, null otherwise
 * @param idempotencyStatus    FIRST_WRITE or IDEMPOTENT_REPLAY
 */
public record TransferResult(
    UUID transferId,
    String referenceNumber,
    TransferStatus status,
    BigDecimal amount,
    String currency,
    UUID sourceAccountId,
    UUID destinationAccountId,
    String memo,
    Instant completedAt,
    String failureReason,
    IdempotencyStatus idempotencyStatus
) {

    /** Indicates whether this response is fresh or served from the idempotency cache. */
    public enum IdempotencyStatus {
        FIRST_WRITE,
        IDEMPOTENT_REPLAY
    }
}
