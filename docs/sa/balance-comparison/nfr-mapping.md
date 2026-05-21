# NFR-to-Mechanism Mapping — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Artifact:** SA-001
> **Source NFR doc:** `docs/ba/balance-comparison/nfr.md`
> **Companion docs:** [architecture.md](architecture.md), [event-flows.md](event-flows.md), [service-decomposition.md](service-decomposition.md), [adrs/](adrs/)

Every NFR row in BA's `nfr.md` is mapped to a concrete architectural mechanism below.

---

## 1. Performance

| NFR | Architectural mechanism | Reference |
|---|---|---|
| p95 < 500ms warm | Redis `GET` hit serves pre-ranked, pre-filtered JSON; no upstream calls; no re-ranking ([ADR-004](adrs/ADR-004-server-side-ranking.md)); audit publish is async fire-and-forget — off the critical path | ADR-002, ADR-004, event-flows §4 |
| p95 < 800ms cold | Single batched `AccountClient.listAccountsByCustomer()` call (SUBDEC-002 method); Resilience4j time-limiter **300ms**; filter + rank ~5ms; parallel cache-set + audit-publish (both non-blocking on response) | event-flows §5; service-decomposition §4 |
| p99 < 1000ms warm | Same as p95 warm + Lettuce pooled connection (16/pod); HikariCP equivalent for Redis (Lettuce has its own pool) | Resilience config in architecture.md §6 |
| AccountClient: single batched call (NOT N round-trips) | TL must add `listAccountsByCustomer(customerId): List<AccountInfo>` to `account-client-lib` per [service-decomposition §4](service-decomposition.md). One HTTP call returns ALL customer accounts; BDS applies `EligibilityPolicy` locally. | service-decomposition §4 |
| Redis cache TTL 30s | `SETEX balance-dashboard:customer:{customerId} 30 <json>` after successful upstream call. No active invalidation in v1 ([ADR-002](adrs/ADR-002-cache-strategy.md)) | ADR-002 |
| Audit emit overhead < 200ms | Kafka producer is **fire-and-forget** (`producer.send()` with no `.get()`) — runs on producer's IO thread; main response thread is not blocked. Producer config: `acks=1, enable.idempotence=true`. No outbox (justified in event-flows §3.4). | event-flows §3.3 |
| FE TTI < 3.5s / LCP < 2.5s | Angular standalone `<balance-dashboard>` component with lazy route; minimal initial bundle. Concern delegated to FE Dev with note in handoff. | Out of scope for SA; noted for FE |

**Concurrency assumption:** 50 peak concurrent users → `balance-dashboard-service` sized at min 2 pods (HPA targets CPU 70%), each pod has Tomcat thread pool of 200; well within capacity. **TL action:** confirm sizing in `application.yml` defaults.

---

## 2. Availability and Resilience

