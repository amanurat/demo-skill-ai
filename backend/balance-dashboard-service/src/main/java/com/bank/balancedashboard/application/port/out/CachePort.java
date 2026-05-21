package com.bank.balancedashboard.application.port.out;

import com.bank.balancedashboard.domain.model.RankedDashboard;

import java.util.Optional;
import java.util.UUID;

/**
 * Secondary (outbound) port for the Redis cache layer.
 * Implemented by {@code RedisCacheRepository} in the infrastructure layer.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #get}: returns empty Optional on cache miss OR Redis failure.
 *       Implementations MUST NOT leak {@code RedisException} to callers — they must
 *       catch, log WARN, increment {@code cache_miss_reason_total{reason=REDIS_UNAVAILABLE}},
 *       and return {@code Optional.empty()} (fail-open, ADR SA §availability_redis_fail_open).</li>
 *   <li>{@link #put}: atomic SETEX write (SET + TTL in one command). Implementations MUST use
 *       {@code redisTemplate.opsForValue().set(key, value, Duration)} — NEVER separate
 *       SET then EXPIRE. On write failure, swallow and log WARN (do not throw).</li>
 *   <li>No circuit breaker on Redis — Lettuce timeout + fail-open is sufficient.</li>
 * </ul>
 */
public interface CachePort {

    /**
     * Retrieves a cached ranked dashboard for the given customer.
     *
     * @param customerId customer UUID (used as cache key component)
     * @return populated Optional if cache hit, empty Optional on miss or Redis failure
     */
    Optional<RankedDashboard> get(UUID customerId);

    /**
     * Stores the ranked dashboard in Redis with atomic TTL (SETEX semantics).
     *
     * @param customerId  customer UUID (cache key component)
     * @param dashboard   the ranked dashboard to cache
     */
    void put(UUID customerId, RankedDashboard dashboard);
}
