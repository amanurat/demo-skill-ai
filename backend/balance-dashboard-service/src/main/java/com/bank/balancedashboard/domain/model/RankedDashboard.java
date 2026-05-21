package com.bank.balancedashboard.domain.model;

import java.util.List;

/**
 * Ranked and enriched dashboard result ready for serialization.
 *
 * <p>Domain record — ZERO Spring/Kafka/Redis imports permitted in this package.
 * Produced by {@code Ranker.rank()} and returned by {@code LoadDashboardUseCase}.
 *
 * <p>The {@code accounts} list is server-ranked — FE MUST render in received order (ADR-004).
 * The {@code cacheHit} field is observability/audit only — never displayed to customer.
 * The {@code correlationId} is echoed in X-Correlation-Id response header.
 *
 * @param accounts     server-ranked list (1-based rank on each AccountView), may be empty
 * @param freshness    "live" = cache miss (fresh fetch), "snapshot" = cache hit
 * @param cacheHit     true if served from Redis warm cache
 * @param correlationId OTel trace ID for this request
 */
public record RankedDashboard(
        List<AccountView> accounts,
        String freshness,
        boolean cacheHit,
        String correlationId
) {

    /**
     * Compact constructor — ensures accounts is never null, freshness is valid.
     */
    public RankedDashboard {
        if (accounts == null) {
            throw new NullPointerException("accounts list must not be null");
        }
        if (freshness == null || (!freshness.equals("live") && !freshness.equals("snapshot"))) {
            throw new IllegalArgumentException("freshness must be 'live' or 'snapshot', got: " + freshness);
        }
        // Defensive copy — ranked order must be preserved; no re-sort permitted (ADR-004)
        accounts = List.copyOf(accounts);
    }

    /** Convenience: count of accounts in this dashboard. Matches accounts.size(). */
    public int accountCount() {
        return accounts.size();
    }
}
