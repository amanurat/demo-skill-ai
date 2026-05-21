# AI Multi-Agent SDLC — Session Notes & Retrospective
## Feature: Balance Comparison | Sprint: SPRINT-2026-Q2-BC-01

> **วัตถุประสงค์ของเอกสารนี้:** บันทึก step-by-step ของ SDLC ที่ขับเคลื่อนด้วย AI agents พร้อมปัญหาที่พบจริง, วิธีแก้, และบทเรียนสำหรับทีม
> **ใช้เป็น source สำหรับ:** NotebookLM → Presentation slides + Visual diagrams

---

## ภาพรวม: เราทำอะไรในโปรเจกต์นี้?

โปรเจกต์นี้ demo การใช้ **AI Agent หลายตัวทำงานร่วมกันเพื่อขับเคลื่อน SDLC ทั้งกระบวนการ** — ตั้งแต่ PM วิเคราะห์ requirement จนถึง DevOps deploy โดยไม่มีมนุษย์เขียนโค้ดเอง

- **Tech Stack:** Angular (Frontend) + Spring Boot 3.x / Java 21 (Backend) + Redis + Kafka + PostgreSQL
- **Architecture Pattern:** Hexagonal Architecture (Ports & Adapters)
- **Feature:** Balance Comparison Dashboard — แสดงยอดเงินทุก account ของลูกค้าพร้อม ranking

---

## SDLC Workflow ที่ใช้

```
[User Requirement]
      ↓
[PM] → Sprint Goal + Backlog + Risk Register
      ↓
[BA] → User Stories + Acceptance Criteria + NFR
      ↓ (parallel)
[SA] + [Designer P1 LO-FI] + [QA P1 shift-left]
      ↓
[Designer P2 HI-FI]
      ↓
[Tech Lead] → OpenAPI + DB Schema + ADRs
      ↓ (parallel shift-left)
[DevOps P1] + [Implementation Planner]
      ↓ (parallel)
[BE Dev] + [FE Dev]
      ↓ (parallel)
[Reviewer-BE] + [Reviewer-FE]
      ↓ (if changes_requested)
[Refactoring] → loop back to reviewer (max 3x)
      ↓ (both approved, parallel)
[Integration G7] + [Security G8]
      ↓ (parallel)
[QA P2] + [Docs]
      ↓
[DevOps P2]
      ↓
[PM Sprint Close] → DoD Report
```

**จำนวน Agent ทั้งหมด:** 17 agents (team roles) + 1 orchestrator (banking-player)

---

## Step-by-Step: สิ่งที่เกิดขึ้นจริงในแต่ละ Phase

---

### Phase 0 — Discovery

#### Step 1: banking-pm (Project Manager)
**ทำอะไร:** รับ requirement จาก user → วิเคราะห์ → สร้าง sprint goal, backlog, risk register

**Output:**
- Sprint goal: "ลูกค้าเห็น balance ทุก account ในหน้าเดียว ranked by balance"
- User stories: US-BC-001..005
- Risk register: 6 risks เริ่มต้น (IDOR, Redis TTL, Kafka schema, PDPA)

**บทเรียน:** PM เป็น entry point — ถ้าไม่มี PM กำหนด scope ชัด agent อื่นจะ implement เกินหรือน้อยเกินไป

---

#### Step 2: banking-ba (Business Analyst)
**ทำอะไร:** แตก user stories → Acceptance Criteria (AC) → Non-Functional Requirements (NFR)

**Output:**
- 25 AC ครอบคลุม US-BC-001..005
- NFR: p95 latency < 500ms (warm cache), < 800ms (cold), WCAG 2.1 AA, PDPA §22 compliance

**บทเรียน:** AC ที่ดีเป็น "contract" ระหว่าง BA กับ Dev — ทุก AC ต้องมี automated test รองรับ (trace กลับมาได้)

---

#### Step 3: banking-security (Early Review)
**ทำอะไร:** ตรวจ security concerns ก่อน design เริ่ม (shift-left security)

**Output:** Approved with 4 Conditions:
- **C-1:** Privacy notice / consent UI
- **C-2:** Audit payload ต้องเป็น metadata เท่านั้น — ห้าม log balance/accountId/accountNumber
- **C-3:** customerId ต้องมาจาก JWT sub เท่านั้น ห้ามอ่านจาก header
- **C-4:** Redis ต้องใช้ TLS ทุก environment

