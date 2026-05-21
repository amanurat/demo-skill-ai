# Non-Functional Requirements — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Artifact:** BA-001 (initial)
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Scope:** Read-only dashboard. No write, transfer, or transaction operations.
> **Stakeholder decisions baked in:** OPEN-001 (native currency only), OPEN-002 (ledger `balance_as_of`), OPEN-003 (hidden by default), OPEN-004 (SAVINGS/CURRENT/FIXED_DEPOSIT only).

---

## 1. Performance

| Metric | Target | Condition | Notes |
|---|---|---|---|
| p95 response time (warm cache) | < 500ms | 10-account fixture, Redis warm | Server-side; excludes mobile network transit |
| p95 response time (cold cache) | < 800ms | 10-account fixture, Redis miss | Graceful degradation target for v1 |
| p99 response time (warm cache) | < 1,000ms | Same fixture | Aspirational; not sprint exit criterion |
| AccountClient batch call | Single request | N = 10 accounts | Must NOT issue N sequential round-trips (see RISK-005) |
| Redis cache TTL | 30 seconds | Per `customer:{customerId}:accounts` key | TTL-only for v1; event-driven invalidation deferred |
| Audit event emission latency | < 200ms added overhead | Per dashboard request | Async fire-and-forget; must not block response |
| Frontend time-to-interactive (TTI) | < 3.5s | Lighthouse mobile, mid-range device | Measured on Pixel 5 equivalent or Chrome DevTools throttle |
| Frontend LCP | < 2.5s | Lighthouse mobile | Angular standalone component + lazy route |

**Concurrency assumption:** Peak concurrent users requesting balance dashboard = 50 (same order of magnitude as money-transfer staging; SA to confirm sizing via ADR).

**Measurement method:** k6 or Gatling load test on staging with realistic 10-account fixture per test customer. Evidence required in QA P2 report before sprint demo.

---

## 2. Availability and Resilience

| Attribute | Target | Notes |
|---|---|---|
| balance-dashboard service availability | 99.9% rolling 30 days | Read-only service; lower tier than money-transfer (99.95%) is acceptable |
| AccountClient dependency availability | 99.95% (inherited) | Owned by account-service; BA does not set this |
| Redis dependency | Fail-open | Cache failure MUST NOT cause dashboard failure — fall back to AccountClient |
| Circuit breaker on AccountClient | Required | Resilience4j; threshold to be determined by SA/TL (reference: money-transfer uses 50% errors in 10s) |
| Retry on AccountClient transient failure | Max 2 retries, exponential backoff | Do not flood account-service; shorter retry chain than money-transfer since read-only |
| Graceful degradation | Show cached snapshot with "may be stale" indicator | When `now() - balance_as_of > staleness_threshold` (default 60s — see BR-013) |
| "May be stale" staleness threshold | 60 seconds | Configurable via feature flag; default 60s for v1 |

---

## 3. Security

| Control | Requirement | Reference |
|---|---|---|
| Authentication | OAuth2/OIDC required; JWT validated on every request | Reuse existing auth filter chain from money-transfer |
| Authorization (IDOR guard) | JWT `sub` claim MUST be used as `customerId` filter at service level. Service MUST reject any request where the caller attempts to supply a different `customerId`. HTTP 403 returned. | AC-001-E2; OWASP A01-Broken-Access-Control |
| Account number masking | Last 4 digits visible only (`****XXXX`). Full account number MUST NOT appear in: UI DOM, API JSON response, structured logs, browser network inspector, Redis cached payload. | PDPA data minimization; money-transfer convention (see `docs/artifacts/S2-ba-money-transfer.json`) |
| Balance in logs | Balance values MUST NOT appear in non-encrypted application logs. Balances are stored only in Redis cache (encrypted at rest) and in audit-service encrypted records. | PDPA data minimization |
| PII in Redis cache | Redis cache payload containing `accountNumber` (masked), `balance`, `balance_as_of` MUST be stored with encryption at rest (AES-256-GCM, consistent with account-service at-rest encryption). | PDPA; BoT IT-Risk |
| HTTPS/TLS | TLS 1.2+ enforced at WAF / API Gateway for all traffic | Inherited from platform |
| OWASP Top 10 | banking-security agent performs early consent-coverage review at D2-D3 and full SAST gate after review | RISK-003 mitigation; sprint-goal.md exit criterion |
| Feature flag | `balance-dashboard.enabled` feature flag MUST default to `false` in non-staging environments | Prevents accidental exposure before full security gate |
| JWT scope | Endpoint requires scope `accounts:read` (or equivalent existing scope covering balance inquiry). SA/TL to confirm exact scope name against existing OAuth2 server configuration. | ASSUME-001 |

---

## 4. Compliance

### PDPA (Thailand Personal Data Protection Act B.E. 2562)

- **Data minimization:** Only fields necessary for balance comparison are returned: `accountId`, masked `accountNumber`, `accountType`, `balance`, `currency`, `balance_as_of`. Full name, address, contact details, and full account number are not returned.
- **Purpose limitation:** Data is used solely for the "balance inquiry" purpose. Audit events carry `purpose = 'balance-inquiry'` for traceability.
- **Consent reuse:** Consent from the money-transfer feature covering "view own account information" is assumed to cover balance inquiry. This MUST be verified by banking-security in the early consent-coverage review (D2-D3) per RISK-003 mitigation. If a gap is found, Legal must be engaged immediately (escalate to human user — out of agent scope).
- **Data subject rights:** No new personal data collection in this feature beyond what money-transfer already processes. Erasure requests follow existing legal-hold-override policy (7-year BoT retention applies).
- **Retention:** `balance_as_of` timestamps and account balance data displayed in this feature are sourced from account-service / ledger-service and governed by their existing retention policy (not extended by this feature).

