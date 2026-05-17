# Playbook — Step-by-Step Sessions

> **วิธีใช้:** แต่ละ session ทำตาม checklist ติ๊กตามไป — มี "พิมพ์อะไรให้ Claude" + "git commit อะไรหลังเสร็จ" + "เช็คอะไรก่อนข้าม session ถัดไป"
>
> **Pattern หลัก:** คุณ = Product Owner / Approver | main Claude = Player (Orchestrator) | subagents = ทีมงาน

---

## 📊 Session Map

| Session | Phase | Lead Agent(s) | Output | Status |
|---|---|---|---|---|
| **S1** | Bootstrap | (none — manual setup) | Skill infrastructure | ✅ Done |
| **S2** | Discovery + Design | `banking-ba` → `banking-solution-architect` → `banking-tech-lead` | User stories, service map, OpenAPI, DB schema, ADRs | ⏳ Next |
| **S3** | Backend Implementation | `banking-backend-dev` | Spring Boot monorepo + microservices scaffold | ⏳ |
| **S4** | Frontend Implementation | `banking-frontend-dev` | Angular workspace + Money Transfer UI | ⏳ |
| **S5** | Review + QA + Sec | `banking-reviewer` → `banking-security` → `banking-qa` | Fixes, tests, sign-offs | ⏳ |
| **S6** | DevOps + Deploy | `banking-devops` | CI/CD, Docker, Helm, staging deploy | ⏳ |

---

## ✅ Session 1 — Bootstrap (DONE)

- [x] `CLAUDE.md` + `.claude/agents/` (10 files) + `docs/` created
- [x] Git initialized, `main` branch, initial commit
- [x] `.gitignore` covers macOS / IDE / secrets / Node / Java / Claude local
- [x] Prompt journey archived in `prompts/`

**Commit:** `chore: bootstrap AI multi-agent skill infrastructure`

---

## ⏳ Session 2 — Discovery + Design

> Goal: เปลี่ยน raw feature request → user stories → service architecture → API contract + DB schema

### Pre-checks
- [ ] อยู่ใน main branch (`git status` ต้อง clean)
- [ ] เปิด session ใหม่ใน Claude Code ที่โฟลเดอร์ `demo-skill-ai/`
- [ ] Claude อ่าน `CLAUDE.md` แล้ว (อัตโนมัติทุก session)

### Step 2.1 — BA Agent
**พิมพ์ให้ Claude:**
```
/agents ดูรายการ banking-* agents มีอะไรบ้าง

จากนั้นใช้ banking-ba วิเคราะห์ Money Transfer feature:
- ผู้ใช้ที่ login แล้วต้องโอนเงินจากบัญชีตัวเองไปบัญชีอื่นได้
- รองรับ idempotency, daily limit, audit trail
- โปรดส่งกลับเป็น handoff artifact ตาม docs/architecture/handoff-schema.md
```

**Expected output:**
- [ ] User stories ≥ 5 ตัว (happy path + insufficient + duplicate + limit + audit)
- [ ] AC แบบ Given/When/Then ทุกตัว
- [ ] NFR (p95, availability)
- [ ] Compliance list (PCI-DSS, GDPR, PDPA)
- [ ] Out-of-scope explicit
- [ ] JSON artifact validates against schema

**Save artifact:** Player ควรเซฟ output เป็น `docs/artifacts/S2-ba-money-transfer.json`

**Commit:**
```bash
git add docs/artifacts/
git commit -m "feat(ba): user stories + AC for money-transfer"
```

### Step 2.2 — Solution Architect
**พิมพ์ให้ Claude:**
```
ใช้ banking-solution-architect รับ artifact จาก S2.1 (docs/artifacts/S2-ba-money-transfer.json)
แล้วออกแบบ service map + event flow + ADRs สำหรับ Money Transfer
```

**Expected output:**
- [ ] Service map (≥ 5 services involved)
- [ ] Event list (TransferRequested, TransferCompleted, TransferFailed)
- [ ] ADRs ≥ 3 (Saga strategy, Outbox, Idempotency)
- [ ] NFR traceability table
- [ ] Mermaid diagram in artifact notes

