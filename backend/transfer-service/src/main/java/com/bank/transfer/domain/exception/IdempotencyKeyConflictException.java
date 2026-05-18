package com.bank.transfer.domain.exception;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload.
 * Maps to HTTP 409 with code {@code IDEMPOTENCY_KEY_CONFLICT} (US-003 AC4).
 */
public final class IdempotencyKeyConflictException extends DomainException {

    /** Error code per ADR-013 taxonomy. */
    public static final String CODE = "IDEMPOTENCY_KEY_CONFLICT";

    /**
     * Constructs an IdempotencyKeyConflictException.
     */
    public IdempotencyKeyConflictException() {
        super(
            "Idempotency-Key was previously used with a different request payload.",
            CODE
        );
    }
}