### BoT IT-Risk Guidelines

- **Audit logging (mandatory):** Every dashboard retrieval — regardless of cache hit or miss — MUST emit an immutable audit event to `audit-service`. Minimum fields: `eventType = BALANCE_INQUIRY`, `actorId`, `customerId`, `purpose`, `channel`, `correlationId`, `timestamp (UTC)`, `result`, `accountCount`. This is a hard sprint exit criterion (sprint-goal.md section 3).
- **Audit retention:** Audit records for balance inquiries MUST be retained for the BoT-mandated period (7 years, consistent with money-transfer). No new retention configuration required if reusing existing audit-service schema.
- **Segregation of duties:** Operations staff MUST NOT have the ability to delete or modify audit records (append-only constraint inherited from audit-service design).
- **Incident reporting:** If the dashboard service experiences a security-relevant incident (e.g., IDOR exploitation attempt), the existing 1-hour BoT incident reporting SLA applies.

### PDPA + BoT Intersection — Audit Trail for Excluded Accounts

Per OPEN-003 (dormant/closed accounts hidden): the aggregation service filter that excludes non-ACTIVE accounts MUST NOT suppress the audit trail. The audit event records the access attempt (with `accountCount` reflecting only the returned active accounts). A separate observability counter tracks how many accounts were excluded by status filter, accessible to compliance/ops teams.

---

## 5. Accessibility

| Standard | Requirement | Measurement |
|---|---|---|
| WCAG 2.1 AA | Full compliance for the balance dashboard screen | Lighthouse mobile a11y score >= 90 (sprint demo exit criterion) |
| Color contrast | Normal text >= 4.5:1 against background; large text >= 3:1 | Automated Lighthouse check + manual review |
| Keyboard navigation | All rows Tab-reachable in logical order; visible focus ring | Manual QA test case in QA P1 test plan |
| Screen reader | Each row announces rank, type, masked number, balance (full spoken form), last updated | VoiceOver (iOS) + TalkBack (Android) manual test |
| Balance `aria-label` | Must use full spoken form (e.g., "45,000 baht") to prevent digit-by-digit reading | FE implementation requirement; QA to verify |
| Viewport | Renders without horizontal scroll at 375px | Chrome DevTools emulation + physical device |
| Dynamic font scaling | Layout adapts at 200% OS font size without clipping | Manual QA test |

---

## 6. Observability

| Signal | Requirement | Implementation notes |
|---|---|---|
| Distributed tracing | `traceparent` / `correlation_id` propagated through: Angular → API Gateway → balance-dashboard-service → AccountClient → audit-service | Reuse existing OTel setup (money-transfer convention) |
| Structured logs | JSON format with: `correlationId`, `customerId` (NOT full account data), `cacheHit: boolean`, `accountCount`, `durationMs`, `result` | Logback JSON appender (existing) |
| Metrics (Prometheus / Micrometer) | `balance_dashboard_requests_total{status, cache_hit}`, `balance_dashboard_duration_ms{p50, p95, p99}`, `balance_dashboard_cache_hit_ratio`, `balance_dashboard_audit_events_total{result}`, `balance_dashboard_excluded_accounts_total{reason}` | New metrics; Grafana panel required for demo |
| Grafana panel | One panel per sprint demo: request rate, error rate, p95 latency, cache hit ratio, audit-event rate | NEW asset; DevOps P2 to create using account-service dashboard template |
| Cache miss reason | `cache_miss_reason` label on metrics: `TTL_EXPIRED`, `COLD_START`, `REDIS_UNAVAILABLE` | Enables RISK-005 monitoring |
| Alerting | Alert if: error rate > 1% over 5 minutes; p95 > 800ms warm-cache over 5 minutes; audit-event emission drops to 0 over 2 minutes (potential silent compliance failure) | SA/DevOps to configure in AlertManager |

---

## 7. Scalability

| Dimension | Assumption / Target | Notes |
|---|---|---|
| Concurrent users | 50 peak concurrent dashboard loads (staging baseline) | SA to confirm horizontal scaling target in ADR |
| Horizontal scaling | Kubernetes HPA on balance-dashboard-service | Stateless aggregation layer; Redis cache is shared |
| Redis connection pool | Sized for peak concurrent connections | SA/TL to specify; reuse existing Redis cluster (ASSUME-004) |
| AccountClient fan-out | Single batched call per customer request | Prevents N×load on account-service at peak |

---

## 8. Data Minimization on Display (PDPA)

Fields exposed to customer in API response and UI (exhaustive list — nothing more):

| Field | Masking / Format | PII Level |
|---|---|---|
| `accountId` | UUID (internal ID — opaque to customer, not displayed in UI) | Low (indirect) |
| `accountNumber` | Last 4 digits only: `****XXXX` | High (masked) |
| `accountType` | Human-readable label (not enum) | Low |
| `balance` | Formatted decimal with currency symbol | Medium (financial) |
| `currency` | 3-char ISO code (THB) | Low |
| `balance_as_of` | Relative time in UI; absolute on hover (ISO 8601) | Low |
| `rank` | Integer (1-based, derived) | None |

Fields explicitly NOT returned in the API response or displayed in UI:
- Full account number
- Customer name
- Customer address / contact
- Account opening date
- Balance history
- Transaction details
- Loan balance, credit limit, credit card balance
