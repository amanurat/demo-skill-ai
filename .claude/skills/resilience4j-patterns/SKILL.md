---
name: resilience4j-patterns
description: Resilience4j patterns for Spring Boot 3.x banking microservices — Circuit Breaker, Retry with backoff/jitter, Bulkhead, TimeLimiter, RateLimiter, fallback strategies, operator ordering, banking-tuned defaults, anti-patterns. Use when adding any cross-service HTTP/Kafka/DB call, designing fallback behavior, or reviewing for retry/timeout/circuit-breaker correctness.
---

# Resilience4j Patterns — Fault Tolerance Skill

Reusable patterns for `resilience4j-spring-boot3` 2.x on Java 21 + Spring Boot 3.x in banking/fintech. Loaded by `banking-backend-dev` and `banking-reviewer` agents.

## When to Use

- Adding a new HTTP client call to another service
- Adding a new Kafka producer/consumer path that touches an external system
- Designing fallback behavior for a degraded dependency
- Reviewing a PR that introduces `RestClient`, `WebClient`, `FeignClient`, or external SDK
- Diagnosing cascading failures, retry storms, or saturation incidents
- Tuning circuit-breaker thresholds for a service in production

## Quick Reference

| Need | Where to Look |
|---|---|
| CircuitBreaker config, states (CLOSED/OPEN/HALF_OPEN), fallback wiring | [references/circuit-breaker.md](references/circuit-breaker.md) |
| Retry config, exponential backoff + jitter, idempotency rules, `@Retryable` vs Resilience4j | [references/retry-with-backoff.md](references/retry-with-backoff.md) |
| Bulkhead (semaphore vs thread-pool) + TimeLimiter + virtual-thread interaction | [references/bulkhead-time-limiter.md](references/bulkhead-time-limiter.md) |
| RateLimiter (outbound) — token bucket config, per-tenant limits | [references/rate-limiter.md](references/rate-limiter.md) |
| Anti-patterns flagged in review | [references/resilience4j-anti-patterns.md](references/resilience4j-anti-patterns.md) |

---

## Banking Hard Rules (inline — auto-fail in review)

These flip a review verdict to `changes_requested` immediately. See [references/resilience4j-anti-patterns.md](references/resilience4j-anti-patterns.md) for "why" and "how to detect".

- **Retry only on idempotent operations** — GET, PUT (idempotent), or POST **with** `Idempotency-Key` propagated downstream. Never blind-retry a non-idempotent POST.
- **Every external call has a TimeLimiter** — no infinite blocking. Default 3 s for sync banking calls.
- **Every cross-service HTTP call has a CircuitBreaker** — even "we trust it" internal services.
- **Bulkhead required for blocking calls inside Kafka listeners** — otherwise one slow dep saturates the consumer thread pool and lags the whole topic.
- **Fallback never silently returns success** — it must (a) return a partial/cached result tagged as degraded, OR (b) throw a domain exception. Returning empty `Optional` / default success is a blocker.
- **No retry inside the business `@Transactional`** — long DB lock + retry storm. Retry at the application layer outside the tx boundary.
- **Metrics + alerts on every CB instance** — `resilience4j.circuitbreaker.state` exported, alert when OPEN > 1 min.

---

## Operator Ordering (inline)

When composing multiple operators, **order matters**. Outermost is invoked first:

```
Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → actual call
```

Rationale:
- **Retry outermost** — so each retry attempt goes through the CB (a retry should be blocked when CB is OPEN)
- **CircuitBreaker before TimeLimiter** — CB sees the timeout as a failure to count
- **RateLimiter before TimeLimiter** — limit attempts even if calls themselves are fast
- **TimeLimiter before Bulkhead** — timeout the wait + the call, not just the call
- **Bulkhead innermost** — guards the actual resource

Spring `@CircuitBreaker` / `@Retry` annotations apply in source-code order; reverse-order if needed. Prefer programmatic `Decorators.ofSupplier(...)` for non-trivial composition (more explicit).

---

## Pattern Decision Matrix (inline)

| Failure class | Pattern |
|---|---|
| Slow / hung dependency | TimeLimiter |
| Transient network blip, 5xx | Retry (idempotent only) + backoff + jitter |
| Repeated failure, want to stop hammering | CircuitBreaker |
| Caller side overload of slow dep | Bulkhead (semaphore for VT; thread-pool for legacy) |
| You're the producer hitting a metered API | RateLimiter (outbound) |
| Inbound rate limiting (per user / tenant) | API Gateway / Spring `RateLimiter` filter (not Resilience4j) |
| Saga step failed → compensate | Outbox + saga orchestrator (not Resilience4j) |

---

## Banking-Tuned Defaults (inline)

Starting points — tune per dependency, never accept generic defaults.