**Save artifact:** `docs/artifacts/S2-architect-money-transfer.json` + ADRs ใน `docs/adr/0001-*.md`...

**Commit:**
```bash
git add docs/artifacts/ docs/adr/
git commit -m "feat(architect): service map + ADRs for money-transfer"
```

### Step 2.3 — Tech Lead
**พิมพ์ให้ Claude:**
```
ใช้ banking-tech-lead รับ artifact จาก S2.2 (docs/artifacts/S2-architect-money-transfer.json)
แล้วสร้าง:
1. OpenAPI 3 spec สำหรับ transfer-service
2. Flyway migration scripts
3. ADRs รายละเอียด (Idempotency-Key TTL, error code taxonomy, retry policy)
4. Implementation notes สำหรับ backend และ frontend
```

**Expected output:**
- [ ] `backend/transfer-service/api/openapi.yaml` (spec ยังไม่อยู่ใน code — แต่เก็บใน artifact)
- [ ] Flyway script content
- [ ] ADRs เพิ่ม (Idempotency TTL, error codes)
- [ ] Frontend notes (Idempotency-Key handling, optimistic UI ห้าม)

**Save artifact:** `docs/artifacts/S2-tech-lead-money-transfer.json` + spec files

**Commit:**
```bash
git add docs/artifacts/ docs/adr/
git commit -m "feat(tech-lead): OpenAPI + DB schema + ADRs for transfer-service"
```

### S2 DoD (ติ๊กก่อนข้าม S3)
- [ ] User stories ครบ + AC สมบูรณ์
- [ ] Service map ชัดเจน
- [ ] OpenAPI spec lints clean (run `npx @stoplight/spectral-cli lint <path>` ถ้าพร้อม)
- [ ] ≥ 5 ADRs filed
- [ ] 3 commits in git log
- [ ] No unresolved `open_questions` from BA

---

## ⏳ Session 3 — Backend Implementation

> Goal: Scaffold Spring Boot monorepo + implement transfer-service ตาม spec

### Pre-checks
- [ ] S2 ทุก artifact พร้อม
- [ ] Java 21, Maven 3.6+ พร้อมใช้งาน (`java --version && mvn --version`)

### Step 3.1 — Monorepo Scaffold
**พิมพ์ให้ Claude:**
```
ใช้ banking-backend-dev:
1. สร้าง Maven monorepo ตามโครงสร้างใน docs/architecture/project-structure.md
2. parent pom + parent-bom + common-libs (audit-lib, idempotency-lib, observability-lib)
3. 7 microservice modules (skeleton only — main class + application.yml + pom.xml)
```

**Expected output:**
- [ ] `backend/pom.xml` (parent)
- [ ] `backend/parent-bom/`, `backend/common-libs/*/`
- [ ] 7 service modules: `api-gateway`, `identity-service`, `account-service`, `transfer-service`, `ledger-service`, `notification-service`, `audit-service`
- [ ] `mvn clean verify -DskipTests` ผ่าน (build success)

**Commit:**
```bash
git add backend/
git commit -m "feat(backend): scaffold Maven monorepo with 7 microservices"
```

### Step 3.2 — transfer-service Implementation
**พิมพ์ให้ Claude:**
```
ใช้ banking-backend-dev implement transfer-service เต็มรูปแบบตาม:
- OpenAPI spec จาก S2.3
- Flyway migrations
- Hexagonal architecture (domain / application / infrastructure / interfaces)
- Saga orchestration + Outbox pattern + Idempotency
- Unit tests + integration tests (Testcontainers)
```

**Expected output:**
- [ ] Domain entities (Account, Transfer, Money value object)
- [ ] Application services (Saga steps)
- [ ] REST controller matching OpenAPI
- [ ] Kafka producers/consumers
- [ ] Unit tests coverage ≥ 80%
- [ ] Integration tests with Testcontainers pass
- [ ] `mvn clean verify` green

**Commit:**
```bash
git add backend/transfer-service/
git commit -m "feat(transfer): implement Money Transfer Saga with idempotency"
```

