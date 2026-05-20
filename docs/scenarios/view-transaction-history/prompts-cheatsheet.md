# Prompts Cheat Sheet — View Transaction History Demo

> **Usage:** เปิดไฟล์นี้บนจอข้างๆ Claude Code terminal → กด Cmd+C copy ทั้งบล็อก → Cmd+V paste ลง terminal → Enter
> **อย่าพิมพ์เอง** — เสียเวลา + พิมพ์ผิด

---

## ⏱ Act 1 [2:00] — BA via Player

```text
Player, ใช้ banking-ba วิเคราะห์ feature นี้:

Feature: View Transaction History (Read-only)
อ่าน seed file: docs/scenarios/view-transaction-history/requirements.md

ขอ artifact ที่:
- docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md
- docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.json

Requirements:
- ≥ 5 INVEST user stories (format US-VTH-XXX)
- AC แบบ Given/When/Then ≥ 2 scenario ต่อ story (happy + edge)
- NFR table: latency, throughput, availability, retention, a11y, i18n
- Open questions section พร้อม flag ระดับ (PO / Compliance / Tech)
- ใช้ handoff schema ตาม docs/architecture/handoff-schema.md
```

---

## ⏱ Act 2 [4:00] — Shift-Left Trio (PARALLEL)

```text
Player, ยิง 3 Task tool calls ใน 1 message เดียว (parallel):

1. banking-solution-architect
   - Input: docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md
   - Output: docs/scenarios/view-transaction-history/S3-sa-view-transaction-history.md
   - Task: ตัดสินใจ reuse existing service vs สร้าง transaction-query-service ใหม่
           + Kafka event (ถ้ามี) + ADR ≥ 3 ข้อ + service map
   - Skill: banking platform patterns

2. banking-designer (Phase 1 — LO-FI)
   - Input: docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md
   - Output: docs/scenarios/view-transaction-history/S3-designer-view-transaction-history.md
   - Task: Wireframe สำหรับ list view + filter bar + pagination
           5 states: loading, empty, data, error, end-of-list
           Mobile responsiveness note

3. banking-qa (Phase 1 — shift-left test plan)
   - Input: docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md
   - Output: docs/scenarios/view-transaction-history/S3-qa-testplan-view-transaction-history.md
   - Task: Test plan ≥ 8 scenarios — happy, empty, date edge, 1M rows, IDOR, timeout, pagination boundary, sort
           Perf SLA mapping
   - Skill: banking-test-automation
```

---

## ⏱ Act 3 [6:30] — Tech Lead

```text
Player, ใช้ banking-tech-lead:

Input ที่ต้องอ่าน:
- docs/scenarios/view-transaction-history/S2-ba-view-transaction-history.md
- docs/scenarios/view-transaction-history/S3-sa-view-transaction-history.md
- docs/scenarios/view-transaction-history/S3-designer-view-transaction-history.md
- docs/scenarios/view-transaction-history/S3-qa-testplan-view-transaction-history.md

Output:
- docs/scenarios/view-transaction-history/S4-openapi.yaml (OpenAPI 3 spec)
- docs/scenarios/view-transaction-history/S4-tl-view-transaction-history.md (DB design + ADR)

Requirements:
- GET /api/v1/transactions with query params: from, to, type, page, size
- Response schema: Page<TransactionDto> with masked counterparty
- ProblemDetail (RFC 7807) for 400 / 401 / 403 / 429
- DB query design: JPA Specification + composite index (customer_id, executed_at DESC)
- Pagination: page (0-based), size (max 100)
- ADR: read replica vs main DB

Skill: openapi-flyway-standards
```

---

## ⏱ Act 4 [8:00] — THE PARALLEL FIRE (BE + FE + DevOps)

```text
Player, ยิง 3 Task tool calls ใน 1 message (parallel) — นี่คือจุดที่ shift-left + parallel power ออกฤทธิ์:

1. banking-backend-dev
   - Read: docs/scenarios/view-transaction-history/S4-openapi.yaml + S4-tl-view-transaction-history.md
   - Output: backend/transaction-query-service/ (new module หรือ extend transfer-service ตาม SA)
   - Deliver:
     * Controller + Service + Repository (JPA Specification)
     * @Transactional(readOnly = true) บน query method
     * Masking utility สำหรับ counterparty account
     * ProblemDetail handler + i18n error messages
     * Customer ID extracted from JWT (NOT query param)
     * JUnit 5 unit tests ≥ 8 + Testcontainers integration test
   - Skill: spring-boot-banking + resilience4j-patterns

2. banking-frontend-dev
   - Read: docs/scenarios/view-transaction-history/S3-designer-view-transaction-history.md + S4-openapi.yaml
   - Output: frontend/src/app/features/transactions/
   - Deliver:
     * Standalone TransactionListComponent + TransactionFilterComponent
     * Signal-based state + Reactive Form for filter
     * Typed HttpClient service (จาก openapi-generator)
     * Loading skeleton, empty state, error state, end-of-list state
     * trackBy ใน *ngFor (required)
     * WCAG 2.1 AA — aria-label, focus management, contrast
     * i18n th/en
     * Jest unit tests
   - Skill: angular-banking-ui

3. banking-devops (Phase 1 — shift-left CI/CD skeleton)
   - Output: infra/transaction-query/
   - Deliver:
     * Dockerfile (multi-stage, distroless, non-root user, healthcheck)
     * helm/Chart.yaml + helm/values.yaml + helm/templates/ (Deployment, Service, ServiceMonitor)
     * Grafana dashboard panel: transaction_query_duration_seconds (p50/p95/p99)
     * Prometheus alert rule: p95 > 500ms for 5 min
     * GitHub Actions workflow skeleton (lint → test → build → push)
   - Skill: banking-devops-platform
```

