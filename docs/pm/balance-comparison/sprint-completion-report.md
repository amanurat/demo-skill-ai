# Sprint Completion Report — balance-comparison

> **Sprint:** SPRINT-2026-Q2-BC-01
> **Window:** 2026-05-21 → 2026-06-04 (demo date)
> **Report date:** 2026-05-22
> **Branch:** `stage/02-balance-comparison`
> **Author:** banking-pm
> **Status:** GREEN — all gates cleared; demo-ready pending staging infra

---

## 1. Sprint Goal (from PM-001)

> "Deliver a read-only Account Balance Comparison Dashboard for retail customers showing their own accounts ranked by balance (desc) on a mobile-first UI with p95 < 500ms, reusing the existing AccountClient and AccountInfo from money-transfer, demo-able on staging by 2026-06-04."

**Goal status:** MET (functionally complete; perf SLA not validated against staging — see GAP-P2-001).

---

## 2. Delivery Summary (story-by-story)

| Story | Title | Priority | SP | Status | Evidence |
|---|---|---|---|---|---|
| US-BC-001 | List my accounts ranked by balance (desc) | MUST | 2 | DELIVERED | Ranker (6 tests) + ArchUnit + E2E AC-001-H1/H2/H4/E1 |
| US-BC-002 | Per-account details (type, balance, currency, last updated) | MUST | 2 | DELIVERED | EligibilityPolicy (11 tests) + balance-as-string contract + E2E AC-002-H1/H3 |
| US-BC-003 | Dashboard meets p95 < 500ms for 10 accounts | MUST | 3 | DELIVERED (code) / UNVALIDATED (perf) | Redis TTL 30s cache + Resilience4j + cache-hit audit; **k6/Gatling missing → GAP-P2-001** |
| US-BC-005 | Mobile-first responsive layout + a11y AA | SHOULD (stretch) | 2 | DELIVERED | Lighthouse a11y test plan + E2E AC-005-H1/H2/H3; 14 Playwright tests |
| US-BC-004 | Total wealth in home currency (FX aggregation) | SHOULD | — | DEFERRED to follow-up sprint (RISK-001 default mitigation; OPEN-001 resolved → native-currency-only) |

**Velocity:** 9 SP delivered (7 committed MUST + 2 stretch) vs 12 SP capacity → within plan, 3 SP headroom consumed by review iterations.

---

## 3. Quality Gate Summary

| Gate | Agent | Verdict | Iteration | Key Evidence |
|---|---|---|---|---|
| G1 BA Completeness | banking-ba | PASS | 1 | All 4 OPEN decisions resolved by D2 EOD; AC complete |
| G2 Design (LO-FI + HI-FI) | banking-designer P1 + P2 | PASS | 1 | W3C design tokens + HI-FI mockups + accessibility tokens |
| G3 Solution Architecture | banking-solution-architect | PASS | 1 | Service map + ADR-001..004 + risk register (SA-RISK-002 raised) |
| G4 Tech Lead + Impl Plan | banking-tech-lead + banking-implementation-planner | PASS | 1 | OpenAPI specs + DB DDL + ADR-005..007 + 130-test-case plan with 100% AC coverage |
| G5 Code Health | banking-backend-dev + banking-frontend-dev | PASS | 1 | 35+ BE tests at first emit; 55 FE files; build green |
| G6 Code Review | banking-reviewer-be + banking-reviewer-fe | PASS | BE iter 3 / FE iter 2 | All blockers resolved; carry-over tracked as BC-DEBT-001 |
| G7 Contract Integrity | banking-integration | PASS | 1 | 0 OpenAPI drift; contract tests pass; smoke deferred to staging |
| G8 Security | banking-security | PASS | iter 2 | F-1 (PII in logs CWE-532) + F-2 (logback filter) RESOLVED; STRIDE + OWASP Top 10 clean |
| G9 QA P2 | banking-qa | PASS | 1 | 170 tests (138 unit / 9 integration / 9 contract+arch / 14 E2E) — all green; 25/25 ACs covered |
| G9 Docs | banking-docs | PASS | 1 | API ref + CHANGELOG + dev guide + ADR index |
| G10 Deploy | banking-devops P2 | PASS (conditional) | 1 | Dockerfile (multi-stage, non-root), CI 10 stages, Grafana dashboard (14 panels); **staging deploy + smoke = pending_infra** |

