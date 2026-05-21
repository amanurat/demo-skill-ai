# Stakeholder Summary — Account Balance Comparison Dashboard

> **Audience:** Business sponsor / executive reviewer
> **Author:** `banking-pm`
> **Date:** 2026-05-21
> **Status:** Sprint approved for execution — demo target 2026-06-04

---

## Problem

ลูกค้า retail ที่มีหลายบัญชี (savings, current, fixed) ต้องสลับหน้าจอหลายครั้งใน mobile app เพื่อดู balance แต่ละบัญชี ไม่มีหน้าสรุปที่เห็น **ทุกบัญชีในมุมเดียวกัน + เรียงลำดับยอดเงิน** เพื่อช่วยตัดสินใจทางการเงินได้เร็ว

---

## Value

| Stakeholder | Value delivered |
|---|---|
| Retail customer | เห็นภาพรวมการเงินภายในหน้าจอเดียว ตัดสินใจเร็วขึ้น |
| Business / Product | เพิ่ม engagement บน mobile app; วาง foundation สำหรับ financial insights ใน roadmap ถัดไป |
| Compliance | ทุกการเปิดดู dashboard ถูก audit (BoT compliant); PDPA-compliant masking ของ account number |
| Engineering | Reuse `AccountClient` + `AccountInfo` จาก Money Transfer feature — ไม่สร้างของซ้ำ ลดต้นทุน maintain |

---

## Approach

**Read-only dashboard** บน Angular (mobile-first) เรียกผ่าน OAuth2 → API gateway → **balance-dashboard aggregation endpoint** (new) ที่ใช้ `AccountClient` (reuse) ดึงข้อมูลทุกบัญชีของลูกค้า แล้ว rank desc by balance ก่อนส่งให้ frontend แสดงผล มี Redis cache (TTL 30s) เพื่อให้ p95 < 500ms

**No new service necessarily** — Solution Architect จะตัดสินใจว่าจะเป็น service ใหม่ หรือ endpoint ใหม่ใน `account-service` ผ่าน ADR

```
[Mobile Web] → [API Gateway/OAuth2] → [balance-dashboard endpoint]
                                          ↓
                              [AccountClient — REUSED]
                                          ↓
                              [account-service / Postgres]
                              [Redis cache layer — NEW]
                                          ↓
                              [audit-service event — REUSED]
```

---

## Scope this sprint (commit)

- US-BC-001 List accounts ranked desc by balance
- US-BC-002 Per-account details (type, balance, currency, last updated)
- US-BC-003 p95 < 500ms with Redis caching (10 accounts)
- US-BC-005 (stretch) Mobile-first responsive + accessibility AA

## Explicitly NOT this sprint

- Multi-currency total / FX conversion (pending stakeholder decision on FX source — see Ask below)
- Historical trends, charts, filters, export
- Joint-account visibility
- Native mobile apps

---

## Timeline

| Date | Milestone |
|---|---|
| 2026-05-21 | Kick-off — PM-001 handed to `banking-ba` |
| 2026-05-22 | BA stories complete; Solution Architect + Designer (LO-FI) + QA (test plan) start in parallel (shift-left) |
| 2026-05-25 | Tech Lead emits OpenAPI + DB schema + design tokens |
| 2026-05-26 | Frontend + Backend dev start; DevOps CI/CD skeleton parallel (shift-left) |
| 2026-06-02 | Reviewers + Security + QA full automation |
| 2026-06-04 | **Demo on staging** — DoD checklist complete |

---

## Top 3 Risks (full register: [risk-register.md](risk-register.md))

1. **FX rate source undecided** (score 16) — default plan: ship native currency only, defer multi-currency total to next sprint
2. **10-day demo window is tight** (score 15) — mitigated by reuse-first + shift-left parallelism + sacrificial stretch story
3. **PDPA consent reuse from Money Transfer needs verification** (score 10) — security will do an early-look review at D2 rather than waiting until D9

---

## Asks from Stakeholder (need by D2 = 2026-05-22 EOD)

1. **Confirm FX policy** for multi-currency display (OPEN-001):
   - Option A — Display in native currency only; show currency badge per row (default if no decision)
   - Option B — Convert to customer home currency using BoT mid-rate (requires new FX service — moves US-BC-004 to next sprint)
   - Option C — Show both native + converted (most informative; highest effort — definitely next sprint)

2. **Confirm "last updated" semantic** (OPEN-002): ledger `balance_as_of` (recommended) vs row `updated_at`

3. **Confirm dormant/closed account visibility** (OPEN-003): hide (recommended) vs grey-out

4. **Confirm in-scope account types** (OPEN-004): deposit accounts only (recommended) vs include loan/credit-card balances

---

## What "Done" Looks Like (executive view)

- ✅ Stakeholder demo runs on staging URL on 2026-06-04 — login as retail test customer → see ranked balance dashboard on phone
- ✅ p95 latency dashboard panel (Grafana) shows green during demo
- ✅ Audit log query proves every dashboard load was recorded (BoT compliance evidence)
- ✅ Stakeholder summary report from PM closes out the sprint and proposes follow-up sprint backlog

---

## Follow-up Sprint Preview (after demo)

Carry-over items already identified for the next sprint planning session:
- US-BC-004 — multi-currency total (pending FX decision)
- US-BC-005 — a11y AA polish if dropped from stretch
- US-BC-006 — filter / search accounts
- US-BC-007 — sparkline of balance over time
- US-BC-008 — hide / pin specific accounts (customer preference)
