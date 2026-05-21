package com.bank.balancedashboard.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for balance-dashboard-service.
 *
 * <p>Stateless JWT resource server. All API routes require authentication.
 * Authorization is scope-based: {@code @PreAuthorize("hasAuthority('SCOPE_accounts:read')")}
 * enforced at the controller method level (ASSUMPTION-TL-002: scope name = "accounts:read").
 *
 * <p>Actuator endpoints: accessible without auth (internal-only, protected by NetworkPolicy).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless — no session (JWT only)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF disabled: stateless REST API with JWT Bearer tokens
                .csrf(csrf -> csrf.disable())

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Actuator endpoints: open for Prometheus scraping + k8s health probes
                        .requestMatchers("/actuator/**").permitAll()
                        // All other API routes require authentication
                        .anyRequest().authenticated()
                )

                // OAuth2 Resource Server — JWT RS256 (issuer validated per application.yml)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                );

        return http.build();
    }
}
