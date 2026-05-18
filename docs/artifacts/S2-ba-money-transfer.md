# S2 — Discovery (BA) — Money Transfer

> Summary of [`S2-ba-money-transfer.json`](S2-ba-money-transfer.json). The JSON remains the source of truth for envelope validation; this file makes the payload human-scannable.

## Envelope

| Field | Value |
|---|---|
| Artifact ID | `a3f7c2e1-84b0-4d9f-b6e3-2c51f0d8a749` |
| From | `banking-ba` |
| To | `banking-solution-architect` |
| Phase | `DISCOVERY` |
| Feature | `money-transfer` |
| Timestamp | `2026-05-18T09:00:00Z` |
| Iteration | 1 |
| Quality Gate | Passed |
| Previous artifact | `null` (first in chain) |

## TL;DR

BA decomposed the Money Transfer v1 requirement (intra-bank, THB, retail internet banking) into **11 user stories** (all MUST priority) covering happy path, all rejection paths, idempotency, concurrency, saga compensation, AML flagging, payee management, and async notifications. Delivered **3 process flows**, **6 data entities** with PII annotations, full NFR + compliance section (PDPA / BoT / AML / PCI-DSS / GDPR), 13 explicit out-of-scope items, and 8 open questions that need SME confirmation before design. All 7 hard constraints are traced to at least one acceptance criterion.

## Key Deliverables

### User Stories

| ID | Title | Priority | AC count |
|---|---|---|---|
| US-001 | Initiate intra-bank money transfer (happy path) | MUST | 4 |
| US-002 | Reject transfer due to insufficient balance | MUST | 3 |
| US-003 | Prevent duplicate transfer via Idempotency-Key | MUST | 4 |
| US-004 | Enforce per-transaction transfer limit | MUST | 4 |
| US-005 | Enforce daily cumulative transfer limit | MUST | 4 |
| US-006 | Reject transfer to frozen or inactive account | MUST | 4 |
| US-007 | Prevent double-debit under concurrent transfer requests | MUST | 3 |
| US-008 | Compensate debit when credit step fails (Saga rollback) | MUST | 4 |
| US-009 | Flag AML threshold breach for compliance review | MUST | 4 |
| US-010 | Add and look up payee account before transfer | MUST | 5 |
| US-011 | Send async notifications after transfer outcome | MUST | 4 |

### Process Flows

- **Happy Path Intra-Bank Transfer** — 17 steps + 8 alt paths (A1–A8)
- **Saga Compensation Flow (Debit Succeeded, Credit Failed)** — 11 steps + 3 alt paths (B1–B3)
- **Payee Add and Lookup Flow** — 10 steps + 3 alt paths (C1–C3)

### Data Entities

| Entity | PII? | Key fields summary |
|---|---|---|
| Transfer | Yes | `transfer_id`, `reference_number`, source/destination accounts, amount, currency, status enum (6 states), `idempotency_key`, `saga_id`, `correlation_id` |
| Account | Yes | `account_id`, `account_number` (masked), `customer_id`, status enum (4 states), `available_balance`, `ledger_balance`, `per_transaction_limit`, `daily_limit`, `customer_tier` |
| Payee | Yes | `payee_id`, `owner_customer_id`, `account_number` (encrypted), `masked_name`, `nickname`, `is_active` |
| IdempotencyKey | No | `key_hash` (SHA-256), `transfer_id`, `request_checksum`, `cached_response_body`, `result_status`, `expires_at` (24h TTL) |
| AuditLog | Yes | `audit_id`, `event_type`, `actor_user_id`, masked account IDs, `result` enum, `correlation_id`, limit snapshots, `partition_key` |
| DailyTransferAccumulator | No | `account_id`, `accumulation_date` (Bangkok TZ), `cumulative_amount`, `aml_threshold_breached`, optimistic-lock `version` |

### Non-Functional Requirements

| Category | Value |
|---|---|
| Performance | p95 < 1000ms end-to-end, p99 < 3000ms, internal p95 < 300ms, payee lookup p95 < 500ms, notification within 60s |
| Availability | transfer + account 99.95% / 30d, notification 99.9%, gateway 99.99% |
| Scalability | Sustained 100 TPS, burst 500 TPS for 5 min, K8s HPA, Kafka RF=3 |
| Security | OAuth2/OIDC RS256 JWT (15min TTL), TLS 1.2+, OWASP Top 10, Idempotency-Key mandatory, AES-256-GCM at rest, Vault secrets, 60 req/min rate limit, no PAN |
| Resilience | RTO <= 15min, RPO <= 1min, Resilience4j circuit breaker (50% / 10s), 3 retries exp backoff, durable saga state |
| Observability | OTel + correlation_id, structured JSON logs, Prometheus metrics, Grafana, alert on err>1% / p99>3s / compensation>0 |
| Data Retention | Transfers + audit 7y (BoT), idempotency 24h TTL, accumulator 90d rolling, PDPA schedule with legal-hold override |

