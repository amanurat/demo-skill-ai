# Decisions Log — Money Transfer

> Consolidated ADRs + key assumptions across all SDLC phases for the **money-transfer** feature.
> Source artifacts: `docs/artifacts/S3-solution-architect-money-transfer.json` (ADRs 001–012) and `docs/artifacts/S4-tech-lead-money-transfer.json` (ADRs 013–016).
> Backend-dev (S5) implementation-only decisions are listed in the final section.

---

## Architecture Decision Records

### ADR-001: Saga Coordination — Orchestration vs Choreography
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Orchestration — `transfer-service` is the saga coordinator with persisted `saga_state` in its own DB.
- **Rationale:** Money transfer has strict compensation order (only the debit needs reversal if credit fails). Orchestration gives a single explicit state machine that is testable and observable. Saga state is persisted in `transfer-service.saga_state` to survive pod restarts (US-008 AC4 "survive network partition during saga").
- **Rejected options:**
  - Choreography (event-driven saga, no central coordinator) — compensation order hard to enforce; state diagnosis requires reconstructing across multiple topics.
  - Two-Phase Commit (2PC / XA across services) — synchronous global locks kill p95 < 1s; PostgreSQL XA operationally fragile.

### ADR-002: Idempotency-Key Storage — Per-service RDBMS Table vs Shared Redis
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Per-service `transfer_idempotency` table in `transfer-service` PostgreSQL with TTL cleanup job; key SHA-256 hashed for PK; `expires_at` indexed for 24h purge.
- **Rationale:** Idempotency for financial writes MUST be transactional with the business write (atomic insert of idempotency row + transfer row in same DB transaction). Shared Redis would require two-phase coordination between Redis and PostgreSQL — a Redis loss between commits would cause double-debits. `common-libs/idempotency-lib` standardizes the pattern.
- **Rejected options:**
  - Shared Redis cluster with `SETNX` — not transactional with business DB write.
  - Cache-aside in Redis + DB write — same atomicity problem.
  - Centralized idempotency-service — adds a synchronous hop; better as a shared library.

### ADR-003: Event Publishing Reliability — Transactional Outbox vs Debezium CDC
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Transactional Outbox pattern — write business row + outbox row in the same DB transaction; in-service relay polls outbox and publishes to Kafka, marking `dispatched=true` upon Kafka ack.
- **Rationale:** Outbox guarantees only events for committed state are published. Works with the existing PostgreSQL + Kafka stack with no new infrastructure (Debezium needs a Kafka Connect cluster). Polling worker uses `FOR UPDATE SKIP LOCKED` for horizontal scaling. RPO ≤ 1 min satisfied.
- **Rejected options:**
  - Debezium CDC on PostgreSQL logical replication — requires Kafka Connect ops surface; no clean filter for intentional vs incidental writes.
  - Direct publish to Kafka inside the same transaction (XA) — Kafka does not participate in XA.
  - After-commit publish without outbox — crash between commit and publish loses the event.

### ADR-004: Daily Transfer Accumulator — RDBMS Optimistic Lock vs Redis Atomic Increment
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** PostgreSQL row in `transfer-service.daily_transfer_accumulator` with optimistic lock (`version` column) updated inside the same DB transaction as the transfer write; failed CAS triggers retry with re-read.
- **Rationale:** Daily accumulator is part of the same business invariant as the transfer (US-005 rejection must be atomic with the decision to debit). Putting it in the same DB lets it participate in the same transaction. Real per-account contention is low; at 500 TPS across distinct accounts, lock contention is negligible. AML threshold (US-009) reuses the same accumulator with a `aml_threshold_breached` boolean for dedupe.
- **Rejected options:**
  - Redis `INCR` + DB ledger sync at EOD — not transactional with the business decision; drift risk; compensation requires manual decrement.
  - Compute on-demand `SUM WHERE date=today` — linear scan grows with daily transfer count; adds p95 latency.
  - Pessimistic `SELECT FOR UPDATE` — holds row lock for the duration of the saga step; if account-service slows, locks pile up.

### ADR-005: AML Threshold Detection — Synchronous In-tx Check vs Async Stream
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Synchronous in-process check during transfer commit; `AmlThresholdBreached` event emitted via outbox (async) to `compliance-service`. Check is non-blocking to the customer response.
- **Rationale:** Accumulator update is already in-transaction (ADR-004); the `cumulative_amount >= 2_000_000 AND aml_threshold_breached = false` check is essentially free. Guarantees exactly-once breach detection per customer per day (the boolean flips inside the same transaction). Outbox keeps customer-facing latency unchanged (US-009 AC2: "flag is advisory; customer experience unaffected").
- **Rejected options:**
  - Async Kafka Streams windowed aggregation over `TransferCompleted` — re-aggregates data already maintained; eventual consistency risks duplicate alerts.
  - Nightly batch job — violates 30s AC for AML notification (US-009 AC1).
  - Synchronous call to `compliance-service` before responding — adds latency; couples customer SLA to compliance-service availability.

