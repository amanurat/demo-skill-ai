package com.bank.transfer.domain.exception;

/**
 * Thrown when a downstream dependency (e.g. account-service) is unreachable
 * or the circuit breaker is open.
 * Maps to HTTP 503 with code {@code DEPENDENCY_UNAVAILABLE} and a
 * {@code Retry-After} header.
 */
public final class DependencyUnavailableException extends DomainException {

    /** Error code per ADR-013 taxonomy. */
    public static final String CODE = "DEPENDENCY_UNAVAILABLE";

    /**
     * Constructs a DependencyUnavailableException for a named service.
     *
     * @param serviceName the name of the unavailable dependency
     */
    public DependencyUnavailableException(final String serviceName) {
        super("Downstream " + serviceName + " is unavailable. Please retry.", CODE);
    }
}