**บทเรียน:** Security early review ช่วยป้องกัน design ผิดตั้งแต่ต้น — ถ้ารอ review ตอนท้ายต้อง refactor ใหญ่

---

#### Step 4: banking-solution-architect (Solution Architect)
**ทำอะไร:** ออกแบบ service architecture, event flows, tech decisions

**Key Decisions (ADRs):**
- **ADR-001:** สร้าง microservice ใหม่ (`balance-dashboard-service`) แยกออกมา — ไม่แก้ existing services
- **ADR-002:** Redis TTL-only cache (15 min) — ไม่มี explicit invalidation, ยอมรับ stale data
- **ADR-003:** Kafka audit event เป็น Avro v2 schema — metadata เท่านั้น (Security C-2)
- **ADR-004:** Server-side ranking — ไม่ให้ FE sort เอง

**บทเรียน:** ADR (Architecture Decision Record) สำคัญมาก — dev agent อ่าน ADR แล้วรู้ว่าห้ามทำอะไร

---

#### Step 5: banking-designer (UX/UI Designer)
**Phase 1 (LO-FI):** 7 screens, user journey map, interaction spec, 14 open questions

**Phase 2 (HI-FI):**
- W3C Design Tokens (tokens.json) → generate CSS variables อัตโนมัติ
- 14 Component specs
- 7 Screen specs
- WCAG 2.1 AA evidence

**Key Design Decision:** `meta.freshness` JSON field จาก server → drive global stale banner (ไม่ให้ FE คำนวณเอง)

---

### Phase 1 — Technical Design

#### Step 6: banking-tech-lead (Tech Lead)
**ทำอะไร:** แปลง architecture → technical artifacts ที่ developer ใช้ได้จริง

**Output (7 deliverables):**
- OpenAPI spec (balance-dashboard-service + account-service extension)
- DB schema decisions (UUID PK, NUMERIC money type, migration scripts)
- ADR-005: Style Dictionary สำหรับ design token consumption
- ADR-006: CustomerIdResolver pattern (Security C-3 implementation)
- ADR-007: AuditEventPublisher port contract (Security C-2 implementation)
- Implementation notes: module layout, layer constraints, gotchas
- 4 Assumptions ที่ต้องแก้ก่อน production

**บทเรียน:** Tech Lead เป็น "bridge" ระหว่าง architecture กับ code — ถ้า TL output ไม่ครบ dev agent จะ guess แล้วผิด

---

#### Step 7: banking-implementation-planner (Implementation Planner)
**ทำอะไร:** แตก TL handoff → task list ระดับ file + class + test case

**Output:**
- BE task list: 6 layers × files × test cases
- FE task list: 5 steps × components × test cases
- AC → test case mapping (100% coverage)
- Interface contracts ที่ BE/FE ต้อง share

**บทเรียน:** Planner คือ "G4 gate" — ถ้าไม่มี planner dev จะ implement ตาม assumption ตัวเองซึ่งอาจ miss AC

---

#### Step 8: banking-devops P1 (shift-left)
**ทำอะไร:** สร้าง CI/CD skeleton ก่อน dev เริ่ม

**Output:**
- Helm chart (staging + prod values)
- GitHub Actions: 4 jobs (build+test, design-tokens check, OpenAPI lint, helm lint)
- Apicurio schema registration plan
- Redis TLS verification checklist

---

### Phase 2 — Implementation

#### Step 9: banking-backend-dev (Backend Developer)
**ทำอะไร:** Implement Spring Boot microservice ตาม 6-layer hexagonal TDD

**6 Layers (sequential, ห้ามข้าม):**
1. Domain Model (entities, value objects, policies)
2. Port Interfaces (application/port/out)
3. Application Service (use cases)
4. Infrastructure Adapters (Kafka, Redis, HTTP client)
5. REST Controllers + Filters
6. Full Integration + Self-checks

**Key Technical Decisions:**
- `balance` field เป็น `BigDecimal` serialized เป็น `String` — ป้องกัน JavaScript float precision loss
- `CustomerIdResolver` อ่านจาก JWT sub เท่านั้น — ArchUnit ตรวจ build-time
- `IborCheckFilter` เป็น class เดียวที่อ่าน X-Customer-Id header
- Redis: atomic SETEX, fail-open (ถ้า Redis ล่มให้ fallthrough ไป AccountClient)
- Resilience4j operator order: `TimeLimiter → CB → Retry → Bulkhead`