### ADR-006: Daily Limit Reset Timezone (OQ-001 assumption)
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Status:** ⏳ **Assumption pending SME confirmation**
- **Decision:** Daily accumulator uses Asia/Bangkok civil date (UTC+7). `DailyTransferAccumulator.accumulation_date` stores the Bangkok DATE; system internal timestamps remain UTC. Reset occurs at 17:00 UTC = 00:00 Bangkok.
- **Rationale:** BA OQ-001 needs confirmation; reasonable default chosen because (a) customers are Thai retail, (b) BoT regulatory reporting uses Bangkok calendar day, (c) AML threshold definition naturally aligns with the same boundary. Single config flag `transfer-service.daily-reset-zone`. **Not blocking** for backend dev of US-001/US-003.
- **Rejected options:**
  - Reset at midnight UTC — customers transacting 00:00–07:00 Bangkok see previous-day total carry over confusingly.
  - Per-customer configurable timezone — over-engineering for intra-bank-Thailand v1.

### ADR-007: AML Scope (OQ-005 assumption)
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Status:** ⏳ **Assumption pending SME confirmation**
- **Decision:** AML 2,000,000 THB threshold applies to OUTBOUND successful transfers per customer per Bangkok civil day (sum of debits from any account owned by the customer). Inbound credits and cash transactions are out of scope. Flag is advisory (does not block); `compliance-service` handles human review.
- **Rationale:** BA OQ-005 needs SME confirmation; defaults based on (a) US-009 AC explicitly says "outbound transfers", (b) AML risk focus is on funds leaving customer's control, (c) inbound credits to a customer are typically the originator's bank's responsibility. Customer-level aggregation prevents splitting evasion. **Not blocking** for backend dev of US-001/US-003.
- **Rejected options:**
  - Apply to inbound + outbound combined — exceeds BA AC without SME confirmation.
  - Per-account accumulator only — allows splitting evasion across owned accounts.
  - Block transactions at threshold (hard limit) — US-009 AC2 explicitly says advisory, not block.

### ADR-008: Payee Management Bounded Context — Separate payee-service vs Merge into account-service
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Separate `payee-service` in the Payments bounded context (sibling of `transfer-service`).
- **Rationale:** `account-service` owns the global system-of-record for accounts; payees are per-customer saved lists with a different aggregate lifecycle. Different scaling profile: `account-service` sits on the synchronous critical path and must be optimized; payee CRUD is low-TPS. Lookup endpoint (US-010) is proxied through `payee-service` which calls `account-service` internally.
- **Rejected options:**
  - Merge payee CRUD into account-service — mixes aggregates; adds payee CRUD load to the critical-path service.
  - Put payee CRUD inside transfer-service — breaks SRP; bloats the saga service.

### ADR-009: Service Mesh — None in v1
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** No dedicated service mesh in v1. Use Kubernetes Service DNS for discovery + Spring Cloud Gateway at the edge + Resilience4j inside each service for circuit-breaker / retry / bulkhead. mTLS at K8s ingress + namespace-level NetworkPolicy.
- **Rationale:** Service mesh adds significant operational complexity (control plane, sidecar overhead, debugging sidecar issues) not justified at the current service count (9). Resilience4j gives type-safe fallback methods, more debuggable than Envoy filter chains. Defer mesh adoption to >20 services or cross-language services.
- **Rejected options:**
  - Istio with full sidecar mesh from day one — disproportionate ops complexity; ~15% hosting cost overhead.
  - Linkerd — same ops argument; smaller ecosystem; less in-house experience.

### ADR-010: Schema Registry — Apicurio vs Confluent
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Apicurio Registry (Apache 2.0) with Avro schemas for Kafka events. Compatibility mode BACKWARD on producer topics.
- **Rationale:** Apicurio is fully open source (no Confluent Community License restrictions — important for bank vendor-neutrality). Supports the Confluent-compatible REST API so Spring Kafka clients work unchanged. Avro chosen over Protobuf for stronger schema-evolution rules natively and team experience.
- **Rejected options:**
  - Confluent Schema Registry — Confluent Community License flagged by legal review; lock-in to Confluent Platform.
  - Protobuf with no registry — no central evolution check; producers/consumers can drift.
  - JSON Schema for events — larger payload, no native binary encoding, weaker evolution enforcement.

