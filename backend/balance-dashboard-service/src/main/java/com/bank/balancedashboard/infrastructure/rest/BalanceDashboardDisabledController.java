package com.bank.balancedashboard.infrastructure.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stub controller active when {@code balance-dashboard.enabled=false} (default prod posture).
 *
 * <p>Returns HTTP 501 Not Implemented (NOT 404) so operations can distinguish
 * "feature disabled" from "URL typo" in logs (impl-notes §5).
 *
 * <p>Prod default: feature flag is false until BoT sign-off. Staging: flag is true.
 * This controller is the fallback when {@link BalanceDashboardController} bean is absent.
 */
@RestController
@RequestMapping("/api/v1/balance-dashboard")
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "false", matchIfMissing = true)
public class BalanceDashboardDisabledController {

    /**
     * Returns 501 Not Implemented with Problem-Detail body.
     * Flag-off state is visible in ops logs as 501, not 404.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> disabled() {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://docs.bank.com/errors/feature-disabled");
        problem.put("title", "Feature disabled");
        problem.put("status", 501);
        problem.put("detail", "balance-dashboard is disabled in this environment.");
        problem.put("instance", "/api/v1/balance-dashboard");
        problem.put("code", "FEATURE_DISABLED");

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(problem);
    }
}
