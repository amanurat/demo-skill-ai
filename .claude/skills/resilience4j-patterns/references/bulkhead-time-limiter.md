# Bulkhead + TimeLimiter — Resilience4j 2.x

These two patterns work together to **bound resource consumption per dependency** so one slow/saturated dep can't take down the whole service.

## Bulkhead Types

Resilience4j has two implementations:

| Type | Mechanism | Use when |
|---|---|---|
| `SemaphoreBulkhead` | Permits → counter | Virtual threads (Java 21+) or async non-blocking |
| `ThreadPoolBulkhead` | Dedicated thread pool | Legacy blocking code on platform threads |

**Banking default**: `SemaphoreBulkhead` paired with virtual threads. Avoid `ThreadPoolBulkhead` unless you have a specific reason — it adds context-switch cost and forks a thread per call.

## SemaphoreBulkhead Config

```yaml
resilience4j.bulkhead:
  configs:
    banking-default:
      maxConcurrentCalls: 50
      maxWaitDuration: 100ms      # caller waits this long for a permit, then fails fast
  instances:
    settlement:
      baseConfig: banking-default
      maxConcurrentCalls: 30      # tighter for slow dep
```

Sizing rule of thumb:

```
maxConcurrentCalls = (target latency budget × QPS) × safety factor (1.2–1.5)
```

Example: settlement p95 = 200ms, peak QPS = 100 → 0.2 × 100 × 1.3 ≈ 26. Round up to 30.

## ThreadPoolBulkhead Config (use sparingly)

```yaml
resilience4j.thread-pool-bulkhead:
  configs:
    legacy-blocking:
      maxThreadPoolSize: 20
      coreThreadPoolSize: 5
      queueCapacity: 50
      keepAliveDuration: 20ms
```

Wraps your call in a managed `ExecutorService` — caller gets a `CompletableFuture` back. **Don't use this on virtual threads** (defeats the purpose of cheap threads).

## TimeLimiter

Bounds the duration of an async call. Cancels the underlying `Future` on timeout.

```yaml
resilience4j.timelimiter:
  configs:
    banking-default:
      timeoutDuration: 3s
      cancelRunningFuture: true     # interrupt the thread
```

**Banking defaults by call type**:

| Call kind | Timeout |
|---|---|
| Sync HTTP (internal service) | 3s |
| Sync HTTP (3rd party / KYC / FX) | 10s |
| DB query (single statement) | 2s |
| Kafka send (sync .get) | 5s |
| Long background job | 60s+ (separate executor) |

## TimeLimiter Requires Async Return Type

```java
@TimeLimiter(name = "settlement", fallbackMethod = "timeoutFallback")
public CompletableFuture<SettlementResult> settle(SettlementRequest req) {
  return CompletableFuture.supplyAsync(() -> doCall(req), virtualThreadExecutor);
}

private CompletableFuture<SettlementResult> timeoutFallback(
    SettlementRequest req, TimeoutException ex) {
  meter.counter("settlement.timeout").increment();
  return CompletableFuture.failedFuture(
      new SettlementTimeoutException(req.transferId(), ex));
}
```

If your code is sync, wrap it in `supplyAsync(...)` on a dedicated executor. **Don't use the common ForkJoinPool** for blocking calls.

## Composition with CircuitBreaker

Recommended order (annotation declaration top-down = outer-to-inner):

```java
@Retry(name = "settlement")              // outermost
@CircuitBreaker(name = "settlement")
@TimeLimiter(name = "settlement")
@Bulkhead(name = "settlement", type = Type.SEMAPHORE)
public CompletableFuture<SettlementResult> settle(SettlementRequest req) { ... }
```

When this composes:
1. Retry runs N times
2. Each attempt enters the CB (fast-fails if OPEN — counted as failure attempt for retry, but `CallNotPermittedException` should be in `ignoreExceptions`)
3. TimeLimiter wraps the future with a timeout
4. Bulkhead admits the call (or fast-fails if all permits taken)
5. Actual call executes

## Virtual Thread Interaction

With `spring.threads.virtual.enabled=true`, the Tomcat request executor is virtual. Bulkhead semaphore counts virtual threads holding a permit — works correctly.

`ThreadPoolBulkhead` will pin a platform-thread-per-call — defeats virtual threads. Use semaphore only.

For `TimeLimiter`, supply a virtual-thread-per-task executor:

```java
@Bean(name = "vtExecutor")
public ExecutorService virtualThreadExecutor() {
  return Executors.newVirtualThreadPerTaskExecutor();
}

CompletableFuture<SettlementResult> fut =
    CompletableFuture.supplyAsync(() -> doCall(req), virtualThreadExecutor);
```

See [`concurrency-virtual-threads`](../../concurrency-virtual-threads/SKILL.md) for full guidance.

## Metrics (auto-exposed)

| Metric | Meaning |
|---|---|
| `resilience4j.bulkhead.available.concurrent.calls` | Free permits |
| `resilience4j.bulkhead.max.allowed.concurrent.calls` | Configured cap |
| `resilience4j.timelimiter.calls{kind=successful|failed|timeout}` | Counter |

Alert: bulkhead permits = 0 for > 30s → saturated dependency.

## Bulkhead vs Thread-Pool Sizing in Spring Boot

If you're on **platform threads** (`server.tomcat.threads.max=200` default), and your dep has its own thread pool (e.g., Apache HttpClient 5 with `maxConnPerRoute=50`), the effective bulkhead is the smallest of those numbers — don't double-cap unintentionally.

With **virtual threads**, the Tomcat executor is unbounded → Resilience4j bulkhead becomes the only protection. **Setting bulkhead is no longer optional**.

## Common Pitfalls

- `ThreadPoolBulkhead` on virtual threads → pins, defeats Loom
- `maxWaitDuration` too long → callers queue up, latency cascades
- TimeLimiter without `cancelRunningFuture=true` → underlying call keeps running, thread leak
- Wrapping sync calls in `supplyAsync` on common ForkJoinPool → starves CPU
- Bulkhead too tight → false-positive saturation under healthy burst
- Missing TimeLimiter on Kafka `template.send(...).get()` → infinite hang if broker stalls