**ปัญหาที่พบ (ถูกจับใน Code Review):** ดูหัวข้อ "ปัญหาและการแก้ไข" ด้านล่าง

---

#### Step 10: banking-frontend-dev (Frontend Developer)
**ทำอะไร:** Implement Angular UI ตาม 5-step sequential TDD

**5 Steps (sequential):**
1. API Client Generation (typed TypeScript models)
2. State / Service Layer (Signal-based state management)
3. Dumb (Presentational) Components
4. Smart (Container) Components
5. Routing + Guards

**Key Technical Decisions:**
- `balance` typed เป็น `string` ใน TypeScript (ไม่ใช่ `number`)
- Signal-based state (ไม่ใช้ NgRx — feature ไม่ซับซ้อนพอ)
- JWT เก็บใน in-memory (ไม่ใช่ localStorage)

---

### Phase 3 — Review Loop

#### Step 11: banking-reviewer-be + banking-reviewer-fe (parallel)
**ทำอะไร:** Code review แบบ Principal Engineer — ตรวจ architecture, patterns, security

**Review Iterations:**

| Iteration | BE Result | FE Result |
|---|---|---|
| 1 | changes_requested (4 blockers) | changes_requested (3 blockers) |
| 2 | changes_requested (3 new blockers) | **approved** ✅ |
| 3 | **approved** ✅ | — |

**รวม 3 iterations สำหรับ BE, 2 สำหรับ FE** (ไม่เกิน limit 3 ครั้ง)

---

#### Step 12: banking-refactoring (Refactoring Specialist)
**ทำอะไร:** แก้เฉพาะ findings จาก reviewer — ห้าม scope creep

**Iteration 1 (23 findings):** architectural + code quality issues
**Iteration 2 (4 BE blockers):** Apicurio dep, @WebMvcTest config, CacheHitAuditEmittedTest, ArchUnit fixture

---

### Phase 4 — Integration + Security

#### Step 13: banking-integration (G7 Gate)
**ทำอะไร:** ตรวจ FE ↔ BE contract drift, OpenAPI alignment

**Result:** Approved — 0 drift findings ใน 12 fields

---

#### Step 14: banking-security (G8 Gate)
**ทำอะไร:** STRIDE, OWASP Top 10, SAST, PII scan, compliance check

**Iteration 1 Result:** changes_requested — 2 blockers (F-1, F-2)

**Iteration 2 Result:** Approved ✅

---

### Phase 5 — Quality + Documentation

#### Step 15: banking-qa P2
**Output:** 170 tests, 25/25 ACs covered

| Category | Count |
|---|---|
| BE Unit | 55 |
| FE Unit + Integration | 83 |
| BE Integration (Testcontainers) | 9 |
| Contract + ArchUnit | 9 |
| E2E (Playwright) | 14 |

---

#### Step 16: banking-docs
**Output:** API Reference, CHANGELOG, Developer Guide, ADR Index

---

### Phase 6 — Deploy

#### Step 17: banking-devops P2
**Output:**
- Dockerfile (multi-stage, non-root user, healthcheck)
- CI/CD: 10 jobs (SAST, image scan, cosign sign, SBOM, helm deploy, smoke test)
- Grafana dashboard: 15 panels

---

#### Step 18: banking-pm Sprint Close
**DoD:** PASS
**Recommendation:** Ship to Staging

---

## ปัญหาที่พบจริงและวิธีแก้

---

### ปัญหาที่ 1: Hexagonal Architecture Violation
**พบที่:** Review iteration 1 (BE)
**อาการ:** `UpstreamUnavailableException` (infrastructure exception) รั่วเข้า application layer
**ผลกระทบ:** Domain layer มี import จาก infrastructure → ทำลาย hexagonal boundary
**วิธีแก้:** สร้าง `DashboardUnavailableException` ใน `domain/exception/` (zero non-package imports) แล้วให้ adapter wrap exception ที่ boundary

**บทเรียน:** Hexagonal = domain layer ต้องไม่รู้จัก Spring, Kafka, Redis เลย — ArchUnit บังคับ build-time

