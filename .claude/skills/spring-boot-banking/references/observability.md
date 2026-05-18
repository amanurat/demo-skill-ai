# Observability & Performance Tuning

Reference loaded on demand by `spring-boot-banking` skill. Cover logging, metrics, tracing, health checks, and JVM performance.

## Logging

### Stack
- **Logback** with **JSON encoder** (`logstash-logback-encoder`)
- Spring Boot default `LoggerFactory.getLogger(...)`
- Structured fields, never string concatenation

### Correlation
- **`traceparent`** header (W3C Trace Context) propagated via interceptor → MDC
- Logger pattern includes `traceId`, `spanId`, `customer_id` (when in request scope)

### Log Levels
| Level | When | Example |
|---|---|---|
| ERROR | Actionable, on-call should care | Saga compensation failed after retries |
| WARN | Degraded but recoverable | Circuit breaker opened, retry succeeded |
| INFO | State changes worth a record | Transfer completed, user logged in |
| DEBUG | Dev / staging only | Request payload, query parameters |
| TRACE | Off in prod | Detailed flow tracing |

### Hard Rules
- **NEVER log**: passwords, JWTs, full card numbers, SSN, full account numbers (mask to last 4 digits), full balance values (per PDPA minimization)
- Use structured fields: `log.info("transfer completed", kv("transferId", id), kv("amount", amount))` — never `log.info("transfer " + id + " for " + amount)`
- Logger naming: class-based, not feature-based

---

## Metrics (Micrometer → Prometheus)

### RED Metrics (per endpoint, auto from Spring Boot Actuator)
- **R**ate — `http_server_requests_seconds_count{uri="/api/v1/transfers"}`
- **E**rrors — same metric, filter by `status` label
- **D**uration — `http_server_requests_seconds{quantile="0.95"}`

### Custom Business Metrics
Define in domain code, register via `MeterRegistry`:

```java
@Component
@RequiredArgsConstructor
public class TransferMetrics {
    private final MeterRegistry registry;

    public void recordCompleted(String currency, BigDecimal amount) {
        registry.counter("transfers_completed_total",
            "currency", currency).increment();
        registry.summary("transfer_amount",
            "currency", currency).record(amount.doubleValue());
    }

    public void recordFailed(String reason) {
        registry.counter("transfers_failed_total",
            "reason", reason).increment();
    }
}
```

### Required Metrics per Service
- `transfers_completed_total{result, currency}`
- `transfer_amount_thb_sum`
- `saga_compensation_total{result}`
- `daily_limit_rejections_total`
- `idempotent_replay_total`
- `circuit_breaker_state{name}` (from Resilience4j)

### Cardinality Caveat
- **Never use unbounded labels** (no `customer_id`, `transfer_id` as label) — Prometheus dies
- Bucket high-cardinality dims into ranges (`amount_bucket=under_10k|10k_100k|over_100k`)

---

## Tracing (OpenTelemetry)

### Setup
- **`opentelemetry-spring-boot-starter`** for auto-instrumentation
- Spans for HTTP in/out (Spring MVC + WebClient), DB calls (JDBC), Kafka produce/consume
- Manual spans for Saga steps:

```java
@WithSpan("saga.transfer.debit-source")
public void debitSource(Account source, Money amount) { ... }
```

### Propagation
- W3C Trace Context (`traceparent`, `tracestate`) — propagated automatically by OTel
- Logs include `traceId` so logs + traces correlate in Grafana / Jaeger / Tempo

### Export
- OTLP gRPC to `otel-collector` (sidecar or daemonset)
- Collector fans out to Tempo (traces) + Prometheus (metrics) + Loki (logs)

---

## Health Probes (Spring Boot Actuator)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, db, kafka
```

- `/actuator/health/liveness` — process alive (used by K8s liveness probe)
- `/actuator/health/readiness` — ready to serve (DB + Kafka reachable; used by K8s readiness probe)
- `/actuator/prometheus` — metrics scrape target
- **Disable** `/actuator/env`, `/actuator/heapdump`, `/actuator/threaddump` in prod

---

## Performance Tuning (JVM)

### Garbage Collector
- **G1GC** default (good for most services)
- **ZGC** for low-latency hot paths (`transfer-service`, `account-service`):
  ```
  -XX:+UseZGC -XX:+ZGenerational
  ```
- **ShenandoahGC** alternative for low-pause concurrent GC

### Heap Sizing
- Start at `-Xmx2g` for typical microservice
- Container-aware: `-XX:MaxRAMPercentage=75` (let JVM size to container limit)
- Always set `-Xms = -Xmx` in prod (avoid heap resize pauses)

### Concurrency
- **`@Async`** for fire-and-forget (notification, audit emission)
- **`CompletableFuture`** for parallel calls (multiple independent service queries)
- **Project Reactor** (`Mono`/`Flux`) for streaming / SSE / WebFlux endpoints

### Caching
- **Caffeine** for in-process cache; **Redis** for shared cache
- **Always set explicit TTL** — no infinite caches
- Use `@CacheEvict` on writes that invalidate cached reads
- **Cache stampede protection**: jitter + single-flight (`@Cacheable(sync = true)` for Caffeine)

### Database
- Review query plans on hot paths (`EXPLAIN ANALYZE` in dev)
- Add indexes **only after measurement** — premature indexing slows writes
- Use connection pool metrics to spot saturation (`hikaricp_connections_active`)

---

## SLO / Alert Thresholds (default per service)

| Metric | Threshold | Action |
|---|---|---|
| Error rate (5xx) | > 1% over 5 min | PagerDuty page |
| p95 latency | > SLA (e.g., 1s for transfer) for 5 min | PagerDuty page |
| p99 latency | > 2× p95 | Slack alert |
| GC pause | > 500ms for 1 min | Slack alert |
| Pod restart | > 3 in 10 min | PagerDuty page |
| Kafka consumer lag | > 10k for 5 min | Slack alert |
| `transfers_failed_ratio` | > 5% | PagerDuty page |
| `saga_compensation_failed` | > 0 | Immediate page |

---

## When to Use This Reference

- Wiring up observability in a new service
- Investigating a latency or memory issue
- Designing alert rules for a new endpoint
- Choosing a GC for a low-latency service
- Adding a new business metric
- Reviewing a PR that introduces caching or async code
