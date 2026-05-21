package com.bank.balancedashboard.infrastructure.config;

import com.bank.balancedashboard.domain.policy.EligibilityPolicy;
import com.bank.balancedashboard.domain.policy.Ranker;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Infrastructure configuration for Resilience4j and domain beans.
 *
 * <p>Resilience4j instances are named "account-client" and wired from application.yml
 * configuration (see impl-notes §3 for the full YAML snippet).
 *
 * <p>EligibilityPolicy is configured here (Spring-wired) to support the ASSUMPTION-TL-001
 * config flip: {@code balance-dashboard.eligible-types=SAVINGS,CURRENT} (no code change needed).
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public TimeLimiter accountClientTimeLimiter(TimeLimiterRegistry registry) {
        return registry.timeLimiter("account-client");
    }

    @Bean
    public CircuitBreaker accountClientCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("account-client");
    }

    @Bean
    public Retry accountClientRetry(RetryRegistry registry) {
        return registry.retry("account-client");
    }

    @Bean
    public Bulkhead accountClientBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("account-client");
    }

    @Bean
    public ScheduledExecutorService timeLimiterScheduler() {
        // Virtual threads preferred for I/O-bound work (Java 21 requirement)
        // Note: if virtual threads pin on JDBC-like drivers, monitor jdk.virtualThreadPinned JFR event
        return Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * EligibilityPolicy bean — configured from application property.
     * ASSUMPTION-TL-001 fallback: set to "SAVINGS,CURRENT" via config to exclude FIXED_DEPOSIT.
     */
    @Bean
    public EligibilityPolicy eligibilityPolicy(
            @Value("${balance-dashboard.eligible-types:SAVINGS,CURRENT,FIXED_DEPOSIT}")
            String eligibleTypes) {
        return EligibilityPolicy.fromString(eligibleTypes);
    }

    /**
     * Ranker bean — domain service, no Spring deps, registered as Spring bean
     * for injection into AccountClientAdapter.
     */
    @Bean
    public Ranker ranker() {
        return new Ranker();
    }
}
