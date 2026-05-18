package com.bank.transfer.domain.exception;

/**
 * Thrown when the source account's available balance is below the requested amount.
 * Maps to HTTP 422 with code {@code INSUFFICIENT_FUNDS}.
 */
public final class InsufficientFundsException extends DomainException {

    /** Error code per ADR-013 taxonomy. */
    public static final String CODE = "INSUFFICIENT_FUNDS";

    /**
     * Constructs an InsufficientFundsException with a safe (non-PII) detail message.
     */
    public InsufficientFundsException() {
        super("Source account available balance is below the requested amount.", CODE);
    }
}
