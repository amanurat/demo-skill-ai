package com.bank.transfer.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable command object carrying all data needed to initiate a transfer.
 *
 * <p>Lives in the application layer — the interfaces layer maps from the HTTP
 * DTO into this command before calling the inbound port. This keeps the domain
 * and application layers free of HTTP/REST concerns.
 *
 * @param sourceAccountId      UUID of the account to debit
 * @param destinationAccountId UUID of the account to credit
 * @param amount               transfer amount (must be positive, scale ≤ 4)
 * @param currency             ISO 4217 currency code (v1: THB only)
 * @param memo                 optional free-text memo (max 200 chars)
 * @param channel              origination channel (INTERNET_BANKING | MOBILE_BANKING)
 * @param idempotencyKey       raw value of the Idempotency-Key header
 * @param customerId           customer UUID extracted from JWT subject
 * @param correlationId        W3C trace / correlation id
 */
public record CreateTransferCommand(
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency,
    String memo,
    String channel,
    String idempotencyKey,
    UUID customerId,
    String correlationId
) {}