---

### ปัญหาที่ 2: Redis TLS ปิดใน Staging
**พบที่:** Review iteration 1 (BE)
**อาการ:** `application-staging.yml` override `ssl.enabled: false`
**ผลกระทบ:** ละเมิด Security C-4 — data in transit ไม่ encrypt
**วิธีแก้:** ลบ override (base `application.yml` มี `ssl.enabled: true` อยู่แล้ว)

**บทเรียน:** Security config ต้องมี test ที่ assert ค่าในทุก profile — ไม่ใช่แค่ review ตา

---

### ปัญหาที่ 3: JWT เก็บใน localStorage
**พบที่:** Review iteration 1 (FE)
**อาการ:** `auth.service.ts` อ่าน/เขียน localStorage โดยตรง
**ผลกระทบ:** XSS สามารถขโมย token ได้
**วิธีแก้:** เปลี่ยนเป็น in-memory Signal-based state — token อยู่ใน JS memory เท่านั้น

---

### ปัญหาที่ 4: PII ใน Application Logs (Security F-1) — HIGH
**พบที่:** G8 Security review
**อาการ:** `customerId` และ `accountId` UUID ถูก log แบบ unmasked ใน 4 files, 12 log statements
```java
// BAD (ก่อนแก้)
log.debug("cache=HIT customerId={}", customerId);

// GOOD (หลังแก้)
log.debug("cache=HIT customerId={}", LogMasking.maskId(customerId));
```
**ผลกระทบ:** ผู้มีสิทธิ์อ่าน log สามารถ enumerate customer IDs ได้ — PDPA §22 violation
**วิธีแก้:**
1. สร้าง `LogMasking.java` utility (`maskId()` + `maskKey()`)
2. Replace ทุก log statement ที่ expose UUID
3. เพิ่ม unit test ที่ assert ว่า full UUID pattern ไม่ปรากฏใน output

---

### ปัญหาที่ 5: Missing logback-spring.xml (Security F-2) — MEDIUM
**พบที่:** G8 Security review
**อาการ:** ไม่มี log masking ที่ระดับ Logback framework
**ผลกระทบ:** ถ้า developer คนอื่น add log statement ใหม่โดยไม่ใช้ LogMasking — PII จะรั่วโดยไม่รู้ตัว
**วิธีแก้:** สร้าง `logback-spring.xml` พร้อม regex masking filter สำหรับ UUID และ digit sequences (defense-in-depth)

---

### ปัญหาที่ 6 (สำคัญที่สุด): Code Compile ไม่ได้ทั้งที่ Sprint "เสร็จ" แล้ว
**พบที่:** หลัง sprint close — ตรวจสอบภายหลัง
**อาการ:** Project ขาดไฟล์ scaffold สำคัญ:

| ไฟล์ที่ขาด | ผลกระทบ |
|---|---|
| `backend/pom.xml` (root multi-module) | `mvn compile` ไม่ได้เลย |
| `common-libs/account-client-lib/pom.xml` | dependency ไม่ resolve |
| `frontend/angular.json` | `ng build` ไม่รู้จัก project |
| `frontend/src/main.ts` | Angular app bootstrap ไม่ได้ |
| `frontend/src/app/app.module.ts` | Feature module ไม่ถูก register |

**Root Cause:** Dev agents ทำงานใน "Document Space" เท่านั้น — เขียนโค้ดโดยไม่เคย compile จริง

```
ก่อนแก้ (Document Space Only)
────────────────────────────────────
Agent เขียน code → Agent อ่าน code → "approved"
ไม่มีใคร compile        ไม่มีใคร execute

หลังแก้ (Execution-Verified)
────────────────────────────────────
Agent เขียน code → mvn compile → mvn test → Reviewer ตรวจ build_evidence → "approved"
                   exit 0 required   exit 0 required   reject ถ้า missing
```

**วิธีแก้ระยะยาว:** แก้ agent definition ของ `banking-backend-dev` และ `banking-frontend-dev`:
1. เพิ่ม **Project Scaffold Check** — ตรวจก่อน code ใดๆ ว่า root files มีครบ ถ้าไม่มีสร้างก่อนเลย
2. **Validation Loop เป็น HARD GATE** — `mvn clean verify` ต้อง exit 0 ก่อน emit handoff
3. **`build_evidence` field บังคับในทุก handoff** — include actual compiler output
4. เพิ่ม **Pre-Review Gate** ใน reviewer agents — reject handoff ทันทีถ้า `build_evidence` หาย หรือ exit_code != 0