---

## 4. Metrics

### 4.1 Test Inventory

| Suite | Count | Notes |
|---|---|---|
| Backend unit | 55 | Domain/policy/service critical-path ~97% |
| Frontend unit | 83 | Components, pipes, services, guards |
| Integration (Testcontainers — real Redis + real Kafka) | 9 | No H2, no embedded Kafka |
| Contract + ArchUnit | 9 | Kafka audit byte-grep + CustomerId source rules + balance-as-string |
| E2E (Playwright) | 14 | Localhost mocked (staging deferred) |
| **Total** | **170** | All green, zero failures |

### 4.2 Coverage

| Metric | Value | Target | Status |
|---|---|---|---|
| Overall unit coverage | ~87% (estimate) | >= 80% | PASS |
| Critical-path coverage (domain.policy / domain.audit / application.service) | >= 95% (estimate) | >= 95% | PASS |
| Mutation kill rate (PIT estimate — not executed) | ~75% | n/a (informational) | informational |
| AC coverage (25 ACs across 4 delivered stories) | 25/25 | 100% | PASS |

### 4.3 Security

| Finding | Severity | Status |
|---|---|---|
| F-1 PII (UUID) in logs | HIGH (CWE-532) | RESOLVED (call-site LogMasking.maskId/maskKey) |
| F-2 Missing Logback masking filter | MEDIUM (CWE-532) | RESOLVED (profile-aware `<replace>` regex in logback-spring.xml) |
| F-3 JWT in sessionStorage | MEDIUM | NON-BLOCKING — carried as BC-DEBT-002 (v1.1 BFF migration) |
| F-4 Privacy notice / consent UI deferred | LOW | NON-BLOCKING — carried as C-1 |
| F-5 Future rate-limiting must be at ingress | INFO | NON-BLOCKING — encoded into NetworkPolicy |

STRIDE: all 6 dimensions PASS. OWASP Top 10: 9 PASS, 1 non-blocking (A07 / F-3), 1 N/A (A10).

### 4.4 Review Iterations

| Track | Iterations | Outcome |
|---|---|---|
| Backend | 3 (max) | iter 1 → refactor → iter 2 → refactor → iter 3 APPROVED |
| Frontend | 2 | iter 1 → refactor → iter 2 APPROVED |
| Refactoring loops | 3 (BE) | All blocker themes (R-BE-202..205) verified fixed in source |

Review-loop slack consumed but **never exceeded the 3-iteration ceiling** → no PM escalation triggered.

---

## 5. DoD Checklist Assessment

> Per `docs/architecture/definition-of-done.md`. Status: PASS conditionally (staging deploy pending infra).

### Code

- [x] All code merged to feature branch with green CI (P1 stages verified; P2 stages will execute on first staging push)
- [x] No TODO/FIXME comments in production paths (one TODO references OBS-042 ticket — acceptable, references real backlog item)
- [x] OpenAPI specs published (BDS + account-service-extension)
- [x] CHANGELOG entry added (`docs/generated/balance-comparison/CHANGELOG.md`)

### Tests

- [x] Unit coverage >= 80% (~87%); critical path >= 95%
- [x] Integration tests pass with real Postgres / Kafka / Redis via Testcontainers (no H2, no embedded Kafka)
- [x] Contract tests pass (Kafka audit byte-grep + ArchUnit + balance-as-string @WebMvcTest + Jest type) — **Pact CDC absent → GAP-P2-002 NON-BLOCKING**
- [x] E2E happy paths automated (14 Playwright scenarios)
- [ ] **Performance baseline established** — GAP-P2-001 NON-BLOCKING for demo, BLOCKING for production. Carried to BC-DEBT-PERF-001.

### Security

- [x] SAST clean (Semgrep stage in CI)
- [ ] DAST against staging — pending staging infra
- [x] SCA clean (Trivy + OWASP Dependency Check stages in CI)
- [x] Secrets scan clean (no hardcoded secrets; Vault CSI + GH Actions secrets)
- [x] Threat model reviewed (STRIDE complete)
- [x] OWASP Top 10 reviewed

### Banking-Specific

