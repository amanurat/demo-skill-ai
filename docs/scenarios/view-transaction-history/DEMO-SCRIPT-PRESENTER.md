# Demo Script (Presenter) — "View Transaction History" (Live, 17 min)

> **Audience:** ทีม Dev (15-20 คน) | **Mode:** Live run | **Total:** 17 นาที (buffer +3 min)
> **Goal:** แสดง end-to-end SDLC จาก raw requirement → deploy ด้วย AI agents 13 ตำแหน่ง โดยรัน agent จริงต่อหน้าผู้ฟัง

---

## Pre-flight Checklist (30 min ก่อน demo)

### Environment
- [ ] `git status` ใน `demo-skill-ai/` ต้อง clean (commit / stash ของที่ค้าง)
- [ ] อยู่บน branch `main` (`git checkout main && git pull`)
- [ ] Terminal font ≥ 16pt (zoom สำหรับผู้ฟังเห็นชัด)
- [ ] Theme: dark mode (contrast ดีกับ projector)
- [ ] WiFi เสถียร — backup: tethering มือถือ
- [ ] Screen recording กดเริ่ม (QuickTime / OBS)
- [ ] Stopwatch บนจอ (iOS Clock / macOS app)

### Files ready to open (เปิดไว้แต่ละ tab)
- [ ] Tab 1: Terminal ที่ `demo-skill-ai/` root
- [ ] Tab 2: `docs/scenarios/view-transaction-history/prompts-cheatsheet.md` (copy/paste source)
- [ ] Tab 3: `docs/architecture/SDLC-AGENT-Flow-v2.png` (architecture diagram)
- [ ] Tab 4: `docs/playbook.md` (agent chain overview)
- [ ] Tab 5: `docs/artifacts/S6-reviewer-money-transfer.md` (proof of reviewer wow moment)
- [ ] Tab 6: `docs/artifacts/FINAL-DOD-money-transfer-v1.md` (Money Transfer comparison)

### Window layout (3 panes)
```
┌──────────────────────────┬──────────────────────────┐
│                          │   Right-Top:             │
│   Left:                  │   playbook.md / diagram  │
│   Claude Code terminal   ├──────────────────────────┤
│   (60% of screen)        │   Right-Bottom:          │
│                          │   prompts-cheatsheet.md  │
│                          │   (copy source)          │
└──────────────────────────┴──────────────────────────┘
```

### Dry-run validation (วันก่อน demo)
- [ ] รัน act 1-3 จริงดู timing — ถ้าเกิน 8 min ให้ตัด narration ลง
- [ ] เช็คว่า fallback files พร้อม (`_fallback/` มี content)
- [ ] เตรียม backup slide ถ้า terminal crash

---

## Master Timeline

| Act | Time | Agents | Magic | Risk if slow |
|---|---|---|---|---|
| Opening | 0:00-2:00 | — | Stakes setting | Low — script-controlled |
| 1. Requirements | 2:00-4:00 | pm → ba | INVEST stories | Low |
| 2. Shift-left trio | 4:00-6:30 | sa + designer + qa (parallel) | 3 fires | Medium |
| 3. Tech Lead | 6:30-8:00 | tech-lead | OpenAPI live | Medium |
| 4. **THE PARALLEL FIRE** | 8:00-11:00 | be + fe + devops (parallel) | 3 streams | **High — longest** |
| 5. Reviewers | 11:00-13:30 | reviewer-be + reviewer-fe | Anti-pattern catch | Medium |
| 6. Security | 13:30-15:00 | security | STRIDE | Low |
| 7. DoD walkthrough | 15:00-16:00 | qa P2 + devops P2 (pre-rendered) | Speed-run | None |
| Wrap-up | 16:00-17:00 | — | Timer reveal | None |

---

## [0:00 — 2:00] Opening — Set the Stakes

### Presenter actions
1. กดเริ่ม stopwatch
2. Show terminal + `ls .claude/agents/` (เห็น 14 agent files)
3. Show `docs/architecture/SDLC-AGENT-Flow-v2.png`

