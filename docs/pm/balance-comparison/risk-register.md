# Risk Register (RAID Log) — Balance Comparison Dashboard

> **Format:** `Likelihood × Impact = Score` (1-5 each, score = product)
> **Owner-role** = agent or human role responsible for mitigation execution
> **Review cadence:** PM updates at end of each sprint day; escalates score ≥ 12 to user

---

## Top Risks (sorted by score)

### RISK-001 — FX rate source for multi-currency display is undecided

| Field | Value |
|---|---|
| Likelihood | 4 (high — no spec exists today) |
| Impact | 4 (high — blocks US-BC-004 and confuses US-BC-001 if balances mix currencies) |
| Score | **16 — CRITICAL** |
| Trigger | US-BC-004 needs FX conversion; even US-BC-001 must decide ranking rule when currencies differ |
| Mitigation | **Default: drop US-BC-004 to follow-up sprint; rank within same currency; show currency badge on each row.** BA must confirm OPEN-001 with stakeholder by D2 EOD. If decision = "show native currency only" → no rework. If decision = "convert using BoT mid-rate" → SA must add FX gateway to ADR and risk re-scoring needed. |
| Contingency | If stakeholder insists on FX in this sprint → cut US-BC-005 (a11y stretch) to free 2 SP, but PM will flag schedule risk to user |
| Owner-role | `banking-ba` (clarify) → `banking-solution-architect` (design if in scope) |
| Status | OPEN |

---

### RISK-002 — Demo deadline (2026-06-04) tightness — only 10 working days

| Field | Value |
|---|---|
| Likelihood | 3 (medium — shift-left parallel tracks help, but any review iteration > 2 rounds eats slack) |
| Impact | 5 (very high — missed demo damages stakeholder trust + delays follow-up sprints) |
| Score | **15 — CRITICAL** |
| Trigger | (a) BA clarifications stall on OPEN-001..004; (b) Reviewer cycles > 2 iterations; (c) Cold-cache perf misses 500ms SLA |
| Mitigation | (1) PM enforces Day-2 hard gate on BA completeness; (2) Reuse-first policy — `AccountClient` / `AccountInfo` MUST NOT be rebuilt; (3) Pre-commit MUST scope at 7 SP (already done — 5 SP headroom on 12-SP velocity); (4) US-BC-005 is a sacrificial stretch — Player drops it without re-planning if D7 review still in iteration 2 |
| Contingency | If D8 reviewers still in iteration 2 → PM cuts US-BC-005 + reduces US-BC-003 NFR to "p95 < 800ms warm-cache" (documented trade-off) |
| Owner-role | `banking-player` (runtime enforcement) + `banking-pm` (replan trigger) |
| Status | OPEN — actively monitored |

---

### RISK-003 — PDPA consent reuse from Money Transfer may not cover read-only dashboard

| Field | Value |
|---|---|
| Likelihood | 2 (medium-low — existing consent likely covers "view own account information" but must be verified) |
| Impact | 5 (very high — compliance hard-stop per [quality-gates.md](../../architecture/quality-gates.md)) |
| Score | **10 — HIGH** |
| Trigger | Security review (D9) flags consent scope mismatch → blocks demo |
| Mitigation | (1) `banking-ba` includes consent-scope check in story AC (US-BC-001 AC must reference the consent clause covering "balance inquiry"); (2) `banking-security` does early consent-coverage review at D2 (shift-left, not D9) — Player should invoke security early-look on BA artifact, not wait for full SEC gate; (3) Audit event must include `purpose = balance-inquiry` for traceability |
| Contingency | If consent gap found → Legal team engagement (out of agent scope) → escalate to human user immediately |
| Owner-role | `banking-ba` (story AC) + `banking-security` (early review) |
| Status | OPEN |

---

### RISK-004 — Shift-left parallel tracks may not fire on Day 2 (sequential drift)

| Field | Value |
|---|---|
| Likelihood | 3 (medium — easy default for orchestrator is to wait for SA before invoking Designer/QA, defeating shift-left) |
| Impact | 4 (high — losing parallelism likely pushes demo to 06-05 or later) |
| Score | **12 — HIGH** |
| Trigger | Player invokes `banking-solution-architect` first, then waits for SA artifact before starting Designer P1 / QA P1 |
| Mitigation | (1) PM-001 envelope explicitly lists Designer P1 + QA P1 as "fire-with-SA" tracks; (2) Player must invoke 3 agents in **one batch** after BA-001 quality-gate pass — not sequentially; (3) Same rule for DevOps P1 fire-with-FE/BE after TL-001 |
| Contingency | If D3 morning shows only SA running (Designer/QA idle) → PM intervenes, forces fan-out |
| Owner-role | `banking-player` (orchestration discipline) |
| Status | OPEN |

