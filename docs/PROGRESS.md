# Progress — demo-skill-ai

> Last updated: 2026-05-21 · Branch: `stage/02-balance-comparison` · Session: 2 (Discovery + Design)

## ⚡ Quick Resume

**Main flow:** TL-001 STARTED (partial — 3/7 deliverables done) → **RESUME `banking-tech-lead`** for ADR-005/006/007 + implementation-notes.md + handoff-tl-001.json
**Side tracks:** US-MOCK-001..008 user stories drafted → **PAUSED:** `banking-frontend-dev` (Sonnet) for mockup HTML implementation

---

## 🎯 Active Sprint

`SPRINT-2026-Q2-BC-01` · feature: `balance-comparison` · dates: `2026-05-21` → `2026-06-04`

---

## ✅ Done

- **PM-001** — backlog + sprint goal + risk register (handoff-pm-001.json)
- **BA-001** — user stories US-BC-001..005 + AC + NFR (handoff-ba-001.json)
- **Security early-review** — APPROVED with 4 conditions (C-1..C-4); RISK-003 mitigated
- **SA-001** — architecture + ADR-001..004 (new `balance-dashboard-service`, Redis TTL-only, audit Avro v2, server-side ranking)
- **Designer P1 LO-FI** — 7 screens + user journey + interaction spec + a11y notes + 14 open questions for HI-FI (handoff-designer-001.json)
- **Designer P2 HI-FI** — design system foundation (W3C tokens + WCAG evidence) + 14 component specs + 7 screen specs + accessibility-final (handoff-designer-002.json)
- **US-MOCK-001..008** — user stories drafted for mockup sub-feature (`docs/design/balance-comparison/hifi/mockups/user-stories.md`)
- **Session-continuity skill** — `.claude/skills/session-continuity/` (this skill + template + cheatsheet)
- **TL-001 (PARTIAL)** — OpenAPI 3 specs (BDS + account-service extension) + db-schema.md (NO Flyway for BDS, JSONB v1 for audit). 4 deliverables still pending — see Next.

---

## ⏭ Next

- [ ] **RESUME `banking-tech-lead`** — finish remaining TL-001 deliverables:
  - ADR-005 (Design Token consumption contract — SCSS + CSS vars dual-emit)
  - ADR-006 (CustomerIdResolver pattern — Security C-3)
  - ADR-007 (Audit event publisher — Kafka, metadata-only payload per C-2 + ADR-003)
  - `implementation-notes.md` (module layout, Resilience4j config, FE/BE/DevOps notes)
  - `handoff-tl-001.json` (final envelope, `parallel_next: [FE-dev, BE-dev, DevOps P1]`)
  - Still-open SA items: #1 Resilience4j verify, #2 JWT scope flag as ASSUMPTION-TL-002, #3 FIXED_DEPOSIT semantic flag as ASSUMPTION-TL-001
- [ ] (parallel after TL) `banking-frontend-dev` + `banking-backend-dev` + `banking-devops` P1 — 3-way shift-left fan-out
- [ ] (side track) `banking-frontend-dev` (Sonnet) → US-MOCK-001..008 mockup HTML files

---

## ⏸ Paused / Side Tracks

| Track ID | What | Why paused | How to resume |
|---|---|---|---|
| **MOCKUP-TASK** | US-MOCK-001..008 HTML mockups using design tokens | User said "หยุดก่อน" after deciding to use Sonnet model | Switch to Sonnet → pick up at `banking-frontend-dev` invocation for Foundation (tokens.css + base CSS + README) + 5 MUST stories |

---

## 🚧 Blockers / Decisions Pending

(none — both tracks waiting for user direction on which to resume first)

---

## 📝 Recent Ad-Hoc Decisions

> Append-only log of in-session choices that aren't in formal artifacts. Newest first.