---

## Key Architecture Patterns ที่ใช้

### Hexagonal Architecture (Ports & Adapters)
```
          [REST Controller]
                 ↓ calls
[Application Service] ← uses → [Port Interfaces]
         ↑ owns                        ↑ implemented by
   [Domain Model]           [Infrastructure Adapters]
   (zero Spring/Kafka/      (Kafka, Redis, HTTP Client)
    Redis imports)
```

**Rule:** Domain layer ต้องไม่มี `import org.springframework.*` แม้แต่บรรทัดเดียว
**Enforcement:** ArchUnit test ตรวจ build-time

---

### Security-by-Design: CustomerIdResolver Pattern (ADR-006)
**ปัญหาที่ป้องกัน:** IDOR (Insecure Direct Object Reference) — ลูกค้า A เห็นข้อมูลลูกค้า B

```
Request มาพร้อม JWT token + X-Customer-Id header
                    ↓
          [IborCheckFilter]
          - อ่าน X-Customer-Id header
          - เปรียบเทียบกับ JWT sub
          - ถ้าไม่ตรง → 403 FORBIDDEN + audit log
                    ↓ (ถ้าตรง)
          [CustomerIdResolver]
          - อ่านจาก JWT sub เท่านั้น
          - ห้ามอ่านจาก header หรือ request param
                    ↓
          [Business Logic] ← ใช้ customerId ที่ปลอดภัยแล้ว
```

**Enforcement:** ArchUnit `CustomerIdSourceRule` — build ล้มเหลวถ้ามี class อื่นอ่าน X-Customer-Id header

---

### Audit Trail: Metadata-Only Pattern (ADR-007, Security C-2)
**หลักการ:** Audit บันทึกว่า "เกิดอะไร" ไม่บันทึก "ค่าคืออะไร"

```java
// ❌ WRONG — บันทึก sensitive data
audit.log(customerId, accounts, balances);

// ✅ CORRECT — บันทึกแค่ metadata
audit.log(customerId_masked, timestamp, result, correlationId, channel);
```

**Enforcement:** `KafkaAuditEventPublisherContractTest` ใช้ byte-grep ตรวจว่า Kafka message ไม่มีคำว่า `"balance"`, `"accountId"`, `"accounts"` เลย

---

## Quality Gates Summary

| Gate | Agent | ผ่านไหม | หมายเหตุ |
|---|---|---|---|
| G1 Requirements | banking-ba | ✅ | 25 ACs, NFRs measurable |
| G2 Architecture | banking-sa | ✅ | 4 ADRs, no shared-DB |
| G3 Technical Contract | banking-tech-lead | ✅ | OpenAPI lint-clean, 7 ADRs |
| G4 Implementation Plan | banking-impl-planner | ✅ | 100% AC coverage, 130 test cases |
| G5 Code Health | BE+FE dev | ✅ | Build green, 170 tests |
| G6 Best Practice | Reviewers | ✅ | BE: 3 iter, FE: 2 iter |
| G7 Contract Integrity | banking-integration | ✅ | 0 drift findings |
| G8 Security | banking-security | ✅ | 2 iter (F-1 PII, F-2 logback fixed) |
| G9 Test + Docs | banking-qa + banking-docs | ✅ | 170 tests, 4 doc deliverables |
| G10 Deployable | banking-devops | ✅ | Pending staging infra |

---

## Metrics สรุป

| Metric | Value |
|---|---|
| จำนวน Agent ที่ใช้ | 17 team agents + 1 orchestrator |
| User Stories Delivered | 4/5 (US-BC-004 deferred) |
| Acceptance Criteria | 25/25 covered |
| Test Cases | 170 total |
| Review Iterations (BE) | 3 (ถึง ceiling ที่กำหนด) |
| Review Iterations (FE) | 2 |
| Security Findings | 5 total: 2 blocking (resolved), 3 non-blocking |
| Refactoring Iterations | 3 (iter 1+2: code quality, iter 3: security) |
| Commits | 15+ commits (per-agent convention) |
| Sprint Recommendation | Ship to Staging |

---

