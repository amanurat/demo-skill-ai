# Backlog — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Sprint window:** 2026-05-21 → 2026-06-04 (2 weeks)
> **Owner:** `banking-pm` (handoff to `banking-ba` next)
> **Status:** PRIORITIZED — ready for BA expansion

---

## Epic

### EPIC-BC-001 — Account Balance Comparison Dashboard

| Attribute | Value |
|---|---|
| Persona | Retail banking customer (multi-account holder) |
| Business value | High — เพิ่ม engagement บน mobile app + เป็น base สำหรับ financial insights ในอนาคต |
| Regulatory driver | PDPA (data minimization on balance display) + BoT IT-Risk Guidelines (audit trail of customer data access) |
| Scope mode | Read-only (no transactions) |
| Estimated story points | ~14 SP across 5 stories |
| Sprint capacity reference | 10-13 SP (single 2-week demo sprint) |
| Dependency | Reuses `AccountClient` + `AccountInfo` from `money-transfer` feature — no rebuild |

---

## Prioritization Approach

ใช้ **MoSCoW + value/effort scoring** (1-5 scale per axis; priority = value − effort, ties broken by regulatory / demo-blocker status).

| Priority | Definition |
|---|---|
| **MUST** | ต้องอยู่ใน demo (2026-06-04); ถ้าไม่มี = demo fail |
| **SHOULD** | เพิ่มคุณค่าให้ demo แต่ตัดได้ถ้า capacity ตึง |
| **COULD** | นำเสนอเป็น "next sprint preview" ใน demo |
| **WON'T** | อยู่นอกขอบเขต sprint นี้ชัดเจน |

---

## User Stories (prioritized)

### Demo-critical (MUST — committed to sprint)

| Story ID | Title | Persona need | Value | Effort | MoSCoW | Reuse vs New |
|---|---|---|---|---|---|---|
| **US-BC-001** | List my accounts ranked by balance (desc) | "อยากเห็นบัญชีทั้งหมดเรียงจากเงินมากไปน้อยในหน้าจอเดียว" | 5 | 2 | MUST | Reuse `AccountClient.listAccountsByCustomer()` + `AccountInfo` DTO |
| **US-BC-002** | See per-account details (type, balance, currency, last updated) | "อยากรู้ว่าบัญชีไหนเป็น savings / current และ balance ล่าสุดเมื่อไหร่" | 5 | 2 | MUST | Reuse `AccountInfo` fields; **new** mapping for `accountType` display label + `lastUpdatedAt` |
| **US-BC-003** | Dashboard meets p95 < 500ms for 10 accounts | "อยากให้หน้าเปิดเร็วบนมือถือเครือข่าย 4G" | 4 | 3 | MUST | **New** — Redis cache layer in front of `AccountClient`; new aggregation endpoint in **balance-dashboard-service** (or extension of account-service — SA to decide) |

**MUST total: ~7 SP** (capacity-safe; leaves headroom for review + integration risk)

### Demo-nice-to-have (SHOULD — commit if capacity allows; else push to follow-up)

| Story ID | Title | Persona need | Value | Effort | MoSCoW | Reuse vs New |
|---|---|---|---|---|---|---|
| **US-BC-004** | Show total wealth in customer's home currency (multi-currency aggregation) | "บัญชี USD + THB อยากเห็นยอดรวมในสกุลเดียว" | 4 | 5 | SHOULD | **New** — FX rate source (open decision — see RISK-001); **new** conversion logic |
| **US-BC-005** | Mobile-first responsive layout + a11y AA | "ใช้งานง่ายบนมือถือจอเล็ก, รองรับ screen reader" | 3 | 2 | SHOULD | **New** — Designer P2 deliverable; FE Tailwind/Material breakpoints |

**SHOULD total: ~7 SP** → only US-BC-005 will fit if MUST runs clean. **US-BC-004 (FX) deferred to follow-up sprint by default** because of open FX-source decision.

### Out of sprint (COULD / WON'T — explicit)

| Story ID | Title | Disposition | Reason |
|---|---|---|---|
| US-BC-006 | Filter / search accounts | COULD (next sprint) | ลูกค้า demo target มี ≤ 5 บัญชี — ไม่ critical |
| US-BC-007 | Sparkline chart of balance over time | COULD (next sprint) | ต้อง historical balance store — out of scope of read-only dashboard |
| US-BC-008 | Hide / show specific accounts (customer preference) | COULD (next sprint) | ต้อง user-preference service |
| US-BC-009 | Export to CSV / PDF | WON'T | Reporting platform owns this; out of scope |
| US-BC-010 | Joint-account / power-of-attorney support | WON'T | Compliance scope expansion — separate epic |
| US-BC-011 | Push notification on balance change | WON'T | Notification service work; not a dashboard concern |

