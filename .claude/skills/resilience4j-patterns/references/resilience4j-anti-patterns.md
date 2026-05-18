# Resilience4j Anti-Patterns — Banking Reviewer Catalog

Each entry: **what / why bad / severity / detection / fix**.

Severity legend: `blocker` = auto-fail review · `major` = must fix before merge · `minor` = log as comment.

---

## 1. Retry on non-idempotent POST without `Idempotency-Key` — **blocker**

**What.** `@Retry` on a method that calls `POST /transfers` with no `Idempotency-Key` header propagated.

**Why bad.** Network blip after server processed request → retry re-submits → double debit. This is the canonical banking money-loss bug.

**Detect.** Grep for `@Retry` on methods that call `RestClient.post()` / `WebClient.post()`. Open each and check headers — must include `Idempotency-Key`, and the key must be stable across retry attempts.

**Fix.** Generate the key once (or propagate caller's), include in header, ensure downstream honors it. See [retry-with-backoff.md](retry-with-backoff.md#banking-rule-idempotency-is-a-prerequisite).

---

## 2. Missing TimeLimiter on external call — **blocker**

**What.** `RestClient` / `WebClient` / Kafka send / DB call with no timeout configured.

**Why bad.** Dependency hangs → thread blocks → eventually full thread pool → service down. With virtual threads, you just leak threads silently.

**Detect.** Search for `@CircuitBreaker` without sibling `@TimeLimiter`. Check `RestClient.Builder` for `requestFactory` settings (`connectTimeout`, `readTimeout`). Both must be set.

**Fix.** Add `@TimeLimiter(name = "<dep>")` and configure `timeoutDuration: 3s` (or per [bulkhead-time-limiter.md](bulkhead-time-limiter.md)).

---

## 3. Retry on `Exception` / `Throwable` — **blocker**

**What.** `retryExceptions:` omitted, or set to `[java.lang.Exception]`.

**Why bad.** Retries OOM, `RejectedExecutionException`, programming bugs, validation errors. Masks bugs and amplifies them.

**Detect.** Search `application.yml` for retry configs missing explicit `retryExceptions` list.

**Fix.** Whitelist: `IOException`, `TimeoutException`, `HttpServerErrorException`. Add domain exceptions you specifically want to retry. List in [retry-with-backoff.md](retry-with-backoff.md).

---

## 4. Fallback returns silent success — **blocker**

**What.** `fallbackMethod` returns empty `Optional`, default object, or `null`.

**Why bad.** Caller thinks operation succeeded → downstream state diverges. In banking, a "succeeded" transfer that didn't happen is fraud bait.

**Detect.** Open every fallback method. It must either return a value clearly tagged as DEGRADED, or throw a domain exception.

**Fix.** Return `Result.degraded(...)` or throw `XxxUnavailableException`. Log + increment metric. See [circuit-breaker.md](circuit-breaker.md#annotation-based-usage).

---

## 5. Retry inside `@Transactional` — **blocker**

**What.** `@Retry @Transactional` on the same method, OR retry logic inside a method already in a tx.

**Why bad.** DB connection held + lock held for full retry duration → connection pool exhausted → other endpoints fail.

**Detect.** Search for `@Retry` + `@Transactional` on same method. Also check service methods called inside a `@Transactional` block that have retry.

**Fix.** Retry at the application layer **outside** the transaction. Restructure: `applicationService.execute()` retries → calls `domainService.execute()` which is `@Transactional`.

---

## 6. ThreadPoolBulkhead with virtual threads enabled — **major**

**What.** `spring.threads.virtual.enabled=true` + `@Bulkhead(type = THREADPOOL)`.

**Why bad.** Each bulkhead call pins a platform thread → defeats virtual threads' cheap-thread benefit; you reintroduce the old saturation problem.

**Detect.** Grep `@Bulkhead(type = Type.THREADPOOL)` and check `application.yml` for `spring.threads.virtual.enabled`.

**Fix.** Use `SemaphoreBulkhead` (default). See [bulkhead-time-limiter.md](bulkhead-time-limiter.md#virtual-thread-interaction).

---

## 7. Counting 4xx as circuit-breaker failure — **major**

**What.** CB config has `recordExceptions` including `HttpClientErrorException` (4xx) or all `HttpStatusCodeException`.

**Why bad.** Caller error (bad request, validation fail) opens the breaker → legitimate requests blocked. False-positive outage.

**Detect.** Check `recordExceptions` and `ignoreExceptions` lists. Ensure 4xx is in `ignoreExceptions`.

**Fix.** Move `HttpClientErrorException` to `ignoreExceptions`. Only count `HttpServerErrorException` (5xx), `IOException`, `TimeoutException`.

---

## 8. New Idempotency-Key per retry attempt — **major**

**What.** `Idempotency-Key` generated inside the call (after retry decorator).

**Why bad.** Each retry creates a new record downstream — same as having no idempotency-key at all.

**Detect.** Code generates `UUID.randomUUID()` for the header inside the lambda passed to `Decorators.ofSupplier(...)`.

**Fix.** Generate key before the decorator (or use caller's). Reuse across all attempts of the same logical op.

---

## 9. No metrics / alerts on circuit breaker — **major**

**What.** Service has CB but no Micrometer binding or alert rule on `state`.

**Why bad.** CB silently opens — incident lasts minutes longer than necessary. Ops finds out from angry users.

**Detect.** Check `pom.xml` / `build.gradle` for `resilience4j-micrometer`. Check Grafana / Prometheus rules for `resilience4j_circuitbreaker_state`.

**Fix.** Add dep + alert (sample in [circuit-breaker.md](circuit-breaker.md#micrometer-metrics-auto-exposed-if-resilience4j-micrometer-on-classpath)).

---

## 10. CB called via `this.x()` (no proxy) — **major**

**What.** A service method calls another method on `this` that has `@CircuitBreaker` — Spring's AOP proxy is bypassed.

**Why bad.** CB silently does nothing. Discovered only during an incident.

**Detect.** Grep for `this.<methodName>(` where target method has `@CircuitBreaker` / `@Retry` / `@TimeLimiter`.

**Fix.** Inject self-reference (`@Autowired private SettlementClient self;`) or use programmatic `Decorators.ofSupplier(...)`.

---

## 11. Retry on `CallNotPermittedException` — **major**

**What.** When CB is OPEN, Resilience4j throws `CallNotPermittedException`. If Retry isn't configured to ignore it, retries hammer the CB.

**Why bad.** Each retry attempt counts as a (rejected) call → CB stays OPEN forever or transitions noisily.

**Detect.** Check retry `ignoreExceptions` for `CallNotPermittedException`. Absent = bug.

**Fix.**
```yaml
resilience4j.retry.configs.banking-default.ignoreExceptions:
  - io.github.resilience4j.circuitbreaker.CallNotPermittedException
  - io.github.resilience4j.ratelimiter.RequestNotPermitted
```

---

## 12. RateLimiter used for inbound throttling — **major**

**What.** `@RateLimiter` on a Spring `@RestController` method to throttle clients.

**Why bad.** Resilience4j is per-JVM → with N pods, real limit is N× config; not fair across instances. Also no per-user/IP tracking.

**Detect.** Grep `@RateLimiter` on `@RestController` or `@GetMapping`/`@PostMapping` methods.

**Fix.** Use API gateway (Spring Cloud Gateway with Redis-backed limiter, Envoy, Kong). See [rate-limiter.md](rate-limiter.md#when-to-use-resilience4j-ratelimiter).

---

## 13. Same CB instance shared across endpoints — **minor**

**What.** Multiple service methods reference `@CircuitBreaker(name = "default")`.

**Why bad.** One endpoint's failure opens the breaker for all endpoints — collateral damage.

**Detect.** Grep CB names across the codebase; count usages per name.

**Fix.** One CB instance per logical dependency or endpoint group (e.g., `settlement-write`, `settlement-read`).

---

## 14. Bulkhead `maxWaitDuration` too high — **minor**

**What.** `maxWaitDuration: 5s` on a bulkhead with 30 permits and 100 QPS.

**Why bad.** Callers queue, latency spikes, downstream timeouts cascade. Better to fail fast.

**Detect.** Check `application.yml` for `maxWaitDuration > 200ms`. Compare to upstream timeout budget.

**Fix.** Lower to `50–100ms`. Caller's TimeLimiter should be the latency budget, not the bulkhead wait.

---

## 15. CB tuned with `permittedNumberOfCallsInHalfOpenState = 100+` — **minor**

**What.** HALF_OPEN allows hundreds of probes → during recovery, you flood the recovering dep.

**Why bad.** Re-opens the breaker on first burst, no real recovery signal.

**Detect.** Check CB instance configs for `permittedNumberOfCallsInHalfOpenState > 20`.

**Fix.** Set to 3–10. Recovery should be cautious.

---

## 16. `automaticTransitionFromOpenToHalfOpenEnabled = false` — **minor**

**What.** CB transitions to HALF_OPEN only on next request after `waitDurationInOpenState`.

**Why bad.** If no traffic flows, CB stays OPEN forever; first user after silence pays for the probe.

**Detect.** Check CB config — default in v2 is `false`; should be explicit `true`.

**Fix.** `automaticTransitionFromOpenToHalfOpenEnabled: true`.

---

## 17. No integration test for fallback path — **minor**

**What.** Tests cover happy path only; fallback never exercised.

**Why bad.** Fallback rots — when it finally runs in prod (during incident), it throws NPE or wrong type.

**Detect.** Search tests for `WireMock.willReturn(serverError())` or similar fault injection. If absent → no fallback coverage.

**Fix.** Add Testcontainers / WireMock test: inject 5xx → assert CB opens → assert fallback returns expected DEGRADED.