### ADR-011: Concurrency Control on Account Balance (US-007 double-debit prevention)
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Optimistic locking via JPA `@Version` on `accounts.version` column; conflict triggers re-read + retry up to 3 times in `account-service`; if all retries fail, return `CONCURRENT_CONFLICT` to `transfer-service` which surfaces as `INSUFFICIENT_FUNDS` to caller after fresh balance re-read.
- **Rationale:** Correct for low-contention accounts. Avoids the deadlock risk of pessimistic locking across debit/credit pairs (classic cross-account deadlock). Retry budget of 3 with jitter caps tail latency. PostgreSQL row-level isolation guarantees `@Version` CAS is atomic.
- **Rejected options:**
  - `SELECT FOR UPDATE` pessimistic — deadlock risk in cross-account scenarios.
  - Single-threaded actor per account (Akka) — introduces a new programming model.
  - Application-level distributed lock (Redis Redlock) — operationally complex; Redlock correctness debates; PostgreSQL MVCC + `@Version` is simpler and sufficient.

### ADR-012: Audit Log Immutability Enforcement
- **Phase:** Planning (S3)
- **Author agent:** banking-solution-architect
- **Decision:** Two-layer enforcement: (1) `audit-service` PostgreSQL role granted INSERT only, no UPDATE/DELETE GRANTs on `audit_log`; (2) row-level append-only trigger that raises exception on UPDATE/DELETE attempts. Partition by `created_at` date for cost-efficient 7-year retention with cold-tier archival.
- **Rationale:** Defense in depth: even if an application bug or SQL injection tries to modify the audit log, the DB role lacks the privilege. Triggers catch any direct connection. Date partitioning lets us move partitions older than 90 days to slower storage. 7-year retention is BoT mandatory.
- **Rejected options:**
  - Application-only immutability (no DB enforcement) — code regression or malicious insider could update records.
  - Blockchain / hash-chained audit log — operationally complex, no regulatory requirement, no proven benefit over append-only RDBMS + signed off-site backups.

### ADR-013: Error Code Taxonomy + Idempotency-Key Hashing Strategy
- **Phase:** Design (S4)
- **Author agent:** banking-tech-lead
- **Decision:** `key_hash = SHA-256(raw_key)`; composite PK `(key_hash, owner_customer_id)`; `request_checksum = SHA-256(canonical_json(request_body))`. Error code taxonomy frozen at the OpenAPI `ProblemDetail.code` enum: `INSUFFICIENT_FUNDS, TRANSACTION_LIMIT_EXCEEDED, DAILY_LIMIT_EXCEEDED, ACCOUNT_FROZEN, SOURCE_ACCOUNT_FROZEN, ACCOUNT_INACTIVE, SOURCE_ACCOUNT_INACTIVE, IDEMPOTENCY_KEY_CONFLICT, MEMO_TOO_LONG, PAYEE_NOT_FOUND, TRANSFER_NOT_FOUND, CONCURRENT_CONFLICT, COMPENSATION_TRIGGERED, DEPENDENCY_UNAVAILABLE, RATE_LIMITED, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR, VALIDATION_ERROR`. Adding a new code requires a new ADR.
- **Rationale:** Builds on Architect's ADR-002 (storage shape). Raw key never on disk → operator cannot replay against another customer. Per-customer scope prevents cross-customer collisions. Canonical-JSON serializer lives in `common-libs/idempotency-lib` to prevent drift across services.
- **Rejected options:**
  - Raw key as PK with global uniqueness — operator could rainbow-table; cross-customer collision risk.
  - HMAC-SHA-256(key, server-secret) — adds key-rotation complexity for a value (UUID v4, 122 bits) that is not a credential.

### ADR-014: Outbox Relay Poll Cadence and Dispatch Semantics
- **Phase:** Design (S4)
- **Author agent:** banking-tech-lead
- **Decision:** Scheduled poll every 500ms (`@Scheduled(fixedDelay=500)` on a dedicated single-thread executor per pod); batch=100 ordered by `created_at ASC`; `SELECT ... FOR UPDATE SKIP LOCKED` for horizontal scaling; Kafka `acks=all`; UPDATE `dispatched=true, dispatched_at=now()` on success; increment `attempt_count` + store `last_error` on failure; hard ceiling at `attempt_count >= 10` → ERROR log + PagerDuty alert (no auto-skip); hourly purge of `dispatched=true AND dispatched_at < now() - interval '7 days'`. Consumers MUST dedupe on `event_id`.
- **Rationale:** Builds on Architect's ADR-003. Simple, no new infrastructure. SKIP LOCKED makes horizontal scaling free. 500ms cadence keeps customer-perceived event latency low (notification-service end-to-end well under 60s SLA).
- **Rejected options:**
  - PostgreSQL LISTEN/NOTIFY trigger on outbox INSERT — lower-latency but adds long-lived DB connections per pod and a fallback path when notifications are dropped — over-engineering for the SLA.

