# Risk-Based Test Prioritization — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Phase:** QA P1 (shift-left)
> **Input:** PM risk-register.md + BA-001
> **Purpose:** Map test cases to PM risks so highest-risk areas are tested first in QA P2

---

## Overview

การจัดลำดับความสำคัญ test cases ตาม risk score จาก PM risk register ช่วยให้ทีมโฟกัสกับการ automate test ที่สำคัญที่สุดก่อน แทนที่จะทำตามลำดับ story — ป้องกัน demo failure และ compliance incident ได้ดีกว่า

---

## Risk-to-Test-Case Mapping

### RISK-002 — Demo Timeline Tightness (Score: 15, CRITICAL)

**Risk:** 10-day sprint; any delay in parallel tracks or review iterations > 2 rounds can miss the demo.
**QA Impact:** P0 test cases must be identified and automation-ready before QA P2 starts (Day 9). Failing to automate P0 first = no QA sign-off = no demo.
**Mitigation via testing:**

| Test Case ID | Title | Priority | Rationale |
|---|---|---|---|
| TC-INT-001 | Ranked accounts returned 200 | P0 | Core happy path — demo breaks without this |
| TC-INT-009 | Audit on cache hit | P0 | Demo exit criterion (BoT requirement shown live) |
| TC-INT-010 | Audit on cache miss | P0 | Same BoT criterion — both paths must emit audit |
| TC-E2E-001 | Rendered dashboard with labels | P0 | Visible demo screen — stakeholders will see this |
| TC-PERF-001 | Warm cache p95 < 500ms | P0 | Demo claim "feels instant" — cannot demo without SLA evidence |
| TC-A11Y-001 | Lighthouse score ≥ 90 | P1 | Demo exit criterion per sprint-goal.md; defer to P1 if MUST stories slip |

**Automation order for P2:** TC-INT-001 → TC-INT-009 → TC-INT-010 → TC-E2E-001 → TC-PERF-001 → remainder P0 → P1 → P2

**Demo readiness top 3 (any of these failing = demo not ready):**
1. TC-INT-001 — core ranked list API returns correct data
2. TC-INT-009 + TC-INT-010 — audit events emitted (BoT compliance evidence in Grafana)
3. TC-PERF-001 — warm cache p95 < 500ms on staging (demo claim validation)

---

### RISK-003 — PDPA Consent Reuse May Not Cover Balance Inquiry (Score: 10, HIGH)

**Risk:** Consent from money-transfer may not extend to "view own account balance information" purpose. If banking-security flags this at D9 (instead of D2-D3), it becomes a demo blocker.
**QA Impact:** IDOR, audit, and data-minimization test cases are all P0. QA P2 must run these before security gate to catch issues early.
**Mitigation via testing:**

| Test Case ID | Title | Priority | Compliance Link |
|---|---|---|---|
| TC-SEC-001 | IDOR: Customer A cannot see Customer B data | P0 | OWASP A01; PDPA data subject isolation |
| TC-SEC-004 | Full account number never in API JSON | P0 | PDPA data minimization (BR-007) |
| TC-SEC-005 | Balance values absent from logs | P0 | PDPA + BoT (no sensitive data in non-encrypted logs) |
| TC-SEC-008 | PDPA consent scope covers balance-inquiry purpose | P1 | RISK-003 direct mitigation — depends on security consent-registry API |
| TC-INT-009 | Audit event has `purpose=balance-inquiry` and `actorId` | P0 | BoT traceability requirement; `purpose` field enables consent tracing |
| TC-INT-010 | Audit event emitted on cache miss | P0 | Audit never suppressed — BoT hard gate |
| TC-INT-018 | FORBIDDEN audit event on IDOR attempt | P0 | PDPA breach detection audit trail |
| TC-E2E-006 | Full account number absent from DOM and network tab | P0 | PDPA masking in all surfaces (including browser DevTools) |
| TC-SEC-007 | Redis cache payload is encrypted (not plaintext) | P1 | PDPA + BoT: PII encrypted at rest |

**Ordering rationale:** IDOR and masking tests are P0 because a PDPA violation found in staging is recoverable; one found in production after demo = legal escalation. Run these before performance tests.

**Gap flagged to orchestrator:**
- TC-SEC-008 depends on banking-security confirming consent-registry API schema. Until confirmed, test cannot be automated. **Recommend banking-player invoke banking-security for early consent-coverage review immediately (D2-D3).**

---

### RISK-001 — FX Rate Source Undecided (Score: 16 → MITIGATED)

**Status:** MITIGATED by scope cut (US-BC-004 deferred). No FX test cases in this sprint.
**Residual test:** TC-INT-017 (`should_not_include_fx_conversion_in_response`) — verifies the deferred feature boundary is enforced and no FX fields accidentally appear in the response. Priority: P1.

---

### RISK-004 — Shift-Left Parallel Tracks May Not Fire (Score: 12, HIGH)

**Risk:** Player sequentializes SA → Designer → QA instead of batching them.
**QA Impact:** This QA P1 artifact IS the mitigation — it is being produced in parallel with SA and Designer. No additional test cases needed.
**Evidence of mitigation:** This `handoff-qa-p1-001.json` is emitted simultaneously with SA and Designer P1 artifacts.

---

### RISK-005 — p95 < 500ms Cold Cache May Be Infeasible (Score: 9, MEDIUM)

**Risk:** AccountClient latency for N=10 accounts in a single batched call may exceed 800ms on staging.
**QA Impact:** Performance test plan is ready (TC-PERF-001 through TC-PERF-005). QA P2 will run these on Day 9 on staging (not localhost). If p95 cold cache > 800ms is detected, loop-back to banking-backend-dev with profiling data.
**Mitigation via testing:**

