# Changelog — Balance Comparison Dashboard

All notable changes to this feature are documented in this file.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.0.0-RC1] — 2026-05-22

### Added

**Core feature — Balance Comparison Dashboard (US-BC-001 through US-BC-005)**

- New `balance-dashboard-service` (BDS) microservice: hexagonal architecture, no RDBMS, standalone deployable (ADR-001). Spring Boot 3.x, Java 21, Maven module `backend/balance-dashboard-service`.
- `GET /api/v1/balance-dashboard` endpoint returning the authenticated customer's ACTIVE accounts (SAVINGS / CURRENT / FIXED_DEPOSIT) ranked by `balance` DESC with `accountId` ASC tie-break (US-BC-001, US-BC-002, ADR-004).
- `EligibilityPolicy` filtering out LOAN and CREDIT_CARD account types before ranking (BR-004).
- Server-side `Ranker` — deterministic, cache-friendly, `rank` field is authoritative for ARIA `aria-label="Account {rank} of {total}"` (ADR-004, AC-005-H2).
- Angular `<balance-dashboard>` frontend component with account row cards, Heroicons v2 per account type, and staleness badge (`<balance-staleness-badge>`) (US-BC-001, US-BC-004).

**Resilience (US-BC-003 / NFR performance and availability)**

- Resilience4j circuit breaker, retry (1 initial + 2 exponential: 100ms, 200ms), time limiter (300ms), and bulkhead (20 concurrent calls) wired to `AccountClientAdapter` (implementation-notes §3).
- Operator order: TimeLimiter (outer) → CircuitBreaker → Retry → Bulkhead (inner) — total attempt budget is bounded by the time limiter.
- Failure mode contract: timeout, CB-open, bulkhead-full, and 5xx-after-retries all return `503` + RFC 7807 `ProblemDetail` with `Retry-After: 5`.

**Redis TTL cache (US-BC-003)**

- Redis Lettuce cache with 30-second TTL (`SETEX` atomic write — never `SET` then `EXPIRE`) (ADR-002, implementation-notes §4).
- Key prefix: `balance-dashboard:customer:{customerId}`.
- Fail-open on `RedisException`: logs + increments `cache_miss_reason_total{reason=REDIS_UNAVAILABLE}`, falls through to `AccountClientAdapter`. No circuit breaker on Redis.
- `X-Cache: HIT | MISS` response header; `meta.freshness: live | snapshot` in response body.
- `Cache-Control: private, no-store` on all responses (PDPA — response body contains financial data).

**Kafka audit events (US-BC-005 / BR-014 / ADR-007)**

- `AuditEventPublisher` hexagonal port + `KafkaAuditEventPublisher` infrastructure adapter: async fire-and-forget (no `.get()`, no outbox — SA ADR-001 at-most-one-event-loss-per-outage accepted in v1).
- `AuditEventRecord` value object: metadata-only, 9 fields (`eventType`, `actorId`, `channel`, `correlationId`, `timestamp`, `result`, `purpose`, `cacheHit`, `accountCount`).
- Three audit call sites: SUCCESS (cache hit and miss), FORBIDDEN (IDOR mismatch), ERROR (upstream failure). Cache layer never short-circuits audit (BR-014).
- Avro v2 `AuditEventRecorded` schema (`com.bank.compliance.audit.v2`) — BACKWARD-compatible extension of money-transfer Avro v1 with three new optional fields (`purpose`, `cacheHit`, `accountCount`) (ADR-003).
- Kafka topic: `audit.event-recorded`. Apicurio schema registry registration target: D6 before D8 BDS deploy.
- Metrics: `audit_events_total{result=PUBLISHED|FAILED}` registered with Prometheus/Micrometer.

**IDOR protection — `CustomerIdResolver` pattern (ADR-006 / Security C-3)**

- `IborCheckFilter` (`OncePerRequestFilter`): the single, ArchUnit-enforced code site permitted to read `X-Customer-Id` header. Mismatch against JWT `sub` → emit `AuditEventRecord.forbidden(...)` then return `403`.
- `CustomerIdResolver`: derives `customerId` from JWT `sub` claim only. No `HttpServletRequest` dependency — structurally impossible to switch source to a header.
- ArchUnit rule `CustomerIdSourceRule`: enforces at compile time that no class outside `IborCheckFilter` calls `request.getHeader("X-Customer-Id")`.
- Controller wiring: `@AuthenticationPrincipal Jwt jwt` → `customerIdResolver.resolve(jwt)`.

**Design token integration (ADR-005)**