| NFR | Mechanism | Reference |
|---|---|---|
| BDS availability 99.9% rolling 30 days | Min 2 replicas across 2 AZs (relaxed from money-transfer's 3 AZs — read-only justifies lower tier per BA NFR §2); K8s rolling deploys with `maxUnavailable=0` | architecture.md §6, service-decomposition §7 |
| AccountClient dep availability 99.95% | Inherited from `account-service` (money-transfer NFR); not set here | — |
| Redis fail-open | Catch Lettuce `RedisException` on `GET` / `SETEX` → log + metric `cache_miss_reason=REDIS_UNAVAILABLE` → fall through to AccountClient. **No circuit breaker on Redis** — fail-fast retries are cheap. | event-flows §6, architecture.md §6 |
| Circuit breaker on AccountClient | Resilience4j config: `slidingWindowSize=100`, `failureRateThreshold=50%`, `waitDurationInOpenState=30s`. Aligns with money-transfer S3 ADR-009 convention. | architecture.md §6 |
| Retry on AccountClient transient failure | Max 2 attempts, exponential backoff (100ms, 200ms); retry only on `IOException` / 5xx (NOT 4xx). Idempotent GET — safe to retry. | architecture.md §6 |
| Graceful degradation (cached snapshot + may-be-stale indicator) | Two layers: (a) `isStale=true` per row when `now() - balanceAsOf > 60s` (BR-013); (b) v1.1 "last-known-good" cache returns previous payload with `X-Cache: STALE` when CB-open. **v1 deliberately defers (b)** — 503 on CB-open for demo simplicity (event-flows §7). | event-flows §7 |
| "May be stale" threshold | 60s, configurable via Spring profile property `balance-dashboard.staleness-threshold-seconds`. Computed per-row in `BalanceSnapshot` factory. | event-flows §2.2 |

---

## 3. Security

| NFR | Mechanism | Reference |
|---|---|---|
| OAuth2/OIDC + JWT validation | Reused existing chain from money-transfer (S3 §api-gateway). RS256 verification at `api-gateway` using JWKS from `identity-service`. Same auth filter on BDS for defense-in-depth. | architecture.md §4 |
| IDOR guard (JWT.sub == customerId) | **Two checkpoints:** (1) `api-gateway` injects `X-Customer-Id` from JWT.sub and strips client-supplied customerId; (2) BDS controller re-asserts; if any client-supplied customerId (path/query/body) differs from JWT.sub → HTTP 403 + audit `result=FORBIDDEN`. Endpoint takes NO customerId parameter — by construction. | architecture.md §4.1, event-flows §8, AC-001-E2 |
| Account number masking (last 4 only) | Reused `observability-lib` Logback masking filter from money-transfer; `AccountInfo` DTO already carries masked accountNumber from `account-service` (per money-transfer convention). BDS never sees full account number. Cache stores only masked. | service-decomposition §1.2, §2 |
| No balance in non-encrypted logs | Logback JSON encoder excludes `balance` field; structured log fields are `correlationId`, `customerId`, `cacheHit`, `accountCount`, `durationMs`, `result` only. Verified by FE/BE reviewer. | architecture.md §5 |
| PII in Redis encrypted at rest (AES-256-GCM) | Redis cluster default. BDS does not need to opt-in — cluster-level disk encryption. | service-decomposition §1.2 |
| TLS 1.2+ | Inherited from platform (WAF + api-gateway). No BDS-level config. | architecture.md §4 |
| OWASP Top 10 mitigations | A01 (broken-access): IDOR guard above; A02 (crypto): RS256, AES-256-GCM at rest; A03 (injection): no SQL in BDS (no RDBMS); A05 (misconfig): Helm chart linted; A07 (auth): OAuth2 reused; A09 (logging): structured audit. banking-security agent performs early review at D2-D3 per RISK-003. | RISK-003, S7-money-transfer for reference |
| Feature flag `balance-dashboard.enabled` = false in non-staging | Spring `@ConditionalOnProperty` on `BalanceDashboardController` bean; if flag false → 404. Default in `application-prod.yml` set to false. | service-decomposition §1.1 |
| JWT scope `accounts:read` | `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` on controller. TL to confirm exact scope name against `identity-service` config (ASSUME-001 from PM register). | architecture.md §7 |

---

## 4. Compliance

### PDPA (Thailand B.E. 2562)

| NFR | Mechanism | Reference |
|---|---|---|
| Data minimization | Response DTO carries only: `rank`, `accountId`, masked `accountNumber`, `accountType`, `balance`, `currency`, `balanceAsOf`, `isStale`. No name/address/contact/openingDate. | nfr-mapping §1, BA NFR §8 |
| Purpose limitation | Every audit event carries `purpose=balance-inquiry` (new field per [ADR-003](adrs/ADR-003-audit-event-evolution.md)) | event-flows §3 |
| Consent reuse from money-transfer | banking-security performs early consent-coverage review at D2-D3 (RISK-003 mitigation). If gap found → escalate to human via PM. | RISK-003 |
| Data subject rights / retention | No new personal data collected. Audit events governed by existing audit-service retention (7yr per BoT). | service-decomposition §3.2 |

### BoT IT-Risk Guidelines

| NFR | Mechanism | Reference |
|---|---|---|
| Mandatory audit event on every retrieval (incl. cache hit) | `AuditEventPublisher.publish()` called in `BalanceDashboardService.loadDashboard()` after every successful return path AND on FORBIDDEN/ERROR; cache layer NEVER short-circuits the audit publish | event-flows §3.2, BR-014 |
| 7-year audit retention | Inherited from existing `audit-service` (money-transfer S3 ADR-012). v2 schema fields stored alongside v1 (audit-service consumer change scoped to schema additions only, per ADR-003). | ADR-003 §audit-service Storage Side |
| Append-only audit | Inherited from money-transfer S3 ADR-012 (DB role grant + append-only trigger) | — |
| Segregation of duties | Inherited from audit-service | — |
| Incident reporting SLA (1hr BoT) | Inherited from platform PagerDuty integration | — |
| Audit for excluded accounts | `EligibilityPolicy` increments `balance_dashboard_excluded_accounts_total{reason}` per excluded account; observable in Grafana for compliance audit | event-flows §5, BA NFR §4 intersection |

---

## 5. Accessibility (WCAG 2.1 AA)

| NFR | Mechanism | Reference |
|---|---|---|
| WCAG 2.1 AA / Lighthouse a11y ≥ 90 | **FE concern** — handoff note to Designer P2 + FE Dev | architecture.md §10 |
| Server contract enables ARIA rank | Response includes per-row `rank: int` field (server-side ranking, [ADR-004](adrs/ADR-004-server-side-ranking.md)); FE binds `aria-label="Account {rank} of {total}"` directly. NO client-side sort. | ADR-004 |
| Balance as full spoken form | Response includes raw `balance` decimal; FE produces `aria-label="45000 baht"` from currency + balance. SA does NOT control aria-label string; only ensures server data is sufficient. | ADR-004 |
| Keyboard nav, focus ring, contrast, viewport, font scaling | FE-only concerns — noted in handoff for Designer/FE | — |

---

## 6. Observability

| NFR | Mechanism | Reference |
|---|---|---|
| Distributed tracing end-to-end | OTel SDK from `observability-lib` (reused); `traceparent` propagated via HTTP + Kafka headers; spans: `gateway`, `bds.controller`, `bds.cache.get`, `bds.cache.set`, `bds.account-client`, `bds.audit.publish`, `account-service`, `ledger-service` | architecture.md §5 |
| Structured JSON logs | Logback JSON encoder from `observability-lib`; fields: `correlationId`, `customerId`, `cacheHit`, `accountCount`, `durationMs`, `result`. **NO** `balance`, **NO** full `accountNumber`. | architecture.md §5 |
| Prometheus metrics | New metrics under `balance_dashboard_*` namespace (full list in architecture.md §5): `requests_total`, `duration_ms`, `cache_hit_ratio`, `audit_events_total`, `excluded_accounts_total`, `cache_miss_reason_total` | architecture.md §5 |
| Grafana panel | DevOps P2 creates new dashboard with panels: request rate, error rate, p95 latency, cache hit ratio, audit-event rate, excluded-account counter. Uses existing OTel/Prometheus pipeline (no new infra). | service-decomposition §1.4 |
| Cache miss reason labels | Metric `balance_dashboard_cache_miss_reason_total{reason=COLD_START|TTL_EXPIRED|REDIS_UNAVAILABLE}` | event-flows §2.3 |
| Alerts | Configured in existing AlertManager (DevOps P2): error rate > 1%/5min → page; p95 > 800ms warm-cache /5min → page; audit-event rate = 0/2min → page (silent compliance failure). | BA NFR §6 |

---

## 7. Scalability

| NFR | Mechanism | Reference |
|---|---|---|
| 50 peak concurrent users | Min 2 replicas across 2 AZs; HPA on CPU 70% + `balance_dashboard_requests_total` rate > 30/s/pod (custom Prometheus metric); each pod handles ~50 RPS comfortably with cached responses | service-decomposition §7 |
| Horizontal scaling | Stateless service — no session state, no in-memory cache (all state in Redis); HPA scales pods elastically | service-decomposition §7 |
| Redis connection pool | Lettuce pool size 16 per pod (50 concurrent / 2 pods × headroom); shared Redis cluster (ASSUME-004) | service-decomposition §1.2 |
| AccountClient fan-out: single batched call | Per SUBDEC-002 / [service-decomposition §4](service-decomposition.md); prevents N×load on `account-service` at peak | service-decomposition §4 |

---

## 8. Data Minimization on Display

Fields exposed in API response (exhaustive list, per BA NFR §8):

| Field | Mechanism |
|---|---|
| `accountId` | UUID, used internally; not in UI per BA |
| `maskedAccountNumber` | Already masked in `AccountInfo` from `account-service`; BDS passes through |
| `accountType` | Raw enum; FE maps to Thai/English label |
| `balance` | Decimal, sensitive — never in non-encrypted logs |
| `currency` | ISO 3-char |
| `balanceAsOf` | ISO 8601 timestamp |
| `rank` | Integer (1-based), server-computed |
| `isStale` | Boolean, server-computed (`now() - balanceAsOf > 60s`) |

Fields NOT returned: full account number, customer name/address/contact, account opening date, balance history, transactions, loan/credit-card balances. Enforced by `AccountView` DTO shape — no other fields exist.

---

## 9. Cross-Cutting

| NFR | Mechanism | Reference |
|---|---|---|
| Reuse-first policy (RISK-002) | NEW = only `balance-dashboard-service` module + Redis namespace + 1 audit event schema extension + 1 new method on `AccountClient`. Everything else reused from money-transfer infra (auth, observability, Kafka, schema registry, Resilience4j config, audit-service). | service-decomposition §2 |
| Demo-deadline discipline | 4 ADRs decisive; no over-engineering. Event-driven invalidation, last-known-good cache, and outbox pattern all deferred with documented re-open criteria. | All ADRs |
| Shift-left parallel-track readiness | This SA artifact unblocks: TL (OpenAPI + DB schema in TL hop — DB schema is empty per ADR-001), FE Dev (response shape clear), QA P1 (cache hit/miss + IDOR test cases derivable from this doc), DevOps P1 (Helm chart skeleton, Apicurio v2 registration timing in [ADR-003 §Implementation Sequencing](adrs/ADR-003-audit-event-evolution.md)) | All docs |

---

## Coverage Self-Check

| BA NFR section | Mapped? | Where |
|---|---|---|
| §1 Performance | ✅ | §1 above |
| §2 Availability and Resilience | ✅ | §2 above |
| §3 Security | ✅ | §3 above |
| §4 Compliance (PDPA + BoT) | ✅ | §4 above |
| §5 Accessibility | ✅ (FE-delegated) | §5 above |
| §6 Observability | ✅ | §6 above |
| §7 Scalability | ✅ | §7 above |
| §8 Data Minimization | ✅ | §8 above |

All eight NFR sections covered. No NFR row left without a mechanism.

---

## Open Items for Tech Lead (NFR-related)

1. **Confirm Resilience4j defaults** (timeout 300ms, retry 2x exp, CB 50%/100, bulkhead 20) in TL `application.yml` — values are SA proposals; TL refines based on actual `account-service` latency profile.
2. **Confirm JWT scope name** (`accounts:read` or alternative) against `identity-service` OAuth2 client config — ASSUME-001 in PM register.
3. **Confirm `AccountInfo.balance` for FIXED_DEPOSIT** semantics during OpenAPI authoring — SUBDEC-001 (BA default: principal + accrued interest).
4. **Audit-service storage path** for v2 fields — first-class columns vs JSONB — coordinate with audit-service owner by D5 ([ADR-003 §audit-service Storage Side](adrs/ADR-003-audit-event-evolution.md)).
5. **Lettuce connection pool size** — verify against shared Redis cluster's `maxclients` limit (ASSUME-004).

---

## References

- [architecture.md](architecture.md)
- [service-decomposition.md](service-decomposition.md)
- [event-flows.md](event-flows.md)
- [ADR-001](adrs/ADR-001-service-boundary.md), [ADR-002](adrs/ADR-002-cache-strategy.md), [ADR-003](adrs/ADR-003-audit-event-evolution.md), [ADR-004](adrs/ADR-004-server-side-ranking.md)
- BA NFR: `docs/ba/balance-comparison/nfr.md`
- BA process flow: `docs/ba/balance-comparison/process-flow.md`
