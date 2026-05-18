package com.bank.transfer.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the transfer service.
 *
 * <p>v1 policy:
 * <ul>
 *   <li>Actuator health endpoints are permitted without auth (liveness/readiness for K8s).</li>
 *   <li>All other endpoints require an authenticated JWT bearer token.</li>
 *   <li>Sessions are stateless — no HTTP session created.</li>
 * </ul>
 *
 * <p>TODO (US-006 / ADR-002): Wire a real JWT issuer URI from Vault and enable
 * {@code oauth2ResourceServer().jwt()} with RS256 key set from identity-service JWKS endpoint.
 * For v1 scaffold, JWT validation is intentionally disabled so integration tests can run
 * without a live identity-service. Method-level {@code @PreAuthorize} on use-cases
 * remains the guard; tests authenticate via {@code @WithMockUser}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configures the security filter chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // K8s probes and Prometheus scrape
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/prometheus"
                ).permitAll()
                // SpringDoc / Swagger UI (dev/staging only — should be blocked in prod
                // via network policy, not by disabling the route entirely)
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // All transfer API endpoints require authentication
                .anyRequest().authenticated()
            );
        // v1: no real JWT issuer configured — permit-all approach for scaffold.
        // Production wiring: uncomment the block below and supply issuer-uri via Vault.
        // http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
