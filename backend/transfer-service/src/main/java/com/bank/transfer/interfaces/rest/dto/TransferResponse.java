package com.bank.transfer.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable HTTP response DTO for POST /api/v1/transfers and
 * GET /api/v1/transfers/{transferId}.
 *
 * <p>Maps directly to the OpenAPI {@code TransferResponse} schema.
 * Amount is carried as a String (decimal wire format per ADR-016 — JSON number
 * would lose BigDecimal precision). Null-valued optional fields are omitted
 * from JSON output via {@link JsonInclude#NON_NULL}.
 *
 * @param transferId           server-assigned transfer UUID
 * @param referenceNumber      human-readable reference, e.g. "TRF-20260518-ABCD1234"
 * @param status               current transfer status enum name
 * @param amount               decimal string representation of the amount
 * @param currency             ISO 4217 currency code
 * @param sourceAccountId      UUID of the debited account
 * @param destinationAccountId UUID of the credited account
 * @param memo                 optional free-text memo
 * @param completedAt          ISO-8601 UTC timestamp when status became terminal (null if in-flight)
 * @param failureReason        machine-readable reason code on failure (null otherwise)
 * @param idempotencyStatus    FIRST_WRITE or IDEMPOTENT_REPLAY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferResponse(
    UUID transferId,
    String referenceNumber,
    String status,
    String amount,
    String currency,
    UUID sourceAccountId,
    UUID destinationAccountId,
    String memo,
    Instant completedAt,
    String failureReason,
    String idempotencyStatus
) {}
