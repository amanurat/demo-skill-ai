package com.bank.balancedashboard.arch.violations;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Deliberate-violation fixture for CustomerIdSourceRule.violationIsDetected().
 *
 * <p>This class intentionally calls {@code request.getHeader(String)} outside of
 * IborCheckFilter, which is a direct violation of ADR-006 §2.4 / Security C-3.
 *
 * <p>It exists ONLY to prove the ArchUnit rule fires on real violations.
 * It MUST NOT be referenced from any production code path.
 *
 * <p>R-BE-011 fix: provides the deliberate-violation fixture mandated by task-plan §Layer 6.
 */
public class FakeViolatingService {

    /**
     * Violating method — reads X-Customer-Id header directly (forbidden outside IborCheckFilter).
     * ArchUnit should flag this as a violation of {@code only_filter_reads_x_customer_id_header}.
     */
    public String getCustomerIdFromHeader(HttpServletRequest request) {
        // VIOLATION: direct header access outside IborCheckFilter (ADR-006 §2.4)
        return request.getHeader("X-Customer-Id");
    }
}