- **2026-05-21 (end of session — `จบ session`)** — Session ปิดอย่างเป็นทางการผ่าน session-continuity skill. 10 commits ทั้งหมด persisted. Next session: พิมพ์ `ถึงไหนแล้ว` เพื่อดูภาพรวม หรือ `ทำต่อ` เพื่อ resume TL ที่ค้าง.
- **2026-05-21 (end of session)** — Session ปิดด้วยงานค้างที่ TL-001 partial. Persist ทั้งหมดก่อนปิดเครื่อง — TL ค้าง 4 deliverables ระบุชัดใน Next.
- **2026-05-21 (late)** — Created `session-continuity` skill + `docs/PROGRESS.md` to solve cross-session memory loss. Commands: `ถึงไหนแล้ว` / `ทำต่อ` / `บันทึก` / `จบ session`.
- **2026-05-21** — User approved Sonnet model for FE implementation tasks (US-MOCK HTML mockups + future Angular implementation). Opus stays for strategic agents (PM/BA/SA/TL/Designer/Security).
- **2026-05-21** — MOCKUP-TASK paused mid-flow; will resume after user explicit command. 8 user stories committed for record but no implementation yet.
- **2026-05-21** — Mockup deliverable format chosen: HTML + CSS prototype → screenshot to PNG (tokens enforced via `var(--color-*)`). Alternatives (SVG, Figma) rejected.
- **2026-05-21** — Heroicons outline v2.x (MIT) locked as icon family for entire banking project; SAVINGS=banknotes, CURRENT=credit-card, FIXED_DEPOSIT=lock-closed.
- **2026-05-21** — All 14 OPEN-D items resolved during Designer P2; key resolution: server `meta.freshness` JSON field drives global stale banner (per SA-001 event-flows §3.1).

---

## 🔗 Key Artifacts

| Phase | Artifact | Path |
|---|---|---|
| PM | handoff-pm-001.json | `docs/pm/balance-comparison/handoff-pm-001.json` |
| BA | handoff-ba-001.json | `docs/ba/balance-comparison/handoff-ba-001.json` |
| Security early | early-review-consent-coverage.md | `docs/security/balance-comparison/early-review-consent-coverage.md` |
| SA | handoff-sa-001.json | `docs/sa/balance-comparison/handoff-sa-001.json` |
| Designer P1 | handoff-designer-001.json | `docs/design/balance-comparison/handoff-designer-001.json` |
| Designer P2 (HI-FI) | handoff-designer-002.json | `docs/design/balance-comparison/hifi/handoff-designer-002.json` |
| Design tokens (shared) | tokens.json (W3C spec) | `docs/design/_shared/tokens.json` |
| HI-FI component specs | component-specs.md (14 components) | `docs/design/balance-comparison/hifi/component-specs.md` |
| HI-FI screen specs | screen-specs.md (7 screens) | `docs/design/balance-comparison/hifi/screen-specs.md` |
| HI-FI a11y final | accessibility-final.md | `docs/design/balance-comparison/hifi/accessibility-final.md` |
| Mockup user stories | user-stories.md (US-MOCK-001..008) | `docs/design/balance-comparison/hifi/mockups/user-stories.md` |
| TL OpenAPI (BDS) | balance-dashboard-service.openapi.yaml | `docs/tech-lead/balance-comparison/openapi/balance-dashboard-service.openapi.yaml` |
| TL OpenAPI (account-service ext) | account-service-extension.openapi.yaml | `docs/tech-lead/balance-comparison/openapi/account-service-extension.openapi.yaml` |
| TL DB schema decisions | db-schema.md | `docs/tech-lead/balance-comparison/db-schema.md` |

---

## 📚 Session History

- **Session 2 (2026-05-21):** Discovery + Design phase end-to-end — PM → BA → Security early → SA → Designer P1 → Designer P2. Also created session-continuity skill. Branch: `stage/02-balance-comparison`. Commits: TBD (pending user `git commit`).
- **Session 1 (earlier):** Bootstrap — project structure, agents, skills, docs scaffolding. Committed to `main` branch.

---

## 🚦 Quality Gate Status

| Phase | Gate Passed | Iteration |
|---|---|---|
| PM-001 | ✅ true | 1 |
| BA-001 | ✅ true | 1 |
| Security early | ✅ APPROVED (with C-1..C-4) | 1 |
| SA-001 | ✅ true | 1 |
| Designer P1 | ✅ true | 1 |
| Designer P2 | ✅ true | 1 |
| TL-001 | ⏳ PARTIAL (3/7 deliverables) | 1 |

**Overall sprint health:** GREEN — all completed phases passed first iteration; TL-001 in progress (paused mid-flow for end-of-session).