### Compliance

- **PDPA (Thailand)** — data minimization in logs, mask account numbers, hash IP, data-subject rights with legal-hold override, consent management, DPA registration
- **Bank of Thailand IT Risk Guidelines** — mandatory audit logging, 7-year immutable retention, segregation of duties, 1-hour incident reporting, BCP with RTO/RPO
- **AML (B.E. 2542)** — flag (not block) cumulative daily outbound >= 2,000,000 THB, AMLO availability, 7-year retention
- **PCI-DSS** — scoped OUT (no card PAN data); re-evaluate if FX/card features added later
- **GDPR** — applies to EU-resident customers, data residency in Thailand, no cross-border transfer without SCCs

### Out of Scope

- Inter-bank transfers (PromptPay, BAHTNET, SWIFT, domestic ACH)
- Cross-currency (FX) transfers — THB only in v1
- Scheduled transfers (future date / recurring)
- Bulk / batch transfer (CSV upload)
- Customer-initiated refund / reversal (manual Ops only in v1)
- Card-to-account transfers
- E-wallet integration
- ML-based fraud scoring (v2)
- International remittance
- PromptPay ID lookup (phone / national ID proxy)
- Multi-currency accounts
- Transfer approval workflows (dual auth for corporate — v2+)
- Beneficiary bank validation beyond intra-bank registry

### Open Questions

| ID | Summary | Owner | Blocking? |
|---|---|---|---|
| OQ-001 | Daily-limit reset timezone (UTC vs UTC+7) — affects DB schema | BA + Tech Lead | Pre-DB-design |
| OQ-002 | Who manages customer-tier assignment & limit overrides? Admin API in v1? | BA + Product | Pre-API-design |
| OQ-003 | Self-transfer (own→own) UX flow — skip payee lookup / masked-name? | BA + UX | Pre-FE-design |
| OQ-004 | Notification fallback priority + "delivered if 1-of-3 vs 2-of-3" sign-off | BA + Product | Pre-go-live |
| OQ-005 | AML threshold scope (outbound vs inbound vs combined; electronic intra-bank?) | Compliance SME | Pre-impl of US-009 |
| OQ-006 | Customer-facing SLA for COMPENSATION_FAILED manual resolution | BA + Ops + Legal | Pre-runbook |
| OQ-007 | Idempotency-Key generation (client vs server, UUID v4?) | Tech Lead + FE | Pre-FE-impl |
| OQ-008 | Masked-payee-name sufficiency (collision risk with common names) | UX + BA | Pre-FE-impl |

### Hard Constraint Traceability

| # | Hard Constraint | Covered by |
|---|---|---|
| 1 | Atomic debit + credit with HTTP 200 + reference number (p95 < 1s) | US-001 AC1, AC2 |
| 2 | Idempotency via `Idempotency-Key` (no double-debit on retry) | US-003 AC1–AC4 |
| 3 | Per-transaction limit enforcement | US-004 AC1–AC4 |
| 4 | Daily cumulative limit enforcement | US-005 AC1–AC4 |
| 5 | Reject frozen / inactive source or destination | US-006 AC1–AC4 |
| 6 | Concurrent-request protection (no negative balance, no double-debit) | US-007 AC1–AC3, US-002 fallback |
| 7 | Saga compensation when credit step fails | US-008 AC1–AC4 |

Bonus (not required but delivered): US-009 AML flag, US-010 payee management, US-011 async notification fan-out.

## Quality Gate Check (Player validation)

- [x] All 7 hard constraints traced to at least one AC
- [x] All MUST stories use Given/When/Then format with measurable thresholds
- [x] PII flagged per entity with handling notes (encryption, masking, retention)
- [x] NFR section covers all 8 categories (perf / avail / scale / sec / resilience / observability / retention / compliance)
- [x] Compliance covers PDPA + BoT + AML + PCI-DSS scoping + GDPR
- [x] Out-of-scope explicit (13 items) to prevent scope creep
- [x] Open questions surfaced (8) with owners and blocking nature

## Open Items

8 open questions (OQ-001 to OQ-008) listed above need SME or stakeholder sign-off. OQ-001 (timezone) and OQ-005 (AML scope) carried forward into S3 as ADR-006 and ADR-007 assumptions with explicit "confirm before go-live" flag.

## Links

- **Source JSON:** [S2-ba-money-transfer.json](S2-ba-money-transfer.json)
- **Previous artifact:** First in chain
- **Next artifact:** [S3-solution-architect-money-transfer.md](S3-solution-architect-money-transfer.md)
- **Communications timeline:** [../agents-comms/timeline.md](../agents-comms/timeline.md)