### CircuitBreaker
```yaml
resilience4j.circuitbreaker:
  configs:
    banking-default:
      slidingWindowType: COUNT_BASED
      slidingWindowSize: 50
      minimumNumberOfCalls: 20
      failureRateThreshold: 50           # %
      slowCallRateThreshold: 80          # %
      slowCallDurationThreshold: 2s
      waitDurationInOpenState: 30s
      permittedNumberOfCallsInHalfOpenState: 5
      automaticTransitionFromOpenToHalfOpenEnabled: true
      recordExceptions:
        - java.io.IOException
        - java.util.concurrent.TimeoutException
        - org.springframework.web.client.HttpServerErrorException
      ignoreExceptions:
        - com.bank.domain.BusinessRuleException
        - org.springframework.web.client.HttpClientErrorException.BadRequest
```

### Retry
```yaml
resilience4j.retry:
  configs:
    banking-default:
      maxAttempts: 3
      waitDuration: 500ms
      enableExponentialBackoff: true
      exponentialBackoffMultiplier: 2.0
      exponentialMaxWaitDuration: 5s
      enableRandomizedWait: true
      randomizedWaitFactor: 0.5          # jitter ±50%
      retryExceptions:
        - java.io.IOException
        - java.util.concurrent.TimeoutException
        - org.springframework.web.client.HttpServerErrorException
      ignoreExceptions:
        - com.bank.domain.BusinessRuleException
        - org.springframework.web.client.HttpClientErrorException.BadRequest
```

### TimeLimiter
```yaml
resilience4j.timelimiter:
  configs:
    banking-default:
      timeoutDuration: 3s
      cancelRunningFuture: true
```

### Bulkhead (semaphore — safe for virtual threads)
```yaml
resilience4j.bulkhead:
  configs:
    banking-default:
      maxConcurrentCalls: 50
      maxWaitDuration: 100ms
```

### RateLimiter (outbound)
```yaml
resilience4j.ratelimiter:
  configs:
    banking-default:
      limitForPeriod: 100
      limitRefreshPeriod: 1s
      timeoutDuration: 50ms
```

---

## Idempotency Header Propagation (inline rule)

When retrying a downstream call, propagate the **caller's** `Idempotency-Key` if present, else **generate one per logical operation** and reuse across retries.

```java
String idempotencyKey = Optional.ofNullable(MDC.get("Idempotency-Key"))
    .orElseGet(() -> UUID.randomUUID().toString());

return Retry.decorateSupplier(retry, () ->
    restClient.post()
        .uri("/api/v1/transfers")
        .header("Idempotency-Key", idempotencyKey)
        .body(payload)
        .retrieve()
        .toEntity(TransferResponse.class)
).get();
```

Never generate a new key per attempt — defeats the purpose.

---

## Pre-Handoff Self-Checks

Before emitting handoff to `banking-reviewer`:

- [ ] Every outbound HTTP/Kafka/DB-to-external call has a `TimeLimiter`
- [ ] Every cross-service HTTP call has a `CircuitBreaker`
- [ ] Retry is configured with backoff + jitter, max 3 attempts default
- [ ] Retry attached only to idempotent operations (or with `Idempotency-Key`)
- [ ] Fallback method explicitly surfaces degradation (no silent success)
- [ ] Metrics exposed: `resilience4j.circuitbreaker.calls`, `state`, `slow.calls`, `failure.rate`
- [ ] Alert rule added: CB state = OPEN for > 1 min
- [ ] No retry inside `@Transactional`
- [ ] Bulkhead present on any blocking call invoked from Kafka listener
- [ ] Integration test covers fallback path (e.g., WireMock 5xx → CB opens → fallback)
- [ ] Tuning values documented in service README or ADR

---

## Reference Index

- [circuit-breaker.md](references/circuit-breaker.md) — config, states, sliding-window types, fallback methods, event listeners, metrics
- [retry-with-backoff.md](references/retry-with-backoff.md) — backoff/jitter math, idempotency rules, Spring `@Retryable` comparison, retry-on exception lists
- [bulkhead-time-limiter.md](references/bulkhead-time-limiter.md) — semaphore vs thread-pool bulkhead, virtual-thread interaction, TimeLimiter + CompletableFuture
- [rate-limiter.md](references/rate-limiter.md) — token bucket, when to use Resilience4j vs gateway, per-tenant limits
- [resilience4j-anti-patterns.md](references/resilience4j-anti-patterns.md) — concrete smells with severity + how to detect

---

## How This Skill is Loaded

Referenced (not auto-injected) by:
- [`.claude/agents/banking-backend-dev.md`](../../agents/banking-backend-dev.md) — Read before any task adding an external dependency call
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read when reviewing PRs that touch HTTP clients, listeners with downstream calls, or fallback methods

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work.