### S3 DoD
- [ ] Build green
- [ ] Coverage ≥ 80% (transfer-service ≥ 95%)
- [ ] OpenAPI in sync (springdoc generates matching spec)
- [ ] No anti-patterns (self-checked by agent)

---

## ⏳ Session 4 — Frontend Implementation

> Goal: Angular workspace + Money Transfer feature module

### Pre-checks
- [ ] S2 OpenAPI spec available
- [ ] `node --version` (≥ 20), Angular CLI installed (`npm i -g @angular/cli` ถ้ายัง)
- [ ] Backend can run locally (S3 complete) for E2E verification (optional)

### Step 4.1 — Workspace Scaffold
**พิมพ์ให้ Claude:**
```
ใช้ banking-frontend-dev:
1. สร้าง Angular workspace (latest LTS) ตามโครงสร้างใน docs/architecture/project-structure.md
2. apps/banking-web + libs/ui + libs/data-access + libs/feature-auth + libs/feature-transfer
3. Generate typed API client จาก OpenAPI spec ใน docs/artifacts/S2-tech-lead-*
```

**Commit:**
```bash
git add frontend/
git commit -m "feat(frontend): scaffold Angular workspace + generated API client"
```

### Step 4.2 — Money Transfer Feature
**พิมพ์ให้ Claude:**
```
ใช้ banking-frontend-dev implement libs/feature-transfer:
- หน้า list transfers + หน้า new transfer form
- Reactive forms + validation
- Idempotency-Key generation
- Error handling (Problem-Detail → i18n messages)
- Confirmation step (no optimistic UI)
- E2E test (Playwright) สำหรับ happy path
```

**Commit:**
```bash
git add frontend/libs/feature-transfer/
git commit -m "feat(transfer-ui): money transfer feature module with confirmation flow"
```

### S4 DoD
- [ ] `ng build` succeeds
- [ ] `ng test` ≥ 80% coverage
- [ ] axe-core a11y check pass
- [ ] No `any` in TypeScript

---

## ⏳ Session 5 — Review + Security + QA

> Goal: รัน review/security/QA agents ตามลำดับ → fix loops จนผ่าน

### Step 5.1 — Reviewer
**พิมพ์ให้ Claude:**
```
ใช้ banking-reviewer review ทุก code จาก S3 + S4
รายงาน findings พร้อม severity + suggested fix
ถ้ามี blocker/major ให้ loop back ไป backend-dev / frontend-dev
```

**Loop:** ถ้า `verdict: changes_requested` → Player invoke dev agent อีกครั้ง → review รอบ 2 → max 3 รอบ

**Commit per fix iteration:**
```bash
git commit -m "fix(transfer): address reviewer comments (anemic domain → Account.debit)"
```

### Step 5.2 — Security
**พิมพ์ให้ Claude:**
```
หลัง reviewer approve แล้ว ใช้ banking-security:
- STRIDE threat model สำหรับ /api/v1/transfers
- OWASP Top 10 checklist
- ตรวจ banking hard rules (no PII logs, idempotency, audit, RS256, ...)
```

**Loop:** ถ้า critical/high → loop back

**Commit:**
```bash
git commit -m "fix(transfer): security findings (HS256 → RS256 + Vault key rotation)"
```

### Step 5.3 — QA
**พิมพ์ให้ Claude:**
```
ใช้ banking-qa:
- Test plan ใน docs/test-plans/money-transfer.md
- เพิ่ม test cases ที่ขาด (concurrent, daily limit edge, Saga compensation)
- Run mutation tests (PIT)
- Performance baseline (Gatling/k6)
```

**Commit:**
```bash
git commit -m "test(qa): comprehensive test suite + perf baseline for money-transfer"
```

### S5 DoD
- [ ] Reviewer + Security + QA ทุก verdict = `approved`
- [ ] Test coverage ≥ 80% overall, ≥ 95% money paths
- [ ] Mutation score ≥ 70%
- [ ] Perf SLA met (p95 < 1s)
- [ ] No blocker/high findings open

---

## ⏳ Session 6 — DevOps + Deploy

> Goal: CI/CD + container + Helm + staging deploy

