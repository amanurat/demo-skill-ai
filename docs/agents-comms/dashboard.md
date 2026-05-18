# SDLC Dashboard — Money Transfer Feature

> Snapshot of where every phase stands. Updated after each agent handoff.
> For the chronological event feed see [timeline.md](timeline.md). For ADRs see [decisions-log.md](decisions-log.md). For risks/issues see [open-issues.md](open-issues.md).

**Last updated:** 2026-05-18 (current)
**Active feature:** `money-transfer`
**Active iteration:** 1
**Pacing mode:** autonomous chain (no per-phase pauses)
**In-scope this run:** US-001 (happy-path intra-bank transfer) + US-003 (idempotency)
**Backend target service:** `transfer-service`
**Frontend:** out-of-scope this run (Angular deferred)
**Last Quality Gate:** DevOps → Done (✅ pass; v1 scaffold close-out — see [S9](../artifacts/S9-devops-money-transfer.md))

---

## Phase Progression

| # | Phase | Agent | Status | Gate | Artifact | Notes |
|---|---|---|---|---|---|---|
| S2 | DISCOVERY | banking-ba | ✅ Done | ✅ Pass | [S2](../artifacts/S2-ba-money-transfer.md) | 11 user stories, 43 AC, 6 entities, 8 open questions |
| S3 | PLANNING | banking-solution-architect | ✅ Done | ✅ Pass | [S3](../artifacts/S3-solution-architect-money-transfer.md) | 9 services, 14 events, 12 ADRs, 6 risks |
| S4 | DESIGN | banking-tech-lead | ✅ Done | ✅ Pass | [S4](../artifacts/S4-tech-lead-money-transfer.md) | OpenAPI + 5 Flyway migrations + 3 Avro schemas + 4 ADRs |
| S5 | DEVELOPMENT | banking-backend-dev | ✅ Done | ✅ Pass | [S5](../artifacts/S5-backend-dev-money-transfer.md) | 25 files; 40/40 unit tests; cov 0.85; 6 IT not executed |
| S6 | REVIEW | banking-reviewer | ✅ Done | ✅ Pass | [S6](../artifacts/S6-reviewer-money-transfer.md) | verdict approved; 0 blocker · 5 major · 13 minor · 5 nit |
| S7 | SECURITY | banking-security | ✅ Done | ⚠️ Pass-with-conditions | [S7](../artifacts/S7-security-money-transfer.md) | verdict approved; 0 crit · 0 high · 3 med · 7 low · 2 info |
| S8 | TESTING | banking-qa | ✅ Done | ⚠️ Pass-with-conditions | [S8](../artifacts/S8-qa-money-transfer.md) | 40/40 unit tests pass; money-path cov 95%+; 6 IT compile-clean (Docker-gated); 2 bugs filed back to backend-dev |
| S9 | DEPLOYMENT | banking-devops | ✅ Done | ✅ Pass (v1 scaffold) | [S9](../artifacts/S9-devops-money-transfer.md) | Docker image built + smoke 200 (liveness/readiness/Prometheus); 23 infra files; 7 schema mismatches fixed; staging gated on 5 backend items |

---

## Scope (this run)

- **Stories implemented in code:** US-001 (happy-path), US-003 (idempotency)
- **Stories deferred:** US-002, US-004..US-011 (paths exist in architecture + tech-lead OpenAPI but no impl)
- **Backend only** — no Angular yet
- **Pacing:** autonomous chain (no per-phase pauses)
- **Stub posture in v1 code:** `AccountClientStub` (canned 10M THB ACTIVE), `SecurityConfig` permit-all, `STUB_CUSTOMER_ID` hardcoded, outbox poller without Kafka publisher — all gated for removal before US-006 / staging.

---

## Feedback Loop Status

_(none active — chain has been forward-only across all 8 phases)_