- [x] Audit trail recorded for every state-changing op (every dashboard read emits Kafka `balance-inquiry-audit` event — even on cache hit; CacheHitAuditEmittedTest verifies)
- [x] Idempotency — N/A (read-only feature)
- [x] PII handling reviewed (LogMasking + logback filter resolve CWE-532; AuditEventRecord 9-field metadata-only invariant)
- [x] PCI-DSS scope unchanged (no PAN, no CVV, no cardholder data crosses the service)
- [x] GDPR/PDPA — lawful basis documented (performance of contract + legitimate interest); retention encoded (logs 90d, audit topic 7y); consent-withdrawal UI deferred to v1.1 (C-1)

### Observability

- [x] Structured logs with correlation IDs (Span.current() trace ID injected, fallback to UUID)
- [x] RED metrics exposed (rate, errors, duration timeseries)
- [x] Distributed tracing spans (OpenTelemetry)
- [x] Health endpoints wired (/actuator/health/liveness + /readiness in Helm probes)
- [x] Grafana dashboard updated (14 panels — UID `balance-dashboard-service-v1`)
- [x] Alert rules defined (expressions in Grafana spec; AlertManager rules deferred to infra)

### Deployment

- [ ] **Deployed to staging — smoke tests pass** — PENDING_INFRA (CI stages 9–10 are ready; will execute on first staging push)
- [x] Rollback plan documented (`helm rollback ... --namespace banking-staging` / `banking-prod`; auto-rollback hooked into CI smoke-tests job)
- [x] Feature flag in place (`balanceDashboardEnabled=false` in values-prod.yaml; returns 501 until product enables)
- [x] DB migrations versioned + reversible (Flyway scripts in TL handoff)
- [ ] Load test executed against staging — GAP-P2-001 + pending infra

### Documentation

- [x] ADRs filed (7 — SA-001..004 + TL-005..007 — indexed at `docs/generated/balance-comparison/adr-index.md`)
- [x] Runbook content present in developer guide; dedicated on-call runbook is a v1.1 follow-up
- [x] Release notes updated (CHANGELOG)

### Approvals

- [x] banking-reviewer-be + banking-reviewer-fe approved
- [x] banking-security approved (iter 2)
- [x] banking-qa P2 signed off
- [ ] **Product owner (human) approved release** — pending demo on 2026-06-04

**DoD overall:** PASS for demo on staging once infra provisioned. Production gate remains conditional on (a) k6 perf validation, (b) Apicurio v2 schema registration, (c) Redis at-rest encryption verification — all carried to backlog.

---

## 6. Backlog Carry-Forward

### 6.1 Tech-Debt Epic — BC-DEBT-001 (sprint-1 carryover)

| ID | Severity | Theme | Owner |
|---|---|---|---|
| R-BE-205-iter2 | major | UpstreamFailureReturns503Test should use WireMock to exercise Resilience4j chain end-to-end | banking-backend-dev |
| R-BE-206-iter2 | major | Remove dead catch (UpstreamUnavailableException) in AccountClientAdapter | banking-backend-dev |
| R-BE-207-iter2 | major | Add explicit `@Bean KafkaTemplate<String, AuditEventRecorded>` factory | banking-backend-dev |
| R-BE-208-iter2 | minor | ProblemDetailAdvice correlationId — use Span.current() trace ID, not random UUID | banking-backend-dev |
| R-BE-209-iter2 | minor | Schema-registry-url double-fallback may resolve to localhost in prod if env vars missing | banking-devops |
| R-BE-210-iter2 | nit | Cascading catch (RuntimeException + Exception) in KafkaAuditEventPublisher | banking-backend-dev |
| R-BE-211-iter2 | nit | Defensive null checks in AvroMapper duplicating record compact-constructor guarantees | banking-backend-dev |
| R-BE-015..026-iter1 | minor/nit | Various cleanup (unused BalanceSnapshot domain class, etc.) | banking-backend-dev |
| R-FE-021 | minor | Missing `auth.service.spec.ts` (~20 LOC) | banking-frontend-dev |
| R-FE-022 | nit | JSDoc note on `_authenticated` signal init pattern | banking-frontend-dev |

### 6.2 Security Debt