### Narration (Thai)

> "สวัสดีครับทุกคน วันนี้เราจะลองทำสิ่งหนึ่ง — Build feature ใหม่จาก scratch แบบ end-to-end ใน 17 นาที"
>
> "ปกติ feature ขนาดเดียวกันนี้ ทีม dev เราใช้ประมาณ 2 สัปดาห์ — BA แตก requirement, Architect, Designer, Tech Lead, BE, FE, Reviewer, Security, QA, DevOps — 8-10 handoff"
>
> "วันนี้ทุกตำแหน่งจะเป็น AI agent ที่ specialize เฉพาะทาง พวกเขาจะส่งงานต่อกันเองตาม contract ผมแค่กดปุ่ม start ครั้งเดียว"
>
> "Feature ที่จะ build: View Transaction History — endpoint ดูประวัติ transaction พร้อม filter ตามวันที่ + pagination. เล็กกว่า Money Transfer แต่ครบทุก phase"

**ห้ามลืม:** ย้ำว่า "agent ทำงานจริง ไม่ได้รันไว้ก่อน" — แสดง stopwatch บนจอ

### Filler if early
- ชี้ `CLAUDE.md` แล้วบอก "ในนี้คือ playbook ของ orchestrator — 13 agent + workflow + quality gates"

---

## [2:00 — 4:00] Act 1 — Requirements (BA via Player)

### Prompt (copy จาก cheatsheet ข้อ 1)

> Player, ใช้ `banking-ba` วิเคราะห์ feature นี้:
>
> Feature: View Transaction History (Read-only)
> อ่าน seed: `docs/scenarios/view-transaction-history/requirements.md`
>
> Output: บันทึก artifact ที่ `docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md` พร้อม JSON envelope
> ต้องการ: ≥ 5 INVEST user stories, AC แบบ Given/When/Then, NFR table, open questions flag

### Narration (พูดขณะ agent ทำงาน)

> "BA agent กำลังโหลด skill — INVEST framework, Gherkin syntax, banking compliance checklist"
>
> "สิ่งที่มันต้องทำคือ — แตก raw requirement ให้เป็น story ที่ dev เอาไปทำงานได้ทันที + flag คำถามที่ต้องถาม PO"
>
> "ในชีวิตจริง BA คนหนึ่งใช้ 1-2 วัน — agent นี้ใช้เวลาประมาณ 90 วินาที"

### Expected output

- 5-7 user stories with `US-VTH-001` format
- AC แต่ละ story ≥ 2 scenario (happy + edge)
- NFR table: latency, throughput, availability, retention
- ≥ 2 open questions flagged สำหรับ PO/compliance

### Budget: 90-120 sec   |   Filler if slow:
เปิด `docs/artifacts/S2-ba-money-transfer.md` แล้วชี้:
> "เคส transfer เกิด 11 stories + 43 AC. เคสนี้ scope เล็กกว่า — น่าจะได้ 5-7 stories"

---

## [4:00 — 6:30] Act 2 — Shift-Left Trio (SA + Designer + QA parallel)

### Prompt (cheatsheet ข้อ 2 — ส่ง 3 Task tool calls ใน message เดียว)

> Player, ยิง PARALLEL ใน 1 message:
>
> 1. `banking-solution-architect` — อ่าน S2-ba → ตัดสินใจ reuse account-service vs สร้าง transaction-query-service ใหม่ + ADR ≥ 3 ข้อ → `S3-sa-view-transaction-history.md`
> 2. `banking-designer` (Phase 1 LO-FI) — อ่าน S2-ba → wireframe list view + filter bar + 5 states (loading/empty/data/error/end-of-list) → `S3-designer-view-transaction-history.md`
> 3. `banking-qa` (Phase 1 shift-left) — อ่าน S2-ba → test plan ≥ 8 scenarios + perf SLA → `S3-qa-testplan-view-transaction-history.md`

### Narration

