package com.bank.balancedashboard.infrastructure.rest;

/**
 * Thrown by {@link CustomerIdResolver} when the JWT sub claim is missing or not a valid UUID.
 * Mapped to HTTP 403 Forbidden by {@link ProblemDetailAdvice}.
 */
public class InvalidJwtSubException extends RuntimeException {

    public InvalidJwtSubException(String message) {
        super(message);
    }

    public InvalidJwtSubException(String message, Throwable cause) {
        super(message, cause);
    }
}