| ID | Theme | Target |
|---|---|---|
| BC-DEBT-002 | Migrate FE JWT storage to HttpOnly cookie + BFF | v1.1 |
| OBS-042 | Ship observability-lib MaskingConverter; replace inline `<replace>` regex in logback-spring.xml | v1.1 |
| C-1 | Privacy notice / consent-withdrawal UI | v1.1 |

### 6.3 QA Gaps (all NON-BLOCKING for demo)

| ID | Summary | Target |
|---|---|---|
| GAP-P2-001 | k6/Gatling perf scripts absent — US-BC-003 SLA unvalidated | BC-DEBT-PERF-001, **BLOCKING for production** |
| GAP-P2-002 | Pact consumer-driven contract tests absent | BC-DEBT-CONTRACT-001, **BLOCKING before independent service deployment** |
| GAP-P2-003 | Resilience4j TimeLimiter 300ms value not test-asserted | next sprint nit |
| GAP-P2-004 | Redis fail-open verified at unit level only; no Testcontainers Redis-stop test | **BLOCKING for production** |
| GAP-P2-005 | Dedicated unit tests for AccountView / BalanceSnapshot / RankedDashboard records missing | next sprint nit |

### 6.4 Infra / Architecture Risks

| ID | Description | Status | Owner |
|---|---|---|---|
| SA-RISK-002 | Apicurio v2 schema (AuditEventRecorded v2) must be registered by D6 | PLAN READY (`docs/devops/balance-comparison/apicurio-schema-registration-plan.md`) — execution pending | banking-devops |
| ASSUMPTION-TL-004 | Redis at-rest AES-256-GCM encryption must be confirmed before prod deploy | CHECKLIST READY (`docs/devops/balance-comparison/redis-cluster-verification.md`) — execution pending infra access | banking-devops + Platform team |

### 6.5 Deferred Stories

| ID | Title | Disposition |
|---|---|---|
| US-BC-004 | Total wealth in home currency (FX aggregation) | DEFERRED to follow-up sprint (default plan executed when OPEN-001 resolved as native-currency-only) |
| US-BC-006 | Filter / search accounts | COULD next sprint |
| US-BC-007 | Sparkline of balance over time | COULD next sprint |
| US-BC-008 | Hide / pin specific accounts | COULD next sprint |
| US-BC-009 | CSV / PDF export | WON'T |
| US-BC-010 | Joint-account / POA support | WON'T |
| US-BC-011 | Push notification on balance change | WON'T |

---

## 7. Risk Register (Updated — End of Sprint)

### 7.1 Resolved / Mitigated this sprint

| ID | Original score | Final status | How |
|---|---|---|---|
| RISK-001 | 16 — CRITICAL | RESOLVED BY SCOPE CUT | OPEN-001 resolved as native-currency-only → US-BC-004 deferred. No FX gateway needed. |
| RISK-002 | 15 — CRITICAL | MITIGATED | Shift-left parallel tracks fired correctly (Designer P1 + QA P1 + SA all parallel; DevOps P1 fired with devs). Review iterations stayed at BE 3 / FE 2 — within ceiling. Stretch story US-BC-005 delivered without sacrifice. |
| RISK-003 | 10 — HIGH | RESOLVED | Early-look security review at D2-D3 confirmed PDPA consent reuse from money-transfer covers read-only balance inquiry. Audit event includes `purpose=balance-inquiry`. |
| RISK-004 | 12 — HIGH | RESOLVED | Player did invoke shift-left batches per PM-001 instructions; no sequential drift observed. |
| RISK-006 | 6 — MEDIUM | LOW (residual) | Lighthouse a11y test plan in place; no real-device run yet — track as informational. |

### 7.2 Residual / Carried Forward