---

## Reuse vs New — Explicit Inventory

### REUSE (do not rebuild — Player must enforce)

| Asset | Origin | Used by |
|---|---|---|
| `AccountClient` (Spring Boot REST/Feign client) | `money-transfer` feature | US-BC-001, US-BC-002, US-BC-003 |
| `AccountInfo` DTO (fields: `accountId`, `accountNumber` (masked), `accountType`, `balance`, `currency`, `status`, `lastUpdatedAt`) | `money-transfer` feature | All stories |
| OAuth2/OIDC + JWT auth filter chain | platform | All stories |
| Audit logging interceptor (`audit-service` event emitter) | `money-transfer` feature | All stories — every read recorded per BoT |
| OpenTelemetry tracing setup | platform | All stories |

### NEW (must be designed/built)

| Asset | Owner agent | Notes |
|---|---|---|
| `balance-dashboard-service` (or extension of `account-service` — SA decides ADR) | `banking-solution-architect` | Aggregation endpoint `GET /v1/balance-dashboard` returning ranked list |
| Redis cache key strategy (per-customer, TTL 30s) | `banking-tech-lead` | To meet p95 < 500ms for cold/warm cases |
| Angular `<balance-dashboard>` standalone component | `banking-frontend-dev` | Mobile-first, a11y AA |
| OpenAPI spec for new endpoint | `banking-tech-lead` | Includes pagination contract (even if not used in v1) |
| Test plan covering ranking determinism + multi-currency rendering | `banking-qa` (P1 shift-left) | Starts in parallel with SA |
| Grafana dashboard panel (latency, cache hit ratio, audit-event rate) | `banking-devops` (P2) | Reuses `account-service` dashboard template |

---

## Shift-Left & Parallel Tracks (Player must orchestrate)

Per [docs/architecture/workflow.md](../../architecture/workflow.md), the following tracks run **in parallel** after BA emits user stories:

```
banking-ba
   ├── banking-solution-architect           (main)
   ├── banking-designer (Phase 1 LO-FI)     (parallel — shift-left UX)
   └── banking-qa (Phase 1 test plan)       (parallel — shift-left QA)

banking-tech-lead
   ├── banking-frontend-dev + banking-backend-dev   (main)
   └── banking-devops (Phase 1 CI/CD skeleton)       (parallel — shift-left ops)
```

PM expectation: **Designer P1 + QA P1 must start ≤ 1 day after BA-001 ships**, and **DevOps P1 must start ≤ 1 day after TL-001 ships**. Otherwise demo deadline is at risk (see RISK-004).

---

## Out of Scope (explicit — do not re-open in this sprint)

- Any write/transaction operation on accounts
- Joint accounts / power-of-attorney visibility rules
- Historical balance time-series
- Customer preferences (hide / reorder / favorite)
- Push or email notifications
- CSV / PDF export
- FX live-rate integration to external providers (depends on RISK-001 resolution)
- iOS / Android native app — Angular web only for demo

---

## Open Decisions (for BA to clarify with stakeholder)

| ID | Question | Why it matters | Default if unanswered |
|---|---|---|---|
| OPEN-001 | FX rate source for US-BC-004 — live BoT mid-rate, internal treasury rate, or "display in native currency only"? | Determines if US-BC-004 can ship this sprint or needs a follow-up | Display native currency only (drop US-BC-004 to follow-up sprint) |
| OPEN-002 | "Last updated" — is this the `balance_as_of` timestamp from ledger, or the row `updated_at`? | Affects truthfulness of UI label and cache TTL | Ledger `balance_as_of` (more accurate for customer) |
| OPEN-003 | Should closed / dormant accounts appear (greyed out) or be hidden? | Affects ranking output + audit-trail expectations | Hidden by default; revisit in follow-up |
| OPEN-004 | Are there account types that must NOT appear (e.g., loan, credit card balances)? | Determines filter logic in aggregation service | Show deposit-style accounts only (savings, current, fixed) |

> BA must resolve OPEN-001 to OPEN-004 **before** SA starts ADRs.
