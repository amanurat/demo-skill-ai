package com.bank.transfer.domain.exception;

/**
 * Sealed base class for all domain-layer exceptions in the transfer service.
 *
 * <p>Using a sealed hierarchy allows exhaustive pattern matching in the
 * {@code @ControllerAdvice} and prevents unchecked exception leakage.
 *
 * <p>All subclasses carry a machine-readable {@code errorCode} that maps
 * directly to the error taxonomy defined in ADR-013 and the OpenAPI spec.
 */
public sealed class DomainException extends RuntimeException
    permits InsufficientFundsException,
            IdempotencyKeyConflictException,
            AccountFrozenException,
            TransferNotFoundException,
            DependencyUnavailableException {

    private final String errorCode;

    /**
     * Constructs a domain exception with a message and machine-readable error code.
     *
     * @param message   human-readable detail (safe for API response; no PII)
     * @param errorCode machine-readable code from the error taxonomy (e.g. INSUFFICIENT_FUNDS)
     */
    protected DomainException(final String message, final String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