### ADR-015: STRIDE Threat Model for POST /api/v1/transfers + GET /api/v1/transfers/{id}
- **Phase:** Design (S4)
- **Author agent:** banking-tech-lead
- **Decision:** Threat model passed. No new mitigations required beyond what is already in the OpenAPI contract + architect ADRs. Used as a checklist by security-agent at S7.
- **STRIDE coverage:**
  - **Spoofing** — bearerAuth RS256 JWT validated at gateway + service; `sourceAccountId` cross-checked against JWT subject's ownership.
  - **Tampering** — TLS 1.2+ end-to-end; Idempotency-Key + payload checksum detect altered replay (409); server never trusts client-supplied `transferId`, `referenceNumber`, `completedAt`, `correlationId`.
  - **Repudiation** — immutable audit-service record per BoT (ADR-012); `initiator_user_id`, `correlation_id`, `ip_address` (hashed), `user_agent`, `channel`, `limit_snapshot_*` persisted; outbox guarantees survival.
  - **Information Disclosure** — Problem-Detail `detail` is SAFE-for-display only; 500 responses never include exception messages; account numbers masked to last 4; ip_address SHA-256-hashed.
  - **Denial of Service** — Gateway rate-limit 60 req/min per customer; `transfer_idempotency` hourly TTL purge; Resilience4j bulkhead 200 concurrent; account-service circuit breaker prevents cascade.
  - **Elevation of Privilege** — JWT scopes enforced (`transfers:write`, `transfers:read`); service double-checks ownership of `sourceAccountId`; `compensation_transfer_id`, `saga_id` are server-assigned only.

### ADR-016: Money Serialization on the Wire — Decimal String, Not JSON Number
- **Phase:** Design (S4)
- **Author agent:** banking-tech-lead
- **Decision:** `amount` is a JSON string matching `^[0-9]+(\.[0-9]{1,4})?$`. Server parsing: `new BigDecimal(amount).setScale(4, RoundingMode.UNNECESSARY)` (throws if client sent more than 4 dp). Server serialization always `setScale(4)`. Frontend MUST treat the string as opaque and use a decimal library (e.g., `decimal.js`).
- **Rationale:** JSON numbers are IEEE 754 doubles in most clients (JavaScript especially). DB stores `NUMERIC(19,4)` and Avro uses logical-decimal — the HTTP boundary must match to avoid binary-float artifacts on large amounts.
- **Rejected options:**
  - JSON number — IEEE 754 precision loss on JS clients.
  - Object `{"unscaled": 15000000, "scale": 4}` — more verbose; harder for humans to debug.

---

## ADR Index

| ID | Phase | Title | Status |
|---|---|---|---|
| ADR-001 | Planning (S3) | Saga coordination: orchestration | ✅ accepted |
| ADR-002 | Planning (S3) | Idempotency storage: per-service RDBMS table | ✅ accepted |
| ADR-003 | Planning (S3) | Event publishing: transactional outbox | ✅ accepted |
| ADR-004 | Planning (S3) | Daily accumulator: RDBMS optimistic lock | ✅ accepted |
| ADR-005 | Planning (S3) | AML detection: synchronous in-tx check | ✅ accepted |
| ADR-006 | Planning (S3) | Daily limit timezone = Asia/Bangkok | ⏳ assumption (OQ-001) |
| ADR-007 | Planning (S3) | AML scope = outbound aggregation per customer | ⏳ assumption (OQ-005) |
| ADR-008 | Planning (S3) | Separate payee-service bounded context | ✅ accepted |
| ADR-009 | Planning (S3) | No service mesh in v1 | ✅ accepted |
| ADR-010 | Planning (S3) | Apicurio Schema Registry + Avro | ✅ accepted |
| ADR-011 | Planning (S3) | Optimistic locking on account balance | ✅ accepted |
| ADR-012 | Planning (S3) | Audit log immutability (role + trigger) | ✅ accepted |
| ADR-013 | Design (S4) | Error code taxonomy + Idempotency-Key hashing | ✅ accepted |
| ADR-014 | Design (S4) | Outbox relay poll cadence (500ms / batch 100) | ✅ accepted |
| ADR-015 | Design (S4) | STRIDE threat model for transfer endpoints | ✅ accepted |
| ADR-016 | Design (S4) | Money as decimal string on the wire | ✅ accepted |