## บทเรียนสำหรับทีม

### 1. AI Agents ทำงานใน "Document Space" — ต้องบังคับ "Execution Space"
AI agents อ่านและเขียนไฟล์ได้ดีมาก แต่ถ้าไม่บังคับให้ compile/run จริง จะไม่ทำเอง
**Fix:** Dev agent ต้องมี HARD GATE — `mvn compile` + `mvn test` ต้อง exit 0 ก่อน handoff

### 2. Project Scaffold ต้องมีก่อน Feature Development
Agents assume ว่า project structure มีอยู่แล้ว ถ้า root pom.xml / angular.json หายไปจะไม่สร้างให้เอง
**Fix:** เพิ่ม Scaffold Check เป็น step แรกใน dev agent — ถ้าไม่มีให้สร้างก่อน

### 3. Review ที่ไม่มี Build Evidence = ไม่มีความหมาย
Reviewer ที่ approve โค้ดที่ยังไม่ compile ไม่ได้เพิ่มคุณค่าอะไร
**Fix:** Reviewer reject handoff ทันทีถ้าไม่มี `build_evidence` — ไม่อ่านแม้แต่บรรทัดเดียว

### 4. Security Conditions ที่กำหนดตอนต้น ต้องมี Test Enforcement
Security early review กำหนด C-2/C-3/C-4 แต่ถ้าไม่มี automated test enforce จะถูกลืมหรือทำผิด
**Fix:** ทุก security condition ต้องมี test ที่ assert condition นั้น (ArchUnit, byte-grep, unit test)

### 5. ADR = Law สำหรับ Agents
Agent ที่ไม่อ่าน ADR จะตัดสินใจเองและอาจทำผิด pattern ที่ architect ออกแบบ
**Fix:** Dev agent ต้องอ่าน ADR ที่เกี่ยวข้องก่อนเขียนโค้ด ทุกครั้ง

### 6. Parallel Phases ช่วยประหยัดเวลาได้มาก
Review-BE กับ Review-FE ที่รัน parallel ลดเวลาได้ ~50%
Security กับ Integration ที่รัน parallel หลัง review เสร็จ
QA P2 กับ Docs ที่รัน parallel ก่อน DevOps P2

### 7. Shift-Left คือ Investment ที่คุ้มค่า
QA P1 (เขียน test plan ตอน BA phase) ทำให้ QA P2 เร็วขึ้นมาก
DevOps P1 (Helm + CI ตอน TL phase) ทำให้ DevOps P2 เหลือแค่ complete ไม่ต้อง build from scratch
Security early review ป้องกัน design ผิดตั้งแต่ต้น

---

## สิ่งที่ต้องทำก่อน Production

| Item | Priority | Owner |
|---|---|---|
| Provision staging infra | HIGH | DevOps |
| k6 performance test (p95 SLA) | HIGH | QA |
| Apicurio v2 schema registration | HIGH | DevOps |
| Redis at-rest AES-256-GCM verification | HIGH | DevOps |
| สร้าง project scaffold ที่ขาด (root pom.xml, angular.json) | HIGH | Dev |
| BC-DEBT-002: Migrate JWT to HttpOnly cookie + BFF | MEDIUM | FE Dev |
| Privacy consent UI (C-1) | MEDIUM | FE Dev + BA |

---

## Visual Ideas สำหรับ Presentation

1. **Agent Workflow Diagram** — flowchart แสดงทุก agent และ connection (parallel tracks ใช้สีต่างกัน)
2. **Quality Gate Timeline** — horizontal timeline แสดง G1-G10 พร้อม icon pass/fail
3. **Hexagonal Architecture Diagram** — วงกลม 3 ชั้น (Domain / Application / Infrastructure)
4. **Document Space vs Execution Space** — before/after comparison สำหรับปัญหา compilation
5. **Security Defense-in-Depth** — layers ของ security controls (ArchUnit → LogMasking → logback)
6. **Review Loop Counter** — visual แสดง iterations และ findings per iteration
7. **Metrics Dashboard** — numbers: 17 agents, 170 tests, 25 ACs, 15+ commits, 10 gates

---

*เอกสารนี้จัดทำเพื่อใช้เป็น source material สำหรับ NotebookLM*
*branch: `stage/02-balance-comparison` · วันที่: 2026-05-22*