> "นี่คือ shift-left ของจริง — 3 บทบาททำงานขนาน ไม่รอใคร"
>
> "QA agent กำลังเขียน test cases ก่อนที่จะมีโค้ดด้วยซ้ำ — TDD level system"
>
> "ในทีมจริง — Architect ใช้ 2-3 วัน, Designer ใช้ 3-5 วัน, QA test plan ใช้ 1-2 วัน. ที่นี่ 3 ตัวพร้อมกัน ใน ~2 นาที"

### Expected output (3 streams)

- **SA:** Decision = "extend transfer-service" + Kafka event `TRANSACTION_VIEWED` (optional) + 3 ADRs
- **Designer:** Wireframe (ASCII art OK), 5 states ครบ, mobile responsiveness note
- **QA:** Scenarios — happy, empty result, date range edge, large dataset (1M rows), permission denial, downstream timeout, pagination boundary, sort order

### Budget: 150 sec   |   Magic moment script:
หลัง 3 outputs ขึ้นจอ:
> "นับ — 3 agents fired ใน 1 message. Sequential นี่ ~8-10 นาที ตอนนี้ 2.5 นาที"

### Filler if slow:
เปิด `docs/architecture/SDLC-AGENT-Flow-v2.png` แล้วชี้ "เห็น parallel arrow ตรงนี้ไหม — นี่คือ shift-left ของจริง"

---

## [6:30 — 8:00] Act 3 — Tech Lead (The Contract)

### Prompt (cheatsheet ข้อ 3)

> Player, ใช้ `banking-tech-lead` อ่าน S2 + S3 ทั้ง 3 ตัว → ออก:
> - OpenAPI 3 spec ที่ `S4-openapi.yaml` (GET /api/v1/transactions พร้อม pagination + RFC 7807 errors)
> - DB query design (JPA Specification, index strategy) ที่ `S4-tl-view-transaction-history.md`
> - ADR เรื่อง read replica vs main DB
> โหลด skill: `openapi-flyway-standards`

### Narration

> "Tech Lead รวม input จาก SA + Designer + QA แล้วออก contract เดียวที่ทั้ง BE และ FE จะใช้ร่วม"
>
> "Read-only — ไม่ต้องมี Idempotency-Key. แต่ต้องมี Pageable contract + Problem Detail สำหรับ error + JPA Specification ที่ป้องกัน N+1"
>
> "ขั้นนี้คือจุด lock contract — หลังจากนี้ FE และ BE ทำงานคู่กันได้โดยไม่ต้องคุยกัน"

### Expected output

- `openapi.yaml`: GET endpoint, query params (from, to, type, page, size), `Page<TransactionDto>` schema
- DB design: index on `(customer_id, executed_at DESC)` + read-only transaction
- ProblemDetail schema with 400 (bad date), 401, 403, 429 (rate limit)

### Budget: 90 sec

---

## [8:00 — 11:00] Act 4 — THE PARALLEL FIRE (BE + FE + DevOps)

### Prompt (cheatsheet ข้อ 4 — 3 Task calls ใน 1 message — **จุดที่ wow ที่สุด**)

> Player, ยิง PARALLEL ใน 1 message — 3 Task tool calls:
>
> 1. `banking-backend-dev` — อ่าน S4 → implement ที่ `backend/transaction-query-service/`
>    - Controller + Service + Repository (JPA Specification) + Pageable
>    - `@Transactional(readOnly = true)` + masking + i18n error messages
>    - JUnit 5 unit tests + Testcontainers integration test
>    - โหลด skill: `spring-boot-banking` + `resilience4j-patterns`
>
> 2. `banking-frontend-dev` — อ่าน S3-designer + S4 → implement ที่ `frontend/src/app/features/transactions/`
>    - Standalone component + Signal-based state + Reactive Form filter
>    - HttpClient typed (openapi-generator output) + loading/empty/error states
>    - WCAG 2.1 AA + Thai/English i18n + Jest tests
>    - โหลด skill: `angular-banking-ui`
>
> 3. `banking-devops` (Phase 1 shift-left) — สร้าง CI/CD skeleton:
>    - `infra/transaction-query/Dockerfile` (multi-stage, distroless, non-root)
>    - `infra/transaction-query/helm/` (chart skeleton + values.yaml)
>    - Grafana dashboard panel `transaction_query_duration_seconds`
>    - โหลด skill: `banking-devops-platform`

