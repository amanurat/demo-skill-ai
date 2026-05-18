# Retry with Backoff + Jitter — Resilience4j 2.x

Retry is the most-abused resilience pattern. Done wrong, it amplifies outages (retry storm) and creates double-spend bugs.

## Banking Rule: Idempotency Is a Prerequisite

| Op | Safe to retry blind? |
|---|---|
| GET | ✅ |
| PUT (idempotent by REST semantics) | ✅ |
| DELETE (idempotent) | ✅ |
| POST | ❌ — only if `Idempotency-Key` propagated downstream |
| Database INSERT | ❌ — only with natural unique constraint or upsert |
| Kafka send | ✅ if producer `enable.idempotence=true` |

**A retry without idempotency is a money-loss bug, not a resilience pattern.**

## Backoff Math — Why Exponential + Jitter

| Strategy | Behavior under outage |
|---|---|
| Fixed 1s × 3 | All callers retry at the same instant → repeated thundering herd |
| Exponential (1s, 2s, 4s) | Spreads retries — but if all callers start at the same time, still synchronized |
| Exponential + jitter (±50%) | Decorrelates retry timing across the fleet |

Resilience4j config:

```yaml
maxAttempts: 3                       # includes the original call
waitDuration: 500ms
enableExponentialBackoff: true
exponentialBackoffMultiplier: 2.0
exponentialMaxWaitDuration: 5s       # cap to prevent stupid-long waits
enableRandomizedWait: true
randomizedWaitFactor: 0.5            # jitter ±50% of computed wait
```

Resulting attempt timing (approx): t=0, t=500–750ms, t=1000–1500ms, capped at 5s.

## Retry-On Exception Selection

Whitelist exceptions that **indicate transient failure**:

```yaml
retryExceptions:
  - java.io.IOException
  - java.util.concurrent.TimeoutException
  - org.springframework.web.client.HttpServerErrorException        # 5xx
  - org.springframework.dao.TransientDataAccessException
ignoreExceptions:
  - com.bank.domain.BusinessRuleException
  - org.springframework.web.client.HttpClientErrorException        # 4xx (caller error)
  - jakarta.validation.ConstraintViolationException
```

**Never** retry on `Throwable` / `Exception` — silently retries OOMs, programming bugs, and `RejectedExecutionException`.

## HTTP Status Code Discrimination

Spring `RestClient` throws different exceptions per status:
- `HttpClientErrorException` for 4xx → ignore (caller fault)
- `HttpServerErrorException` for 5xx → retry
- `ResourceAccessException` (wraps IOException) → retry

For finer control:

```java
.onErrorMap(IOException.class, ex -> new TransientDownstreamException(ex))
.onErrorMap(HttpServerErrorException.class, ex -> {
  if (ex.getStatusCode().value() == 503) {
    return new TransientDownstreamException(ex);   // retry
  }
  return new PermanentDownstreamException(ex);     // don't retry
})
```

## Honor `Retry-After` Header (HTTP 429 / 503)

Resilience4j doesn't auto-honor `Retry-After`. Wire it manually:

```java
public TransferResponse send(TransferRequest req) {
  for (int attempt = 1; attempt <= 3; attempt++) {
    try {
      return restClient.post().uri("/transfers").body(req).retrieve().body(TransferResponse.class);
    } catch (HttpClientErrorException.TooManyRequests ex) {
      Duration wait = parseRetryAfter(ex.getResponseHeaders())
          .orElse(Duration.ofSeconds(1L << attempt));
      sleep(wait);
    }
  }
  throw new DownstreamSaturatedException();
}
```

## Resilience4j vs Spring `@Retryable`

| | Resilience4j `@Retry` | Spring Retry `@Retryable` |
|---|---|---|
| Composable with CircuitBreaker / TimeLimiter | ✅ Yes (same library) | Manual |
| Reactive support (Mono/Flux) | ✅ | ❌ |
| Metrics auto-exposed | ✅ Micrometer | Manual |
| Programmatic API | ✅ `Decorators` | Limited (`RetryTemplate`) |
| **Banking default** | ✅ Use Resilience4j | Avoid unless legacy |

Don't mix the two — pick one stack per service.

## Composing Retry with CircuitBreaker

When CB is OPEN, `CallNotPermittedException` is thrown — **don't retry it**. Add to `ignoreExceptions`:

```yaml
resilience4j.retry.configs.banking-default.ignoreExceptions:
  - io.github.resilience4j.circuitbreaker.CallNotPermittedException
```

Otherwise Retry will hammer the CB → counts as failures → permanent OPEN.

## Programmatic Usage

```java
Retry retry = retryRegistry.retry("settlement");
CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("settlement");
TimeLimiter tl = timeLimiterRegistry.timeLimiter("settlement");

Supplier<CompletableFuture<SettlementResult>> decorated = Decorators
    .ofCompletionStage(() -> CompletableFuture.supplyAsync(this::callSettlement))
    .withCircuitBreaker(cb)
    .withRetry(retry, executor)         // executor for async retry scheduling
    .withFallback(List.of(CallNotPermittedException.class), this::degradedResult)
    .decorate();

return decorated.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
```

## Event Listener (for observability)

```java
retry.getEventPublisher()
    .onRetry(ev ->
        log.warn("retry attempt={} name={} lastEx={}",
            ev.getNumberOfRetryAttempts(),
            ev.getName(),
            ev.getLastThrowable() != null ? ev.getLastThrowable().getClass().getSimpleName() : "n/a"))
    .onError(ev ->
        log.error("retry exhausted name={} totalAttempts={}",
            ev.getName(), ev.getNumberOfRetryAttempts(), ev.getLastThrowable()));
```

## Retry Budget (advanced — limit total system-wide retries)

Pure Resilience4j doesn't have a fleet-wide budget. Approximations:
- Cap `maxAttempts` low (2–3)
- Rate limiter in front of retry (limit retry QPS, not just initial)
- Service mesh (Envoy, Istio) `retry_budget` config

## Common Pitfalls

- Retry inside `@Transactional` → long-held DB locks
- Retry on `Exception` → masks programming bugs
- Retry without backoff → DOS the dependency
- New `Idempotency-Key` per attempt → each retry creates new record downstream
- `maxAttempts` includes original call — `3` means "1 try + 2 retries", not 3 retries
- Caller and callee both retry → multiplied calls (3 × 3 = 9 hits per logical op)
- Forgetting `executor` on async retry → scheduling on common ForkJoinPool starves CPU
