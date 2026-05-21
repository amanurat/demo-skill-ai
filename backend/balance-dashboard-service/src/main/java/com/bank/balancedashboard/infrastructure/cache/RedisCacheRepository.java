package com.bank.balancedashboard.infrastructure.cache;

import com.bank.balancedashboard.application.port.out.CachePort;
import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.model.RankedDashboard;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis-backed implementation of {@link CachePort} using Lettuce (Spring Data Redis).
 *
 * <p>Key pattern: {@code balance-dashboard:customer:{customerId}}
 * TTL: 30 seconds (SA ADR-002 — TTL-only, no active invalidation in v1)
 *
 * <p>Write: ALWAYS atomic SETEX via {@code redisTemplate.opsForValue().set(key, value, Duration)}.
 * NEVER separate SET + EXPIRE — race window allows indefinite retention on pod crash (impl-notes §4).
 *
 * <p>Fail-open: catch {@code RedisException/RuntimeException} on reads and writes:
 * log WARN + increment {@code cache_miss_reason_total{reason=REDIS_UNAVAILABLE}} + return empty.
 * NO circuit breaker on Redis per SA NFR {@code availability_redis_fail_open}.
 *
 * <p>Security C-4: {@code spring.data.redis.ssl.enabled=true} in application.yml.
 */
@Component
public class RedisCacheRepository implements CachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheRepository.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String keyPrefix;
    private final long ttlSeconds;

    public RedisCacheRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${balance-dashboard.cache.key-prefix:balance-dashboard:customer:}") String keyPrefix,
            @Value("${balance-dashboard.cache.ttl-seconds:30}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.keyPrefix = keyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Retrieves cached ranked dashboard from Redis.
     *
     * <p>On Redis failure: log WARN + increment metric + return empty (fail-open, BR-015).
     * On cache miss: return empty.
     * On cache hit: return populated Optional with freshness="snapshot".
     */
    @Override
    public Optional<RankedDashboard> get(UUID customerId) {
        String key = keyFor(customerId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            CachedBalanceDashboard cached = objectMapper.readValue(json, CachedBalanceDashboard.class);
            RankedDashboard dashboard = toDomain(cached);
            return Optional.of(dashboard);
        } catch (RuntimeException e) {
            // Redis fail-open (AC-003-E1, BR-015)
            log.warn("cache.get.failed key={} — failing open to AccountClient", key, e);
            meterRegistry.counter("cache_miss_reason_total", "reason", "REDIS_UNAVAILABLE")
                    .increment();
            return Optional.empty();
        }
    }

    /**
     * Stores ranked dashboard in Redis with atomic TTL (SETEX semantics).
     *
     * <p>MANDATORY: single {@code set(key, value, Duration)} call — never SET + separate EXPIRE.
     * On write failure: log WARN + increment metric + swallow (do NOT throw to application layer).
     */
    @Override
    public void put(UUID customerId, RankedDashboard dashboard) {
        String key = keyFor(customerId);
        try {
            CachedBalanceDashboard cached = toCached(dashboard);
            String json = objectMapper.writeValueAsString(cached);

            // ATOMIC SETEX — RedisTemplate translates to SETEX under the hood.
            // NEVER call .set(key, json) then .expire(key, duration) separately.
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));

            log.debug("cache.put key={} ttl={}s accountCount={}", key, ttlSeconds,
                    dashboard.accountCount());
        } catch (RuntimeException e) {
            // Cache write failure — swallow (user still gets fresh response)
            log.warn("cache.put.failed key={} — swallowing, user gets fresh response", key, e);
            meterRegistry.counter("cache_miss_reason_total", "reason", "REDIS_WRITE_FAILED")
                    .increment();
        }
    }

    private String keyFor(UUID customerId) {
        return keyPrefix + customerId.toString();
    }

    private RankedDashboard toDomain(CachedBalanceDashboard cached) {
        List<AccountView> accounts = cached.accounts().stream()
                .map(this::accountViewFromCached)
                .collect(Collectors.toList());
        // correlationId is recomputed at serve time (caller sets current trace ID)
        return new RankedDashboard(accounts, "snapshot", true, "");
    }

    private AccountView accountViewFromCached(CachedBalanceDashboard.CachedAccountView c) {
        Instant balanceAsOf = c.balanceAsOf() != null ? Instant.parse(c.balanceAsOf()) : null;
        return new AccountView(
                c.rank(),
                UUID.fromString(c.accountId()),
                c.accountNumberMasked(),
                AccountType.valueOf(c.accountType()),
                new BigDecimal(c.balance()),
                c.currency(),
                balanceAsOf,
                c.isStale(),
                c.displayLabel()
        );
    }

    private CachedBalanceDashboard toCached(RankedDashboard dashboard) {
        List<CachedBalanceDashboard.CachedAccountView> accounts = dashboard.accounts().stream()
                .map(this::cachedAccountViewFrom)
                .collect(Collectors.toList());
        return new CachedBalanceDashboard(accounts, "snapshot");
    }

    private CachedBalanceDashboard.CachedAccountView cachedAccountViewFrom(AccountView av) {
        return new CachedBalanceDashboard.CachedAccountView(
                av.rank(),
                av.accountId().toString(),
                av.accountNumberMasked(),
                av.accountType().name(),
                // WRITE_BIGDECIMAL_AS_PLAIN — use toPlainString() for explicit control
                av.balance().toPlainString(),
                av.currency(),
                av.balanceAsOf() != null ? av.balanceAsOf().toString() : null,
                av.isStale(),
                av.displayLabel()
        );
    }
}