No iteration-2 rework has been triggered. All quality gates passed on iteration 1. The reviewer's 5 majors, the security agent's 3 medium findings, and QA's 2 bug reports (BUG-QA-001, BUG-QA-002) are acceptable-for-v1 with documented gating; they convert into hardening tasks before US-006 lands, not retries of S5.

---

## Quality Gate History

| Gate | Phase Transition | Result | Iteration | Date | Source artifact |
|---|---|---|---|---|---|
| Requirement Completeness | Discovery → Planning | ✅ pass | 1 | 2026-05-18 | S2 |
| Architecture Soundness | Planning → Design | ✅ pass | 1 | 2026-05-18 | S3 |
| Contract Validated | Design → Development | ✅ pass | 1 | 2026-05-18 | S4 |
| Code Health | Development → Review | ✅ pass (cov 0.85; lint/build `not_run` per S6 metrics) | 1 | 2026-05-18 | S5 |
| Best-Practice Compliance | Review → Security | ✅ pass | 1 | 2026-05-18 | S6 |
| Vulnerability Floor | Security → QA | ⚠️ pass-with-conditions (0 crit / 0 high; 1 hard-rule `no_secrets_in_code=false` — low) | 1 | 2026-05-18 | S7 |
| Test Coverage + SLA | QA → DevOps | ✅ pass (40/40 unit; money-path 95%+; 6 IT compile-clean Docker-gated; 2 bugs filed back to backend-dev) | 1 | 2026-05-18 | S8 |
| Deployable + Observable | DevOps → Done | ✅ pass (image built + smoke 200; 23 infra files; 7 schema mismatches fixed; staging gated on 5 backend items) | 1 | 2026-05-18 | S9 |

### Banking Hard Gates (from quality-gates.md)

| Hard rule | Status | Source |
|---|---|---|
| No PII / card data in logs | ✅ verified (AccountId masked to last 4) | S7 banking_hard_rules_check |
| All financial endpoints accept `Idempotency-Key` | ✅ enforced (ADR-013) | S5 + S7 |
| Audit trail for state-changing ops | ✅ via outbox row in same `@Transactional` | S6 + S7 |
| No hardcoded secrets | ⚠️ DB_PASSWORD default in YAML (Finding S-04, low) | S7 |
| Reversible migrations | ✅ 5 Flyway migrations, forward-only with compensating-migration policy | S4 |

---

## Next Action

**SDLC complete — see DoD checklist for staging-deploy gating.** All 8 phases (S2..S9) have signed off for the v1 scaffold. The v1 close-out is in [S9](../artifacts/S9-devops-money-transfer.md); the consolidated remaining work is in [open-issues.md](open-issues.md).

Headline blockers for **staging promotion** (US-006 work):
- 5 backend-dev items: JWT activation, STUB_CUSTOMER_ID removal, AccountClientStub @Profile guard, MDC.clear() ServletFilter, Micrometer-tracing + traceparent filter
- 2 QA bug fixes: BUG-QA-001 (idempotency replay of REJECTED), BUG-QA-002 (TTL expiry coverage)
- CI runner upgrade to Docker Desktop ≥ 4.19 (Docker API ≥ 1.44) so the 6 integration tests execute
- Gatling performance baseline at first staging deploy (100 RPS sustained, p95 < 1s)
- Backend-dev to reconcile JPA entity annotations with the 7 migration fixes applied by DevOps in V001/V002/V004

After these land → re-run S5 → S6 → S7 → S8 → S9 (iteration 2) and promote to `banking-staging` via `helm upgrade --atomic`.

---

## Known Open Items

For the consolidated, source-linked list see [open-issues.md](open-issues.md). Headline counts:
- 8 BA open questions (OQ-001..OQ-008) pending SME confirmation
- 6 Architect risks (RISK-001..RISK-006) with mitigations recorded
- 10 Security must-fix-before-staging items (3 CRITICAL-FOR-DEPLOY, 4 BEFORE-PROD, 3 BEFORE-STAGING)
- 5 Reviewer majors carried forward as US-006 hardening
- 7 Backend-dev known limitations carried forward