**พิมพ์ให้ Claude:**
```
ใช้ banking-devops:
1. Dockerfile (multi-stage, distroless/alpine, non-root) per service
2. docker-compose.yml สำหรับ local dev (PG, Kafka, Redis, ทุก service)
3. Helm chart per service ใน infra/helm/
4. GitHub Actions workflow ใน .github/workflows/ (lint, test, SAST/SCA, build, scan, deploy)
5. Grafana dashboard config + Prometheus alert rules
```

**Commit (แยกตาม artifact):**
```bash
git commit -m "feat(devops): Dockerfile + docker-compose for local dev"
git commit -m "feat(devops): Helm charts for all microservices"
git commit -m "ci: GitHub Actions pipeline with SAST/SCA/DAST"
git commit -m "feat(observability): Grafana dashboards + Prometheus alerts"
```

**Verification (local):**
- [ ] `docker compose up` → all services healthy
- [ ] Hit `/api/v1/transfers` end-to-end → response 200
- [ ] Grafana shows metrics
- [ ] Logs structured (JSON + correlation ID)

### S6 DoD
- [ ] CI pipeline green end-to-end
- [ ] Local docker-compose runs full stack
- [ ] Smoke tests pass
- [ ] Rollback command documented (`helm rollback ...`)
- [ ] Runbook in `docs/runbooks/` updated

---

## 🚨 Universal Rules (ทุก Session)

### Before พิมพ์สั่ง Claude
- [ ] อยู่ใน branch ที่ถูกต้อง
- [ ] `git status` clean (commit หรือ stash ก่อน)
- [ ] Open relevant docs ที่จะ reference (CLAUDE.md, schema, ฯลฯ)

### หลัง agent ส่ง artifact กลับมา
- [ ] Player **validate** envelope (artifact_id, from/to, phase, payload)
- [ ] Player apply **quality gate** ของ phase นั้น (ดู `docs/architecture/quality-gates.md`)
- [ ] ถ้า gate fail → loop back กับ comments (max 3 iterations)
- [ ] ถ้า gate pass → commit artifact + ส่งต่อ agent ถัดไป

### Git Discipline
- [ ] **1 agent output = 1 commit** (เพื่อ traceability)
- [ ] Conventional Commits (`feat(scope): ...`, `fix(scope): ...`, `chore: ...`, `test(scope): ...`)
- [ ] Commit body อธิบาย **why** ไม่ใช่แค่ what
- [ ] ไม่ใช้ `--no-verify` / `--amend` กับ commit ที่ปล่อยไปแล้ว
- [ ] Branch strategy: trunk-based, feature branch สำหรับงานใหญ่ (`feat/<phase>-<feature>`)

### Escalation
ถ้าเจอ:
- [ ] Iteration ครบ 3 รอบยังไม่ผ่าน → Player แจ้งผู้ใช้พร้อม options
- [ ] Conflict ระหว่าง agents → Player ขอ arbitration จากผู้ใช้
- [ ] Out-of-scope artifact → reject + แจ้ง

---

## 🎯 Cheat Sheet — สั่งใครเมื่อไหร่

| ผู้ใช้พิมพ์... | Player ควรเรียก... |
|---|---|
| "วิเคราะห์ feature ใหม่..." | `banking-ba` |
| "ออกแบบ architecture..." | `banking-solution-architect` |
| "เขียน API contract..." | `banking-tech-lead` |
| "เขียนโค้ด backend..." | `banking-backend-dev` |
| "ทำหน้า UI..." | `banking-frontend-dev` |
| "ตรวจโค้ดให้หน่อย" | `banking-reviewer` |
| "เช็คความปลอดภัย..." | `banking-security` |
| "เขียน test..." | `banking-qa` |
| "deploy..." | `banking-devops` |
| "ทำ end-to-end Money Transfer" | Chain: ba → sa → tl → backend + frontend → reviewer → security → qa → devops |

---

## 🔗 References

- [CLAUDE.md](../CLAUDE.md) — main orchestrator playbook
- [Workflow + Feedback Loops](architecture/workflow.md)
- [Quality Gates](architecture/quality-gates.md)
- [Definition of Done](architecture/definition-of-done.md)
- [Handoff Schema](architecture/handoff-schema.md)