---

### RISK-005 — p95 < 500ms cold-cache may be infeasible with current `AccountClient` latency

| Field | Value |
|---|---|
| Likelihood | 3 (medium — `AccountClient` was tuned for transfer flow's p95 < 300ms internal, but N=10 fan-out behavior is untested) |
| Impact | 3 (medium — fail the NFR but demo can still happen with degraded SLA disclosure) |
| Score | **9 — MEDIUM** |
| Trigger | QA P2 perf test (D9) shows p95 ≥ 500ms even with Redis warm |
| Mitigation | (1) TL must batch the `AccountClient` calls (single batched call, not N round-trips); (2) Redis cache key = `customer:{id}:accounts`, TTL 30s; (3) QA P1 includes perf test plan with realistic 10-account fixture by D3 (shift-left — finds the issue early) |
| Contingency | Document p95 ≤ 800ms as v1 acceptance; track p95 < 500ms as v1.1 follow-up. Demo script discloses honestly. |
| Owner-role | `banking-tech-lead` (design) + `banking-qa` (early measurement) |
| Status | OPEN |

---

### RISK-006 — Mobile perf on low-end devices (sub-3 CPU score Android)

| Field | Value |
|---|---|
| Likelihood | 2 (low-medium — Angular initial bundle for a single dashboard view is manageable) |
| Impact | 3 (medium — affects "mobile-first" claim in stakeholder demo if shown on real device) |
| Score | **6 — MEDIUM** |
| Trigger | Lighthouse mobile run shows TTI > 3.5s or LCP > 2.5s |
| Mitigation | (1) FE Dev uses standalone components + lazy-route loading; (2) No heavy charts (deferred to next sprint anyway); (3) Designer P2 specs limit components to those already in design system |
| Contingency | Demo on mid-range device (Pixel 5 / equivalent) — not low-end — and document low-end as known limitation |
| Owner-role | `banking-frontend-dev` + `banking-designer` |
| Status | OPEN |

---

## Top 3 Surfaced to Stakeholder

> Per PM anti-pattern guidance: surface top 3 only to user; rest in RAID log above.

| Rank | Risk | Score | One-line headline |
|---|---|---|---|
| 1 | RISK-001 | 16 | FX/multi-currency policy undecided — default plan = drop US-BC-004 |
| 2 | RISK-002 | 15 | 10-day demo window is tight; need shift-left discipline + reuse-first |
| 3 | RISK-003 | 10 | PDPA consent reuse must be verified early (not at D9 security gate) |

---

## Assumptions (A in RAID)

| ID | Assumption | If wrong, impact |
|---|---|---|
| ASSUME-001 | Existing OAuth2/JWT flow returns `customer_id` claim that `AccountClient` can use | Need IDS change → +2 SP, schedule risk |
| ASSUME-002 | `AccountClient.listAccountsByCustomer(customerId)` already exists or is trivial to add | Rebuild risk → blows reuse story → +3 SP |
| ASSUME-003 | `audit-service` already exposes event type usable for "balance-inquiry" purpose tagging | Need new event schema → small SA work |
| ASSUME-004 | Redis cluster is shared infra and provisioned for staging | Otherwise DevOps P1 must provision → +1 SP |
| ASSUME-005 | Team velocity from money-transfer sprint (~12 SP) applies to this team | If lower, US-BC-005 stretch drops automatically |

---

## Issues (I in RAID — active blockers, none yet)

_(None at PM intake; will be populated during sprint.)_

---

## Dependencies (D in RAID)

| ID | Depends on | Need-by | Owner |
|---|---|---|---|
| DEP-001 | `account-service` running on staging | D5 (TL handoff) | `banking-devops` (verify) |
| DEP-002 | OAuth2 staging IdP with retail-customer test users | D9 (QA P2 E2E) | `banking-devops` |
| DEP-003 | Audit-service consuming new `BalanceInquiry` event (or reusing existing schema) | D5 (TL) | `banking-solution-architect` (decides) |
| DEP-004 | Grafana folder for `balance-dashboard` | D10 (DevOps P2) | `banking-devops` |

---

## Escalation Triggers (PM → human user)

PM will escalate to the human user immediately if:
- Any risk score increases to ≥ 20 mid-sprint
- RISK-001 stakeholder decision not received by D2 EOD
- RISK-003 consent gap confirmed (legal involvement needed)
- Any quality gate fails 3 iterations (per [workflow.md](../../architecture/workflow.md) retry policy)
