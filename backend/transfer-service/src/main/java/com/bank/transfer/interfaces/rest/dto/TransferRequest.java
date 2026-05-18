package com.bank.transfer.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Immutable HTTP request DTO for POST /api/v1/transfers.
 *
 * <p>Maps directly to the OpenAPI {@code TransferRequest} schema.
 * Bean Validation constraints mirror the OpenAPI constraints to catch
 * client errors at the boundary before they reach the domain layer.
 *
 * @param sourceAccountId      UUID of the account to debit (required)
 * @param destinationAccountId UUID of the account to credit (required)
 * @param amount               decimal money string, e.g. "1500.0000" (required)
 * @param currency             ISO 4217 code — v1 accepts THB only (required)
 * @param memo                 optional free-text memo, max 200 chars
 * @param channel              origination channel; defaults to INTERNET_BANKING
 */
public record TransferRequest(

    @NotNull(message = "sourceAccountId is required")
    UUID sourceAccountId,

    @NotNull(message = "destinationAccountId is required")
    UUID destinationAccountId,

    @NotBlank(message = "amount is required")
    @Pattern(
        regexp = "^[0-9]+(\\.[0-9]{1,4})?$",
        message = "amount must be a positive decimal with up to 4 decimal places"
    )
    String amount,

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^THB$", message = "Only THB currency is supported in v1")
    String currency,

    @Size(max = 200, message = "memo must not exceed 200 characters")
    String memo,

    @Pattern(
        regexp = "^(INTERNET_BANKING|MOBILE_BANKING)$",
        message = "channel must be INTERNET_BANKING or MOBILE_BANKING"
    )
    String channel

) {}
