# Rate Limiter — Resilience4j 2.x

A rate limiter caps the **outbound** call rate to a dependency. It's about respecting their contract or your own SLA, not about protecting yourself from inbound floods (use an API gateway for that).

## When to Use Resilience4j RateLimiter

| Scenario | Use this? |
|---|---|
| 3rd party with metered quota (FX provider says "100 req/s") | ✅ Yes |
| Internal microservice you don't want to overwhelm | ✅ Yes (or use bulkhead) |
| Per-tenant quota on outbound calls | ✅ Yes (one limiter instance per tenant) |
| Limit inbound REST requests | ❌ No — use gateway (Spring Cloud Gateway, Envoy, Kong) |
| Limit inbound Kafka consumer throughput | ❌ No — tune `max.poll.records` / consumer concurrency |
| Per-user login attempts (security) | ❌ No — use Spring Security `LoginAttemptService` or gateway |

## Config

```yaml
resilience4j.ratelimiter:
  configs:
    banking-default:
      limitForPeriod: 100          # number of permits
      limitRefreshPeriod: 1s       # how often the bucket refills
      timeoutDuration: 50ms        # how long a caller waits for a permit
  instances:
    fx-rate-api:
      baseConfig: banking-default
      limitForPeriod: 50           # provider says 50/s
      limitRefreshPeriod: 1s
      timeoutDuration: 200ms       # latency-sensitive; fail fast
```

Math: this is a **fixed window** limiter (per-period refill), not a true token bucket. Smoother bursts → use shorter `limitRefreshPeriod` (e.g. 100ms with 1/10 of the limit).

```yaml
limitForPeriod: 10
limitRefreshPeriod: 100ms          # ≈ 100/s spread evenly
```

## Annotation Usage

```java
@RateLimiter(name = "fx-rate-api", fallbackMethod = "rateFallback")
public BigDecimal getRate(String from, String to) {
  return fxClient.getRate(from, to);
}

private BigDecimal rateFallback(String from, String to, RequestNotPermitted ex) {
  // Use cached rate, or fail
  return cachedRates.get(from + "->" + to)
      .orElseThrow(() -> new FxUnavailableException(from, to, ex));
}
```

`RequestNotPermitted` is thrown when no permit is available within `timeoutDuration`.

## Programmatic Usage

```java
RateLimiter limiter = rateLimiterRegistry.rateLimiter("fx-rate-api");

Supplier<BigDecimal> decorated = RateLimiter.decorateSupplier(limiter,
    () -> fxClient.getRate(from, to));

try {
  return decorated.get();
} catch (RequestNotPermitted ex) {
  return cachedRates.get(from + "->" + to)
      .orElseThrow(() -> new FxUnavailableException(from, to, ex));
}
```

## Per-Tenant Rate Limiting

```java
@Service
@RequiredArgsConstructor
public class TenantRateLimiterRegistry {

  private final RateLimiterRegistry registry;
  private final Map<String, RateLimiter> perTenant = new ConcurrentHashMap<>();

  public RateLimiter forTenant(String tenantId, int qps) {
    return perTenant.computeIfAbsent(tenantId, k ->
        registry.rateLimiter("tenant-" + tenantId,
            RateLimiterConfig.custom()
                .limitForPeriod(qps)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(50))
                .build()));
  }
}

// Usage
public BigDecimal getRate(String tenantId, String from, String to) {
  RateLimiter limiter = tenantRegistry.forTenant(tenantId, tenantQuota(tenantId));
  return RateLimiter.decorateSupplier(limiter, () -> fxClient.getRate(from, to)).get();
}
```

Beware unbounded growth: clean up tenant entries periodically or use a `Caffeine` cache with eviction.

## Metrics

| Metric | Meaning |
|---|---|
| `resilience4j.ratelimiter.available.permissions` | Permits left in current period |
| `resilience4j.ratelimiter.waiting.threads` | Callers blocked waiting for a permit |

Alert: `waiting.threads > 5` for > 1m → quota too small or burst pattern.

## Compose with Retry — Carefully

If you retry calls that the limiter rejected, you're hammering yourself, not the dep. Either:
- Add `RequestNotPermitted` to retry `ignoreExceptions`
- Or apply RateLimiter outside the Retry decoration (so retries don't get blocked, but you still respect the budget)

Banking choice: **don't retry on rate-limit rejection** — surface the degradation, let the caller decide.

```yaml
resilience4j.retry.configs.banking-default.ignoreExceptions:
  - io.github.resilience4j.ratelimiter.RequestNotPermitted
```

## When NOT to Use This Pattern

- **Inbound limiting** — use Spring Cloud Gateway's `RequestRateLimiter` (Redis-backed, fleet-wide) or your API gateway
- **Distributed rate limit across pods** — Resilience4j is **per-JVM only**. With 5 pods, the effective limit is 5× config. Use Redis-backed limiter (`bucket4j` + Redis) or gateway-side.
- **Security throttling (brute-force)** — needs persistent state and lockout. Use Spring Security + dedicated service.

## Common Pitfalls

- Forgetting `RateLimiter` is per-JVM → multiply by pod count to know fleet limit
- Setting `timeoutDuration` too high → callers queue up, latency spikes mask the problem
- Using it for inbound → wrong tool, leaks IP-level fairness
- Combined with retry on `RequestNotPermitted` → self-DOS
- Per-tenant with unbounded tenant map → memory leak; add eviction
- Limit set without observability → unknown saturation until incident