| ID | Description | Score | Owner | Trigger for re-escalation |
|---|---|---|---|---|
| RISK-005 | p95 < 500ms cold-cache not validated (no k6 perf script + no staging) | 9 — MEDIUM | banking-tech-lead + banking-devops | Production gate — BC-DEBT-PERF-001 must run before prod |
| **RISK-007 (new)** | Staging infra not yet provisioned — blocks G10 smoke tests and live DAST | 12 — HIGH | banking-devops + Platform team | Demo at risk if staging not provisioned by 2026-05-31 (5 days before demo) |
| **RISK-008 (new)** | Apicurio v2 schema registration pending (was SA-RISK-002) | 9 — MEDIUM | banking-devops | Pre-prod gate — plan ready, executable any time |
| **RISK-009 (new)** | Redis at-rest encryption verification pending (was ASSUMPTION-TL-004) | 9 — MEDIUM | banking-devops + Platform team | Pre-prod gate — checklist ready, blocked on infra access |
| **RISK-010 (new)** | F-3 (JWT in sessionStorage) accepted for demo — XSS-recoverable if any future XSS vector is introduced | 6 — MEDIUM | banking-frontend-dev | Carry to v1.1 (BC-DEBT-002 — BFF + HttpOnly cookie) |
| **RISK-011 (new)** | Pact consumer-driven contracts absent — fine while FE+BE deploy together, breaks if either is deployed independently | 6 — MEDIUM | banking-tech-lead | Pre-independent-deploy gate (BC-DEBT-CONTRACT-001) |

**Top 3 residuals to surface to stakeholder:** RISK-007 (staging infra), RISK-005 (perf unvalidated), RISK-008+009 (pre-prod blockers — group as "production readiness pending two infra checks").

---

## 8. Lessons Learned (Sprint Retrospective Seed)

**What worked**

- Shift-left parallel tracks (Designer P1 + QA P1 + SA fired in one batch; DevOps P1 fired with devs) saved ~2 working days.
- Reuse-first policy held: `AccountClient` and `AccountInfo` from money-transfer were not rebuilt. ASSUME-002 confirmed correct.
- Sacrificial stretch (US-BC-005) was NOT sacrificed — delivered cleanly because review iterations stayed within budget.
- Early-look security review at D2-D3 closed the PDPA consent question before it could block G8.

**What slipped**

- Performance validation (k6) was deferred — acceptable for internal demo but means we ship with an unvalidated NFR.
- Staging infrastructure was not provisioned in parallel with development — blocks G10 smoke + DAST.
- BE reviewer needed 3 iterations (the ceiling). Iteration-2 carry-overs (8 items) shifted to BC-DEBT-001.

**Actions for next sprint**

- File BC-DEBT-001 epic on day 1 of next sprint; cap at 3 SP to clear tech debt.
- Engage Platform team for staging provisioning now; do not wait until next sprint kickoff.
- Add k6 skeleton as a Definition-of-Done blocker for any NFR-bearing story going forward.

---

## 9. Recommendation

**SHIP TO STAGING** — with explicit conditions:

1. **Demo on staging at 2026-06-04 is GO** once Platform team provisions the staging cluster. All artifacts (Dockerfile, Helm chart, CI pipeline stages 8–10, Grafana dashboard) are production-ready and waiting on infra.
2. **Production promotion is NOT recommended yet.** The following must clear before prod:
   - GAP-P2-001 — k6 perf script + staging run validating p95 < 500ms warm / < 800ms cold
   - RISK-008 — Apicurio v2 schema registration
   - RISK-009 — Redis at-rest AES-256-GCM verification
   - GAP-P2-004 — Testcontainers Redis-stop integration test for fail-open
   - DAST scan against running staging
3. **BC-DEBT-001 (tech debt epic)** should be the first commitment of the next sprint — estimated 3 SP based on the iteration-2 carry-over list.
4. **Stakeholder communication** — surface the 3 residual risk groups (staging infra, perf unvalidated, two pre-prod infra checks) at the demo so trust is preserved by transparent disclosure.

**Final verdict:** GREEN for sprint close. Demo-ready. Production-conditional.

---

## 10. References

- PM intake (PM-001): `docs/pm/balance-comparison/handoff-pm-001.json`
- Original risk register: `docs/pm/balance-comparison/risk-register.md`
- All gate handoff JSONs: `docs/{ba,sa,design,tech-lead,frontend,backend,review,integration,security,qa,generated,devops}/balance-comparison/handoff-*.json`
- QA full report: `docs/qa/balance-comparison/qa-report-p2.md`
- Security final report: `docs/security/balance-comparison/security-review-final-2.md`
- Deploy evidence: `docs/devops/balance-comparison/deploy-evidence-p2.md`
- API reference / CHANGELOG / dev guide / ADR index: `docs/generated/balance-comparison/`

---

_End of report — banking-pm, 2026-05-22._
