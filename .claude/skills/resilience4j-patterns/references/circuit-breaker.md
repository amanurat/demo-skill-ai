# Circuit Breaker — Resilience4j 2.x

A circuit breaker tracks the failure rate of calls and trips OPEN to stop hammering a failing dependency. After a wait period, it transitions to HALF_OPEN, allows a probe, and either re-closes or re-opens.

## States

```
        ┌─────────┐ failure rate ≥ threshold      ┌──────┐
        │ CLOSED  │ ─────────────────────────────▶│ OPEN │
        └─────────┘                               └──┬───┘
              ▲                                      │  waitDurationInOpenState
              │ probe success                        ▼
              │                                ┌────────────┐
              └──────────────────── probe ─────│ HALF_OPEN  │
                                  failure      └────────────┘
                                  ▼ (probe fails → back to OPEN)
```

- **CLOSED** — calls pass through; failures counted in sliding window
- **OPEN** — calls fail fast with `CallNotPermittedException`; fallback runs if defined
- **HALF_OPEN** — limited probe calls allowed; outcome decides next state

## Sliding Window Types

| Type | When to use |
|---|---|
| `COUNT_BASED` (last N calls) | Predictable QPS, want sample-size guarantee |
| `TIME_BASED` (last N seconds) | Bursty traffic, want time guarantee even if low QPS |

Banking default: `COUNT_BASED, size=50, minimumNumberOfCalls=20`. Small window = fast reaction; too small = noisy.

## Annotation-Based Usage

```java
@Service
@RequiredArgsConstructor
public class SettlementClient {

  private final RestClient settlementRestClient;

  @CircuitBreaker(name = "settlement", fallbackMethod = "settleFallback")
  @TimeLimiter(name = "settlement")
  @Retry(name = "settlement")
  public CompletableFuture<SettlementResult> settle(SettlementRequest req) {
    return CompletableFuture.supplyAsync(() ->
        settlementRestClient.post()
            .uri("/api/v1/settlements")
            .header("Idempotency-Key", req.idempotencyKey())
            .body(req)
            .retrieve()
            .body(SettlementResult.class));
  }

  // Fallback signature: same params + Throwable
  private CompletableFuture<SettlementResult> settleFallback(
      SettlementRequest req, CallNotPermittedException ex) {
    log.warn("settlement CB open, returning DEGRADED for transferId={}", req.transferId());
    meter.counter("settlement.fallback.cb_open").increment();
    return CompletableFuture.completedFuture(
        SettlementResult.degraded(req.transferId(), "settlement service unavailable"));
  }

  private CompletableFuture<SettlementResult> settleFallback(
      SettlementRequest req, Throwable ex) {
    log.error("settlement call failed transferId={}", req.transferId(), ex);
    throw new SettlementUnavailableException(req.transferId(), ex);
  }
}
```

**Fallback overload rules**:
- Multiple `fallbackMethod`s can exist — Spring picks by exception type (most specific wins)
- Must have same return type and same params + a trailing `Throwable` (or subtype)
- A fallback that throws an exception is fine (and often correct for banking)

## Programmatic Composition (preferred for clarity)

```java
public SettlementResult settle(SettlementRequest req) {
  Supplier<SettlementResult> call = () -> settlementRestClient.post()...;

  return Decorators.ofSupplier(call)
      .withCircuitBreaker(circuitBreakerRegistry.circuitBreaker("settlement"))
      .withRetry(retryRegistry.retry("settlement"))
      .withFallback(
          List.of(CallNotPermittedException.class),
          ex -> SettlementResult.degraded(req.transferId(), "cb open"))
      .decorate()
      .get();
}
```

## What Counts as Failure?

```yaml
recordExceptions:
  - java.io.IOException
  - java.util.concurrent.TimeoutException
  - org.springframework.web.client.HttpServerErrorException   # 5xx
ignoreExceptions:
  - com.bank.domain.BusinessRuleException                    # business 4xx — caller's fault
  - org.springframework.web.client.HttpClientErrorException.BadRequest
```

**Banking rule**: business 4xx (`Invalid amount`, `Account closed`) must **not** trip the circuit. Those are caller errors, not dependency health signals.

## Slow Calls (separate threshold)

```yaml
slowCallRateThreshold: 80      # % of calls slower than threshold
slowCallDurationThreshold: 2s
```

A dep that responds with 200 but takes 30 s is "up" but unusable. Slow-call threshold trips the CB before timeouts cascade.

## Event Listeners — wire metrics + alerts

```java
@PostConstruct
void registerListeners() {
  circuitBreakerRegistry.circuitBreaker("settlement").getEventPublisher()
      .onStateTransition(ev -> {
        log.warn("CB {} {} → {}",
            ev.getCircuitBreakerName(),
            ev.getStateTransition().getFromState(),
            ev.getStateTransition().getToState());
        if (ev.getStateTransition().getToState() == State.OPEN) {
          alertSink.fire(CircuitBreakerOpenedAlert.of(ev.getCircuitBreakerName()));
        }
      })
      .onCallNotPermitted(ev ->
          meter.counter("cb.calls.rejected", "name", ev.getCircuitBreakerName()).increment());
}
```

## Micrometer Metrics (auto-exposed if `resilience4j-micrometer` on classpath)

| Metric | What it tells you |
|---|---|
| `resilience4j.circuitbreaker.state` | Current state per instance (gauge: 0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `resilience4j.circuitbreaker.calls{kind=successful|failed|ignored}` | Counter |
| `resilience4j.circuitbreaker.slow.calls` | Slow call counter |
| `resilience4j.circuitbreaker.failure.rate` | Current failure rate |
| `resilience4j.circuitbreaker.buffered.calls` | Window fill level |
| `resilience4j.circuitbreaker.not.permitted.calls` | Fast-failed calls when OPEN |

Alert recipe (Prometheus):

```yaml
- alert: CircuitBreakerOpen
  expr: resilience4j_circuitbreaker_state{state="open"} == 1
  for: 1m
  labels: { severity: warning, team: payments }
  annotations:
    summary: "CB {{ $labels.name }} is OPEN"
```

## Per-Dependency Tuning

| Dependency type | failureRateThreshold | waitDurationInOpenState |
|---|---|---|
| Critical internal (account-service) | 50% | 30s |
| Non-critical internal (notification) | 70% | 60s |
| 3rd-party (FX rate, KYC) | 40% | 60s |
| Cache (Redis) | 60% | 10s (recover fast) |

Set per-instance with `resilience4j.circuitbreaker.instances.<name>.baseConfig: banking-default` + overrides.

## Common Pitfalls

- One CB per service-method, not one per service → granular reaction, isolates endpoints
- `permittedNumberOfCallsInHalfOpenState` too high → re-opens fail flood; default 5–10
- Counting 4xx as failure → CB opens on caller's bad input (false positive)
- Annotated method called via `this.x()` (no proxy) → CB not applied; use injected self-reference or programmatic API
- Forgetting `automaticTransitionFromOpenToHalfOpenEnabled=true` → CB stuck OPEN forever if no traffic
