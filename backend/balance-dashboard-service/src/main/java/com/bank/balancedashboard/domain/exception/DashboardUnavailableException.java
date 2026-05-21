package com.bank.balancedashboard.domain.exception;

/**
 * Domain exception thrown when the balance dashboard cannot be loaded due to
 * upstream service unavailability.
 *
 * <p>This exception lives in the domain layer. The infrastructure adapter
 * ({@code AccountClientAdapter}) catches its own {@code UpstreamUnavailableException}
 * and wraps it as {@code DashboardUnavailableException} before propagating up through
 * the application layer to the controller.
 *
 * <p>Mapped to HTTP 503 by {@code ProblemDetailAdvice}.
 *
 * <p>R-BE-002/003/004 fix: ensures neither the application layer nor the domain layer
 * imports any infrastructure type.
 */
public class DashboardUnavailableException extends RuntimeException {

    public DashboardUnavailableException(String message) {
        super(message);
    }

    public DashboardUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
