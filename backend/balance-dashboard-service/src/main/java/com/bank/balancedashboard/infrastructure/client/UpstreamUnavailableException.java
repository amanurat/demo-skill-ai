package com.bank.balancedashboard.infrastructure.client;

/**
 * Thrown when account-service is unavailable after all Resilience4j attempts are exhausted.
 *
 * <p>Failure modes that translate to this exception (all return HTTP 503 + Problem-Detail):
 * <ul>
 *   <li>TimeoutException — request exceeded timeLimiter.timeoutDuration (300ms)</li>
 *   <li>CallNotPermittedException — circuit breaker is OPEN</li>
 *   <li>BulkheadFullException — concurrent call limit reached</li>
 *   <li>HTTP 5xx after all retries exhausted</li>
 * </ul>
 *
 * <p>Mapped to HTTP 503 by {@code ProblemDetailAdvice}.
 */
public class UpstreamUnavailableException extends RuntimeException {

    private final String reason;

    public UpstreamUnavailableException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public UpstreamUnavailableException(String message, String reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