**Total: 16 ADRs (12 from Architect + 4 from Tech Lead). 2 are assumptions pending SME confirmation.**

---

## Assumptions Pending Confirmation

| ID | Assumption | Origin | Owner | Status | Blocking? |
|---|---|---|---|---|---|
| ADR-006 | Daily limit reset timezone = Asia/Bangkok (covers OQ-001) | Architect (S3) | Compliance SME + BA | ⏳ Pending confirmation | Blocks production go-live; **not** blocking US-001/US-003 dev |
| ADR-007 | AML 2M THB threshold scope = outbound only, per-customer aggregation (covers OQ-005) | Architect (S3) | Compliance SME | ⏳ Pending confirmation | Blocks production go-live; **not** blocking US-001/US-003 dev |

Both assumptions are isolated to (a) one config flag for ADR-006, (b) a view-level aggregation change for ADR-007 — neither requires schema migration if the SME later changes the decision.

---

## Decisions Made by Backend-Dev (S5) — Implementation-Time

These are not ADRs but pragmatic implementation choices recorded in the S5 artifact for transparency:

| Choice | File / Scope | Reason | Carry-forward action |
|---|---|---|---|
| `Transfer.referenceNumber` changed from `final` to non-final | `backend/transfer-service/src/main/java/com/bank/transfer/domain/model/Transfer.java` | Pre-existing compilation bug: `assignReferenceNumber()` mutated a `final` field. Removed `final`; preserved blank-then-assigned design intent with comment. | Reviewer S6 comment (`severity=minor`, `rule: best-practice: mutable-aggregate-field`) recommends moving derivation into `Transfer.create()` to restore immutability. |
| POM version overrides: `junit-jupiter.version=5.10.5` + Surefire `3.5.4` | `backend/transfer-service/pom.xml` | Sandbox Maven cache gap — `5.10.3` not present due to corporate mirror; `5.10.5` resolves `junit-platform-launcher:1.10.5` which is cached. | Revert when environment has full Maven Central access. Reviewer S6 nit recommends gating behind `-Doffline-cache` profile. |
| `SecurityConfig` permits all requests; `oauth2ResourceServer(jwt)` commented out | `backend/transfer-service/src/main/java/com/bank/transfer/infrastructure/config/SecurityConfig.java` | JWT RS256 validation requires live `identity-service` JWKS endpoint, not available in v1. `@PreAuthorize` remains as method-level guard. | Must be flipped on before US-006/prod deploy. Security S7 ITEM-2 (CRITICAL-FOR-DEPLOY). |
| `AccountClientStub` returning canned `ACTIVE` 10M THB balance | `backend/transfer-service/src/main/java/com/bank/transfer/infrastructure/client/AccountClientStub.java` | Real Feign/WebClient adapter deferred to US-006. | Security S7 ITEM-1 (CRITICAL-FOR-DEPLOY): add `@Profile('!prod & !staging')` + `@ConditionalOnProperty` + startup-failure bean. |
| `STUB_CUSTOMER_ID = 00000000-0000-0000-0000-000000000001` hardcoded as principal | `backend/transfer-service/src/main/java/com/bank/transfer/interfaces/rest/TransferController.java` line 58 | No JWT decoder available in v1. | Security S7 ITEM-3 (CRITICAL-FOR-DEPLOY): delete constant, inject `@AuthenticationPrincipal Jwt`. |
| Saga executes synchronously within the use-case transaction boundary; returns COMPLETED or FAILED | `CreateTransferUseCase` + `TransferSaga` | Single-write scope in v1; no compensation path exercised. | Full saga compensation exercise in US-008. |
| Outbox poller is a stub (writes rows but no Kafka publisher) | `OutboxEventPublisher` | Kafka publisher infrastructure deferred to US-007. | Relay wiring in US-007. Security S7 ITEM-6: populate headers with `{correlationId, traceparent, eventId}` before relay ships. |

---

## Links

- [Timeline of handoffs](timeline.md)
- [Dashboard](dashboard.md)
- [Open issues](open-issues.md)
- Architect ADRs full text: [S3 JSON](../artifacts/S3-solution-architect-money-transfer.json) `payload.decisions[]`
- Tech-Lead ADRs full text: [S4 JSON](../artifacts/S4-tech-lead-money-transfer.json) `payload.adrs[*].content`