| Test Case ID | Title | Priority | SLA Gate |
|---|---|---|---|
| TC-PERF-001 | Warm cache p95 < 500ms | P0 | Hard gate — demo claim depends on this |
| TC-PERF-002 | Cold cache p95 < 800ms | P0 | v1 documented graceful degradation target |
| TC-PERF-003 | Cache hit ratio > 70% | P0 | AC-003-E2 observable via Grafana |
| TC-PERF-004 | Single batched AccountClient call | P1 | BR-016; prevents N×load |
| TC-PERF-005 | 50 concurrent VUs without > 0.1% error | P1 | NFR scalability |

**If TC-PERF-002 fails (cold cache > 800ms):**
- Do NOT lower the threshold
- Generate k6 + OTel profiling report
- Loop back to banking-backend-dev: `bugs_found.loop_back_to = "banking-backend-dev"`
- Include `p95_actual_ms` + AccountClient span duration breakdown

**Nightly CI enforcement:** Perf tests must run nightly against staging. A regression > 10% from the baseline triggers an alert — automatic loop-back to backend-dev before next day's work.

---

### RISK-006 — Mobile Performance on Low-End Devices (Score: 6, MEDIUM)

**Risk:** Angular bundle size causes TTI > 3.5s on low-end Android.
**QA Impact:** Lighthouse TTI and LCP measured as part of TC-A11Y-001. If TTI > 3.5s or LCP > 2.5s, flag to banking-frontend-dev.

| Test Case ID | Metric | Gate |
|---|---|---|
| TC-A11Y-001 | Lighthouse TTI | < 3.5s (mobile emulation) |
| TC-A11Y-001 | Lighthouse LCP | < 2.5s (mobile emulation) |

---

## Test Execution Order for QA P2

ลำดับการ automate ที่ recommended เพื่อ maximize demo readiness ภายในเวลาที่จำกัด:

```
Wave 1 — Demo Blockers (P0 security + P0 core API)
├── TC-SEC-001  IDOR guard (403 + FORBIDDEN audit)
├── TC-SEC-004  Masking in API response
├── TC-SEC-005  No balance in logs
├── TC-INT-001  Ranked list happy path
├── TC-INT-002  401 unauthenticated
├── TC-INT-009  Audit on cache hit
└── TC-INT-010  Audit on cache miss

Wave 2 — P0 Banking-Specific Cases
├── TC-INT-004  Ranking determinism tie-break
├── TC-INT-005  Account-type filter (loan excluded)
├── TC-INT-006  Dormant/closed filter + audit emitted
├── TC-INT-007  Empty state + audit accountCount=0
├── TC-INT-011  GET idempotency within TTL
├── TC-INT-012  Redis fail-open
└── TC-E2E-001  E2E rendered dashboard

Wave 3 — Performance (P0)
├── TC-PERF-001  Warm cache p95 < 500ms
├── TC-PERF-002  Cold cache p95 < 800ms
└── TC-PERF-003  Cache hit ratio > 70%

Wave 4 — P1 Cases (after Wave 1-3 green)
├── TC-CONTRACT-001/002  Contract tests (run BEFORE Wave 4 integration)
├── TC-INT-013  Single batched AccountClient call
├── TC-INT-014  Stale snapshot + staleness flag
├── TC-INT-015  Circuit breaker (depends-on-SA-decision)
├── TC-A11Y-001..004  Automated a11y
└── TC-E2E-002..008  Remaining E2E scenarios

Wave 5 — P2 and manual
├── TC-A11Y-005  VoiceOver manual test
└── TC-SEC-008  Consent scope (blocked on security schema)
```

**Key ordering rule:** Contract tests (TC-CONTRACT-001, TC-CONTRACT-002) must pass BEFORE Wave 4 integration tests run. A failing contract = API drift detected early; merging without contract = production incident risk.

---

## SLA Regression Policy

Per `banking-test-automation` SKILL.md and RISK-005 contingency:

| Condition | Action |
|---|---|
| TC-PERF-001 p95 warm > 500ms | Loop back to banking-backend-dev; do not lower gate |
| TC-PERF-002 p95 cold > 800ms | Loop back to banking-backend-dev with profiling data |
| Any perf metric regresses > 10% from baseline on nightly run | Auto-alert; loop back before next sprint day |
| TC-INT-009 or TC-INT-010 fails (audit not emitted) | Hard gate failure — block merge; loop back to banking-backend-dev |
| TC-SEC-001 IDOR test fails | Critical bug; loop back immediately; do not merge |

---

## Summary: Test Cases Mapped to PM Risks

| PM Risk | Directly Covered Test Cases | Priority | Total |
|---|---|---|---|
| RISK-001 (FX — mitigated) | TC-INT-017 | P1 | 1 |
| RISK-002 (timeline) | TC-INT-001, TC-INT-009, TC-INT-010, TC-E2E-001, TC-PERF-001, TC-A11Y-001 | P0/P1 | 6 |
| RISK-003 (PDPA consent) | TC-SEC-001, TC-SEC-004, TC-SEC-005, TC-SEC-007, TC-SEC-008, TC-INT-009, TC-INT-018, TC-E2E-006 | P0/P1 | 8 |
| RISK-005 (perf SLA) | TC-PERF-001, TC-PERF-002, TC-PERF-003, TC-PERF-004, TC-PERF-005 | P0/P1 | 5 |
| RISK-006 (mobile perf) | TC-A11Y-001 (Lighthouse TTI/LCP) | P1 | 1 |