### Narration (พูดยาวๆ ขณะรอ ~3 นาที)

> "นี่คือจุดที่ทุกคนควรถ่ายรูป — Backend, Frontend, DevOps ขึ้นพร้อมกันใน 1 message"
>
> "ในทีมจริง — Dev ต้องรอ contract เสร็จก่อน, DevOps ต้องรอ Dev เสร็จก่อนจะ containerize"
>
> "ที่นี่ — DevOps shift-left ทำ Helm + Dockerfile + Grafana ไปขนานกับ dev. ตอน BE/FE ส่งโค้ดเสร็จ Docker image พร้อมยิงได้เลย"
>
> [ขณะรอ พูดต่อ:]
> "Skill packs ที่กำลังโหลด — `spring-boot-banking` มี 1,200 บรรทัด รวม hexagonal pattern + JPA anti-patterns + observability + security rules"
> "`angular-banking-ui` มี standalone component template, reactive form pattern, WCAG checklist, i18n setup"
> "`banking-devops-platform` มี Helm chart template, K8s manifests, Prometheus alert rules"

### Expected output (3 streams)

| Stream | Files |
|---|---|
| BE | `TransactionController.java`, `TransactionQueryService.java`, `TransactionSpecification.java`, `TransactionRepository.java`, `TransactionMapper.java`, `TransactionDto.java`, ProblemDetail handler, unit tests (≥ 8), integration test |
| FE | `transaction-list.component.ts/html/css`, `transaction.service.ts`, `transaction-filter.component.ts`, `transaction.model.ts`, i18n files (th/en), Jest specs |
| DevOps | `Dockerfile`, `helm/Chart.yaml`, `helm/values.yaml`, `helm/templates/*.yaml`, `grafana-panel.json` |

### Budget: 180 sec   |   Filler if slow:
เปิด `docs/artifacts/S5-backend-dev-money-transfer.md` แล้วบอก:
> "เคส transfer ออก 54 Java files. เคสนี้ scope เล็กกว่าครึ่ง — น่าจะได้ ~15-20 files"

---

## [11:00 — 13:30] Act 5 — Reviewers (The Anti-Pattern Catch)

### Prompt (cheatsheet ข้อ 5 — 2 Task calls parallel)

> Player, ยิง PARALLEL:
>
> 1. `banking-reviewer-be` — review `backend/transaction-query-service/` → `S6-reviewer-be-view-transaction-history.md`. โหลด skill: `code-review-checklists`
> 2. `banking-reviewer-fe` — review `frontend/src/app/features/transactions/` → `S6-reviewer-fe-view-transaction-history.md`

### Narration

> "Reviewers โหลด anti-pattern catalog — มี 100+ patterns รวม blocker / major / minor / nit"
>
> "ลุ้นว่ามันจะจับอะไรได้บ้าง"

### Expected output — THIS IS THE WOW MOMENT

อย่างน้อย **1 finding ต่อ reviewer** — ตัวที่น่าจะเจอ:

**BE Reviewer:**
- 🟡 Missing `@Transactional(readOnly = true)` on query method
- 🟡 N+1 query risk หาก eager-fetch counterparty
- 🟠 IDOR risk หาก `customer_id` มาจาก request param แทน JWT
- 🟢 Logging มี `customerId` (PII) — ควร hash

**FE Reviewer:**
- 🟡 Missing `trackBy` ใน `*ngFor` → re-render ทั้ง list ทุก state change
- 🟡 Subscription ไม่ unsubscribe — memory leak
- 🟠 `[innerHTML]` ใส่ memo จาก server — XSS risk
- 🟢 Date picker ไม่มี `aria-label`

