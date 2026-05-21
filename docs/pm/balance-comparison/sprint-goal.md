# Sprint Goal — Account Balance Comparison Dashboard

> **Sprint ID:** `SPRINT-2026-Q2-BC-01`
> **Window:** 2026-05-21 → 2026-06-04 (2 weeks, demo on 06-04)
> **Owner:** `banking-pm` → executed by `banking-player`

---

## Sprint Goal (one sentence)

> **ส่งมอบ Read-Only Balance Comparison Dashboard ให้ retail customer ดูบัญชีของตนเองเรียงตามยอดเงินคงเหลือ — บน mobile-first UI, p95 < 500ms, reuse `AccountClient` ที่มีอยู่, demo-able บน staging ภายในวันที่ 2026-06-04.**

---

## Scope IN (committed)

| Story | Title | MoSCoW |
|---|---|---|
| US-BC-001 | List accounts ranked by balance (desc) | MUST |
| US-BC-002 | Per-account detail row (type, balance, currency, last updated) | MUST |
| US-BC-003 | Performance: p95 < 500ms for 10 accounts (with Redis cache) | MUST |
| US-BC-005 | Mobile-first responsive + a11y AA | SHOULD (stretch — drop if MUST slips) |

---

## Scope OUT (explicit)

- Any write / transaction (read-only sprint)
- US-BC-004 multi-currency total — **deferred** pending OPEN-001 FX-source decision
- Historical sparkline, filters, search, export, joint-account, preferences (all WON'T this sprint)
- Native mobile apps (Angular web only)
- New auth flow (reuse existing OAuth2/JWT)

---

## Demo Acceptance Criteria (2026-06-04)

ทุกข้อต้องผ่านครบบน staging environment:

1. **Functional**
   - [ ] Retail customer login → เห็นรายการบัญชีของตนเองเรียง balance จากมากไปน้อย (≥ 3 บัญชีในชุดทดสอบ)
   - [ ] แต่ละแถวแสดง: `accountType` (label ภาษาไทย/อังกฤษ), `balance` (formatted), `currency`, `lastUpdatedAt` (relative + absolute on hover)
   - [ ] บัญชีที่เป็นของลูกค้าคนอื่นห้ามปรากฏ (authorization enforced — verified โดย QA scenario)

2. **Non-functional**
   - [ ] p95 < 500ms วัดจาก 10-account warm-cache scenario (Gatling / k6 run, evidence in QA report)
   - [ ] p95 < 1000ms วัดจาก 10-account cold-cache scenario (acceptable demo-time degradation)
   - [ ] Mobile viewport 375px ทำงาน (no horizontal scroll, all CTAs reachable thumb-zone) — verified โดย Designer + QA
   - [ ] Lighthouse a11y score ≥ 90 บน demo page

3. **Compliance / Security**
   - [ ] PDPA: ไม่มี full account number ใน UI หรือ logs (masking pattern ตาม money-transfer convention)
   - [ ] BoT: audit-service event emitted ทุกครั้งที่ดึง dashboard (verified ใน audit-service log query)
   - [ ] OWASP Top 10 review by `banking-security` passes — no critical/high
   - [ ] JWT validation enforced — anonymous request returns 401

4. **Deployment / Observability**
   - [ ] Deployed to staging via Helm; smoke tests green
   - [ ] Grafana panel live: request rate / error rate / p95 / cache hit ratio
   - [ ] Rollback runbook documented (revert previous Helm release)
   - [ ] Feature flag `balance-dashboard.enabled` available (off by default in non-staging)

5. **Quality Gates (per [quality-gates.md](../../architecture/quality-gates.md))**
   - [ ] Discovery → Planning: requirement completeness ≥ 90%, OPEN-001..004 resolved
   - [ ] Planning → Design: all NFRs traced to ADR
   - [ ] Design → Development: OpenAPI lints clean, threat model done
   - [ ] Development → Review: unit coverage ≥ 80% (≥ 95% on aggregation/ranking)
   - [ ] Review → Security: zero blocker/major comments
   - [ ] Security → QA: SAST clean, no high/critical CVE, no secrets in code
   - [ ] QA → DevOps: all suites green, p95 SLA met, critical-path coverage ≥ 95%
   - [ ] DevOps → Done: deploy green, dashboards live, rollback rehearsed

---

## Capacity Check

| Metric | Value |
|---|---|
| Sprint length | 2 weeks (10 working days) |
| Reference velocity (Money Transfer sprint) | ~12 SP |
| Committed (MUST) | 7 SP |
| Stretch (SHOULD US-BC-005) | +2 SP = 9 SP |
| Buffer for review iterations + integration | ~3 SP (25% headroom) |
| Verdict | **Within capacity** — demo deadline achievable if shift-left tracks fire on Day 2 |

---

## Day-by-Day Milestone Plan (PM-level — Player operationalizes)

| Day | Milestone | Owner agent(s) |
|---|---|---|
| D1 (05-21) | PM-001 envelope → BA invoked | `banking-pm` → `banking-ba` |
| D2 | BA-001 ready → SA + Designer P1 + QA P1 start in parallel | `banking-ba`, then 3-way fan-out |
| D3-D4 | SA-001 + Designer P1 + QA P1 done; Designer P2 + TL start | `banking-solution-architect`, `banking-designer`, `banking-qa` |
| D5 | TL-001 (OpenAPI + DB + tokens) ready → FE + BE + DevOps P1 start | `banking-tech-lead` |
| D6-D7 | FE + BE implementation; DevOps P1 CI skeleton ready | parallel devs |
| D8 | Reviewers FE/BE in parallel; iterate ≤ 2 rounds | `banking-reviewer-fe`, `banking-reviewer-be` |
| D9 | Security review + QA P2 (perf + E2E) | `banking-security`, `banking-qa` |
| D10 (06-04) | DevOps P2 deploy + demo dry-run + DoD checklist + stakeholder report | `banking-devops`, `banking-pm` (report) |

---

## Definition of Sprint Success

Sprint succeeds when:
1. All MUST stories meet demo acceptance criteria on staging by 06-04 09:00 ICT (demo prep buffer)
2. DoD checklist ([definition-of-done.md](../../architecture/definition-of-done.md)) — Universal + Banking-Specific sections — fully checked
3. Stakeholder summary report emitted by `banking-pm` post-demo
4. RAID log updated with carry-over items (US-BC-004 FX, COULD stories) for next sprint planning
