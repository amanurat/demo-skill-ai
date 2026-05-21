package com.bank.balancedashboard.infrastructure.rest;

import com.bank.balancedashboard.domain.model.RankedDashboard;
import com.bank.balancedashboard.domain.port.in.LoadDashboardUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for GET /api/v1/balance-dashboard.
 *
 * <p>Feature-flagged: only registered when {@code balance-dashboard.enabled=true}.
 * When flag is off, {@link BalanceDashboardDisabledController} takes the URL and returns 501.
 *
 * <p>Security contract (ADR-006 §2.3, Security C-3):
 * <ul>
 *   <li>Accepts ONLY {@code @AuthenticationPrincipal Jwt jwt} — NO @RequestHeader, NO @RequestParam
 *       for customerId by construction (IDOR prevention).</li>
 *   <li>Calls {@link CustomerIdResolver#resolve(Jwt)} to derive customerId from JWT sub.</li>
 *   <li>NEVER uses X-Customer-Id header as customerId value — that is IborCheckFilter's job only.</li>
 * </ul>
 *
 * <p>Response headers set per OpenAPI contract:
 * <ul>
 *   <li>{@code X-Cache: HIT|MISS} — observability</li>
 *   <li>{@code X-Correlation-Id} — OTel trace ID echoed from meta.correlationId</li>
 *   <li>{@code Cache-Control: private, no-store} — PDPA: downstream proxies/browsers MUST NOT cache</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/balance-dashboard")
@PreAuthorize("hasAuthority('SCOPE_accounts:read')")
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "true", matchIfMissing = false)
public class BalanceDashboardController {

    private static final Logger log = LoggerFactory.getLogger(BalanceDashboardController.class);

    private final LoadDashboardUseCase useCase;
    private final CustomerIdResolver customerIdResolver;

    public BalanceDashboardController(LoadDashboardUseCase useCase,
                                      CustomerIdResolver customerIdResolver) {
        this.useCase = useCase;
        this.customerIdResolver = customerIdResolver;
    }

    /**
     * Returns the authenticated customer's eligible accounts ranked by balance DESC.
     *
     * <p>customerId is derived ONLY from JWT sub — never from headers or request params.
     * This is the IDOR-safe pattern mandated by ADR-006 and Security C-3.
     *
     * @param jwt the authenticated JWT (Spring Security injects; @PreAuthorize guarantees non-null)
     * @return 200 with ranked dashboard + mandatory response headers
     */
    @GetMapping
    public ResponseEntity<BalanceDashboardResponse> getDashboard(
            @AuthenticationPrincipal Jwt jwt) {

        // ONLY source of customerId in this controller — ADR-006 §2.3 mandatory pattern
        UUID customerId = customerIdResolver.resolve(jwt);

        RankedDashboard dashboard = useCase.loadDashboard(customerId);
        BalanceDashboardResponse response = BalanceDashboardResponse.from(dashboard);

        log.debug("balance-dashboard.response customerId={} freshness={} accountCount={}",
                customerId, dashboard.freshness(), dashboard.accountCount());

        return ResponseEntity.ok()
                // X-Cache: HIT or MISS (observability)
                .header("X-Cache", dashboard.cacheHit() ? "HIT" : "MISS")
                // X-Correlation-Id echoed from meta.correlationId (OTel trace ID)
                .header("X-Correlation-Id", dashboard.correlationId())
                // PDPA: downstream proxies and browsers MUST NOT cache financial data
                .header("Cache-Control", "private, no-store")
                .body(response);
    }
}