- Style Dictionary pipeline: `docs/design/_shared/tokens.json` → `frontend/src/styles/tokens.scss` + `frontend/src/styles/tokens.css`.
- Dual-emit: SCSS `$variables` for compile-time arithmetic; CSS `--custom-properties` on `:root` for runtime (dark-mode ready).
- Alias resolution at build time — generated CSS contains no `var()` chains.
- Mandatory `@media (prefers-reduced-motion: reduce)` block in CSS output (WCAG 2.1 §2.3.3).
- CI job `design-tokens-up-to-date`: `git diff --exit-code` fails PR if generated files lag source.

**PII masking in logs (Security F-1 + F-2 / CWE-532)**

- `LogMasking` utility class (`infrastructure.rest`): `maskId(UUID)`, `maskId(String)`, `maskKey(String)` — all calls to `log.warn/info` that bind `customerId={}`, `accountId={}`, or `key={}` pass through this utility.
- `logback-spring.xml`: profile-aware appenders (`dev/default`, `staging`, `prod`) with regex replace for full UUIDs (`[0-9a-f]{8}-...-...` → `xxxxxxxx-****`) and 10–16 digit PAN-like sequences (`\b\d{10,16}\b` → `****-REDACTED`).
- `LogMaskingTest` unit tests asserting no full UUID pattern survives masking.

**CI/CD pipeline skeleton (DevOps P1 — shift-left)**

- `.github/workflows/balance-dashboard-service.yml` — four P1 stages:
  - `build-and-test`: `./mvnw clean verify` + JaCoCo 80% gate + ArchUnit `CustomerIdSourceRule`
  - `design-tokens-up-to-date`: Style Dictionary build + `git diff --exit-code`
  - `openapi-lint`: Spectral lint on BDS + account-service OpenAPI specs
  - `helm-lint`: `helm lint` + `helm template | kubectl apply --dry-run`
- Helm chart skeleton: `infra/helm/balance-dashboard-service/` — 2-replica deployment with AZ `topologySpreadConstraints`, HPA (CPU 70% + custom metric), NetworkPolicy (ingress: api-gateway only; egress: account-service + Redis + Kafka + Apicurio + OTel), Vault CSI secret placeholders, ServiceMonitor for Prometheus.
- Feature flag bootstrap: `staging` profile: `balance-dashboard.enabled=true`; `prod` profile: `false` (returns `501` until BoT sign-off).

---

### Security

- **Resolved CWE-532 (PII in logs) — F-1 HIGH:** `LogMasking.maskId` / `maskKey` wrappers applied at all `customerId={}`, `accountId={}`, and `key={}` log-binding call sites in `BalanceDashboardController`, `BalanceDashboardService`, `AccountClientAdapter`, and `RedisCacheRepository`. Unit tests assert no full UUID survives masking.
- **Resolved CWE-532 (defense-in-depth) — F-2 MEDIUM:** `logback-spring.xml` added with profile-aware appenders and regex masking as a secondary safety net.
- STRIDE threat model passed: Spoofing (JWT/OIDC), Tampering (IborCheckFilter + ArchUnit), Repudiation (audit on all paths), Information Disclosure (logs masked + Redis TLS), DoS (Resilience4j), Elevation (controller-level `@PreAuthorize`).
- OWASP A09 Security Logging: now PASS (was fail before F-1/F-2 fix).

---

### Known Limitations / Deferred (backlog carry-forward)

- **BC-DEBT-002** — JWT stored in `sessionStorage` (XSS-recoverable). Migration to HttpOnly cookie + BFF pattern deferred to v1.1. Tracked as OWASP A07 non-blocking finding F-3.
- **C-1** — Privacy notice / consent UI not yet implemented. PDPA consent withdrawal flow deferred to v1.1. Tracked as security finding F-4.
- **STALE fallback (last-known-good cache on CB-open)** — v1 returns `503` when circuit breaker is open and no cached snapshot is available. Stale fallback (`X-Cache: STALE`, `meta.freshness: stale`) deferred to v1.1.
- **Event-driven cache invalidation** — v1 uses TTL-only (30 seconds). Event-driven invalidation on `AccountDebited` / `AccountCredited` deferred to v1.1 (ADR-002).
- **FIXED_DEPOSIT accrued interest** — balance reflects principal-only if `account-service` / `ledger-service` does not include accrued interest in `AccountInfo.balance`. Tracked as ASSUMPTION-TL-001; default action is Option A (ship principal-only v1, flag in demo script). FIXED_DEPOSIT can be removed from `EligibilityPolicy` via config flip (`balance-dashboard.eligible-types`) with no code change if needed.
- **Smoke tests** — pending staging deploy (DevOps P2).
- **DAST** — deferred to staging (DevOps P2).
- **OBS-042** — `logback-spring.xml` inline `<replace>` regex to be replaced by `observability-lib MaskingConverter` once that library ships.

---

*CHANGELOG · balance-comparison · banking-docs · 2026-05-22*