### Magic moment script (เมื่อ finding ขึ้น)

หยุดอ่าน narration → ชี้ finding → พูดดังๆ:

> "เห็นไหม — มันจับ [N+1 query / missing trackBy / IDOR] ได้เอง โดยที่ไม่มีใครบอก!"
>
> "นี่คือเหตุผลที่เราใช้ specialized agent ไม่ใช่ generic LLM — มันรู้ pattern แล้วเช็คอัตโนมัติ"

### Budget: 120 sec

### Filler if no findings (worst case)
ถ้า reviewer ไม่จับ — เปิด `docs/artifacts/S6-reviewer-money-transfer.md`:
> "ในเคส Money Transfer มันจับได้ 5 major + 13 minor — รวมทั้ง missing Outbox event และ Saga compensation gap"

---

## [13:30 — 15:00] Act 6 — Security (STRIDE)

### Prompt (cheatsheet ข้อ 6)

> Player, ใช้ `banking-security` ทำ:
> - STRIDE threat model สำหรับ GET /api/v1/transactions
> - OWASP Top 10 banking lens — โดยเฉพาะ A01 (Broken Access Control), A03 (Injection), A04 (Insecure Design — IDOR)
> - PDPA compliance (data masking, audit log)
> - Verdict + output: `S7-security-view-transaction-history.md`
> โหลด skill: `banking-security-patterns`

### Narration

> "Security agent ทำ STRIDE 6 หมวด — Spoofing, Tampering, Repudiation, Information Disclosure, DoS, Elevation"
>
> "Read-only endpoint แบบนี้ความเสี่ยงสูงสุดคือ **Information Disclosure** — ลูกค้า A ไม่ควรเห็น transaction ของลูกค้า B"
>
> "ต้องเช็ค: JWT enforce ไหม, customer_id ดึงจาก token ไม่ใช่ query param, log มี PII ไหม, rate limit ป้องกัน scraping ไหม"

### Expected output

- STRIDE table — 6 rows
- ≥ 1 finding ที่ severity ≥ medium (เช่น missing rate limit, log มี customer name)
- 0 critical / 0 high (ถ้า BE agent ทำดี)
- Compliance verdict: PDPA ✅ if masking present
- Verdict: `approved` หรือ `changes_requested`

### Budget: 90 sec

---

## [15:00 — 16:00] Act 7 — DoD Walkthrough (Pre-rendered)

### Presenter actions
1. เปิด `docs/artifacts/S8-qa-money-transfer.md` (เป็น reference shape)
2. เปิด `docs/artifacts/S9-devops-money-transfer.md`
3. เปิด `docs/artifacts/FINAL-DOD-money-transfer-v1.md`

### Narration

> "เวลาเหลือไม่พอรัน QA Phase 2 + DevOps Phase 2 สด — แต่ flow คือ:"
>
> "QA agent รัน test ทั้งหมด — JUnit + Testcontainers + E2E + perf k6 — แล้วออก report"
>
> "DevOps Phase 2 build Docker image + push registry + helm install staging + smoke test + rehearse rollback"
>
> "เปิด DoD checklist เคส Money Transfer — 14 ข้อ ติ๊กครบ. เคส View History จะเหมือนกัน scope เล็กกว่า"

### Budget: 60 sec

---

## [16:00 — 17:00] Wrap-up — The Timer Reveal

### Presenter actions
1. หยุด stopwatch → ชู้บนจอ
2. คำนวน: "X นาที X วินาที"
3. เปิด artifact tree (`tree docs/scenarios/view-transaction-history/` หรือ ls -la)

### Narration

> "เวลาที่ใช้: [X] นาที"
>
> "Agents ที่รัน live: 8 ตัว — BA, SA, Designer, QA(P1), Tech Lead, BE, FE, DevOps(P1), Reviewer-BE, Reviewer-FE, Security"
>
> "Parallel fires: 2 จุด — Act 2 และ Act 4 รวม 6 agent calls ใน 2 message"
>
> "เทียบกับเคส Money Transfer — 54 Java files, 40 tests passing, 0 critical security, 14 DoD ข้อผ่านครบ"

