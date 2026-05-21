package com.bank.balancedashboard.infrastructure.cache;

import java.util.List;

/**
 * Jackson-deserializable payload stored in Redis for the balance dashboard cache.
 *
 * <p>Stores only the accounts array and freshness — cacheHit and correlationId
 * are recomputed at serve time (per task-plan §Layer 4 RedisCacheRepository notes):
 * "When serving from cache: recompute meta.cacheHit=true and refresh meta.correlationId
 * to current request trace ID. Only accounts[] and meta.freshness=snapshot come
 * verbatim from stored JSON."
 *
 * <p>Key: {@code balance-dashboard:customer:{customerId}}
 * TTL: 30s (SA ADR-002 — TTL-only, no active invalidation in v1)
 *
 * <p>ObjectMapper config: WRITE_BIGDECIMAL_AS_PLAIN=true + no float coercion.
 */
public record CachedBalanceDashboard(
        List<CachedAccountView> accounts,
        String freshness  // always "snapshot" when serving from cache
) {

    /**
     * Per-account view stored in the Redis cache payload.
     * Balance is stored as String (format ^-?\d+\.\d{2}$) to avoid precision loss.
     * balanceAsOf is stored verbatim (ISO 8601) — never substituted (AC-003-H4).
     */
    public record CachedAccountView(
            int    rank,
            String accountId,       // UUID string
            String accountNumberMasked,
            String accountType,     // "SAVINGS" | "CURRENT" | "FIXED_DEPOSIT"
            String balance,         // BigDecimal-as-string, e.g. "128540.25"
            String currency,
            String balanceAsOf,     // ISO 8601 UTC
            boolean isStale,
            String displayLabel
    ) {}
}
