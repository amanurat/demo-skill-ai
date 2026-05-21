package com.bank.balancedashboard.infrastructure.rest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for deriving {@code customerId} from an authenticated request.
 *
 * <p>ALWAYS returns the JWT {@code sub} claim parsed as UUID.
 * NEVER reads HTTP headers, query strings, path variables, or request body.
 * This is the ONLY abstraction permitted to produce a {@code customerId} for downstream
 * business logic (ADR-006 §2.2, Security C-3).
 *
 * <p>Usage in controllers:
 * <pre>{@code
 *   UUID customerId = customerIdResolver.resolve(jwt);
 *   // NEVER: request.getHeader("X-Customer-Id"), @RequestHeader, @RequestParam for customerId
 * }</pre>
 *
 * <p>Pure unit-testable — no Spring context required (takes Jwt argument, not HttpServletRequest).
 * ArchUnit {@code CustomerIdSourceRule} enforces that no other class calls
 * {@code request.getHeader("X-Customer-Id")} outside {@link IborCheckFilter}.
 */
@Component
public class CustomerIdResolver {

    /**
     * Extracts the customer UUID from the JWT sub claim.
     *
     * @param jwt the authenticated JWT (non-null; guaranteed by Spring Security
     *            because controller is {@code @PreAuthorize("hasAuthority('SCOPE_accounts:read')")})
     * @return customerId UUID parsed from JWT sub claim
     * @throws InvalidJwtSubException if sub claim is missing, blank, or not a valid UUID
     */
    public UUID resolve(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new InvalidJwtSubException("JWT sub claim missing or blank");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtSubException("JWT sub claim is not a valid UUID: " + sub, e);
        }
    }
}