### Closing message

> "ประเด็นที่อยากให้คิด — ไม่ใช่ว่า AI จะแทนเราได้ แต่เราจะ direct AI ยังไงให้ทีม 1 คน leverage เท่าทีม 10 คน"
>
> "ทุก handoff มี JSON envelope ตรวจสอบได้, ทุก phase มี quality gate, ทุก agent มี persona + skill pack ที่ลึกพอ"
>
> "คำถาม?"

---

## Q&A Anchor Points (ตอบบ่อย)

| คำถาม | คำตอบสั้น |
|---|---|
| "Agent มันสุ่มไหม?" | ทุก agent มี persona + skill pack + JSON schema bind — output deterministic structure, content แตกต่างกันเล็กน้อยแต่ pass quality gate เหมือนกัน |
| "ราคาเท่าไหร่?" | เคส Money Transfer ใช้ ~$8-12 ต่อ end-to-end run. View History น่าจะ ~$3-5 |
| "Maintain agents ยังไง?" | Agent body 100 บรรทัด แก้ง่าย. Skill pack แยกออกมา reuse ได้ |
| "ถ้า agent ทำผิดล่ะ?" | Quality gate + reviewer agent จับ. ถ้ายัง miss — feedback loop (max 3 retries) → escalate ผู้ใช้ |
| "Production ใช้ได้จริงเหรอ?" | demo นี้คือ proof — ดู `backend/transfer-service/` มีโค้ดจริงพร้อม test 40 ตัวผ่าน |
| "FE/BE สื่อสารกันยังไง?" | OpenAPI contract ใน Act 3 → `openapi-generator` ผลิต typed client สำหรับ FE อัตโนมัติ |
| "ทำไม Reviewer ต้องแยก FE/BE?" | คนละ skill cluster — Java/Spring กับ Angular/TypeScript. Generic reviewer จับน้อยกว่า specialist |

---

## Risk Register (สิ่งที่อาจพลาด)

| Risk | Likelihood | Mitigation |
|---|---|---|
| Agent ตอบช้า > 2 min | Medium | เปิด fallback artifact + ข้าม |
| Context window full ระหว่างทาง | Low | Player จัด context — แต่ละ agent ทำงาน fresh context |
| Network drop | Medium | Tethering backup + dry-run validated |
| Agent generate output schema ผิด | Low | Player validate handoff schema — retry หรือ fallback |
| Reviewer ไม่จับ finding | Medium | เปิด Money Transfer S6 เป็น proof |
| Presenter พูดเร็วเกินไป | High | ฝึก dry-run + ดู stopwatch ทุก act |

---

## Post-demo Checklist

- [ ] บันทึก video / screenshot ลง `docs/demo/recordings/`
- [ ] รวบรวม Q&A ที่ตอบไม่ได้ → update FAQ section ในเอกสารนี้
- [ ] Commit artifacts ที่เกิดจาก demo (S2-S9) ลง git
- [ ] รัน agent ที่ skip ไป (QA P2, DevOps P2) แบบ offline → ครบ artifact tree
- [ ] อัพเดท `EXPECTED-OUTPUTS.md` ถ้า output shape ต่างจากที่คาด

---

## Appendix — Money Transfer Baseline (สำหรับเปรียบเทียบ)

| Metric | Money Transfer | View History (target) |
|---|---|---|
| User stories | 11 | 5-7 |
| AC count | 43 | ~20 |
| Java files | 54 | ~15-20 |
| Unit tests | 40 | ~15 |
| Kafka events | 14 | 0-1 |
| ADRs | 12 | 3-4 |
| Critical security findings | 0 | 0 (target) |
| Total agent time | ~25 min | ~17 min |
| Equivalent human team | ~2 weeks | ~1 week |