---

## ⏱ Act 5 [11:00] — Reviewers (PARALLEL)

```text
Player, ยิง 2 Task tool calls ใน 1 message (parallel):

1. banking-reviewer-be
   - Review: backend/transaction-query-service/
   - Output: docs/scenarios/view-transaction-history/S6-reviewer-be-view-transaction-history.md
   - Checklist:
     * Domain model not anemic
     * @Transactional(readOnly=true) on query
     * No N+1 (check fetch strategy)
     * Customer ID from JWT (no IDOR)
     * No PII in logs
     * Pagination bounded
     * Test coverage ≥ 80%
   - Skill: code-review-checklists

2. banking-reviewer-fe
   - Review: frontend/src/app/features/transactions/
   - Output: docs/scenarios/view-transaction-history/S6-reviewer-fe-view-transaction-history.md
   - Checklist:
     * trackBy in *ngFor
     * Subscriptions cleaned up (takeUntilDestroyed or async pipe)
     * No [innerHTML] for user data (XSS)
     * a11y: aria-label, focus, contrast
     * No 'any' type abuse
     * i18n keys instead of hardcoded strings
   - Skill: code-review-checklists
```

---

## ⏱ Act 6 [13:30] — Security

```text
Player, ใช้ banking-security:

Input ที่ต้อง review:
- docs/scenarios/view-transaction-history/S4-openapi.yaml
- backend/transaction-query-service/
- S6 reviewer outputs

Output: docs/scenarios/view-transaction-history/S7-security-view-transaction-history.md

Required sections:
1. STRIDE Threat Model (6 categories) สำหรับ GET /api/v1/transactions
2. OWASP Top 10 banking lens — โดยเฉพาะ:
   - A01 Broken Access Control (IDOR — customer_id source)
   - A03 Injection (date parsing)
   - A04 Insecure Design
   - A09 Logging — PII leakage
3. PDPA compliance: data masking, audit log of "view" event, retention
4. Banking hard rules check:
   - No hardcoded secrets
   - JWT signature validation
   - Rate limit present
   - Audit trail emit
5. Verdict: approved / changes_requested
   - Critical: 0 (must)
   - High: 0 (must)
   - Medium: list
   - Low: list

Skill: banking-security-patterns
```

---

## ⏱ Act 7 [15:00] — DoD Walkthrough (Manual)

ไม่ต้อง prompt — เปิดไฟล์ pre-rendered:

```text
# Open these in tabs:
docs/artifacts/S8-qa-money-transfer.md          # QA Phase 2 reference shape
docs/artifacts/S9-devops-money-transfer.md      # DevOps Phase 2 reference shape
docs/artifacts/FINAL-DOD-money-transfer-v1.md   # DoD checklist (14 items)
```

พูด narration ตาม DEMO-SCRIPT-PRESENTER.md Act 7

---

## 🔧 Optional — Generate Final DoD (หลัง demo ถ้ามีเวลา)

```text
Player, ใช้ banking-player สรุป Definition of Done สำหรับ feature นี้:

Input: ทุก artifact ใน docs/scenarios/view-transaction-history/ (S2-S7)
Output: docs/scenarios/view-transaction-history/FINAL-DoD-view-transaction-history.md

Sections:
- Functional gates (AC ครบ, contract match)
- Quality gates (coverage, lint, build)
- Security gates (0 critical/high)
- Performance gates (p95 < 500ms ที่ load test)
- Operational gates (Helm + Grafana + alert + runbook)
- Compliance gates (PDPA, audit, retention)

Verdict สุดท้าย: ready-to-deploy / needs-rework
```

---

## 🚨 Emergency Fallback Prompt

ถ้า agent ค้าง / crash / error — ใช้ prompt นี้เพื่อ recover:

```text
Player, agent ก่อนหน้า [agent-name] ล้มเหลว / ตอบไม่ครบ
ขอ skip step นี้ ใช้ pre-baked fallback แทน:

Source: docs/scenarios/view-transaction-history/_fallback/[file].md

ขอให้:
1. Copy เนื้อหา fallback ไปวางที่ output path ที่คาดหวัง
2. Resume workflow จาก step ถัดไป
3. Log skip event ใน timeline
```

---

## 🎯 Speed Tips

| สถานการณ์ | ทำอะไร |
|---|---|
| Agent หาไฟล์ไม่เจอ | Add `(absolute path: /Users/Assanai.M/mystuff/projects/demo-skill-ai/docs/...)` ลง prompt |
| Output ไม่มี JSON envelope | Append: "ต้อง output ตาม handoff-schema.md ด้วย JSON envelope" |
| Output ยาวเกินไป | Append: "เน้น content สำคัญ ตัด boilerplate" |
| Agent ถามคำถามกลับ | ตอบสั้น "ใช้ default ตาม requirements.md" หรือ "skip — ใส่ TODO comment" |
| ต้อง interrupt | ESC → กลับมาที่ Act ปัจจุบัน |
