# Expected Outputs — View Transaction History Demo

> **For:** Presenter pre-flight check และ post-demo verification
> **What this is:** Map ของ artifact ที่ควรเกิดจริงในแต่ละ act + ตัวชี้วัด "demo สำเร็จ" คือเห็นอะไรบ้าง

---

## Artifact Map (S2-S9 + DoD)

| Step | Agent | Output file | Acceptance |
|---|---|---|---|
| S2 | banking-ba | `S2-ba-view-transaction-history.{json,md}` | ≥ 5 user stories, ≥ 1 AC ต่อ story, NFR table ครบ, open questions flag |
| S3a | banking-solution-architect | `S3-sa-view-transaction-history.{json,md}` | ตัดสินใจ reuse vs new service + ≥ 3 ADRs |
| S3b | banking-designer (P1) | `S3-designer-view-transaction-history.md` | Wireframe (ASCII or link), 5 states (loading/empty/data/error/end-of-list) |
| S3c | banking-qa (P1 shift-left) | `S3-qa-testplan-view-transaction-history.md` | Test plan ≥ 8 scenarios + perf SLA |
| S4 | banking-tech-lead | `S4-tl-view-transaction-history.{md,yaml}` | OpenAPI 3 spec + DB query design + ProblemDetail schema |
| S5a | banking-backend-dev | `backend/transaction-query-service/` | Controller + Service + Spec + tests (`mvn test` green) |
| S5b | banking-frontend-dev | `frontend/src/app/features/transactions/` | Component + Service + tests (`npm test` green) |
| S5c | banking-devops (P1) | `infra/transaction-query/{Dockerfile,helm/}` | Multi-stage Dockerfile + Helm skeleton + Grafana panel JSON |
| S6a | banking-reviewer-be | `S6-reviewer-be-view-transaction-history.md` | Verdict + ≥ 1 finding (N+1 / missing readOnly / IDOR risk) |
| S6b | banking-reviewer-fe | `S6-reviewer-fe-view-transaction-history.md` | Verdict + ≥ 1 finding (trackBy / a11y / unsubscribe) |
| S7 | banking-security | `S7-security-view-transaction-history.md` | STRIDE table + OWASP checklist + verdict |
| S8 | banking-qa (P2) | `S8-qa-view-transaction-history.md` | Test run report (coverage %, pass/fail count) |
| S9 | banking-devops (P2) | `S9-devops-view-transaction-history.md` | Deploy log + smoke test + rollback rehearsal |
| DoD | banking-player | `FINAL-DoD-view-transaction-history.md` | All gates ticked ✅ |

---

## "Demo successful" Visual Checklist

หลัง demo จบ presenter เปิด `docs/scenarios/view-transaction-history/` ควรเห็น:

```
docs/scenarios/view-transaction-history/
├── requirements.md                                    (seed — มีอยู่ก่อน demo)
├── EXPECTED-OUTPUTS.md                                (เอกสารนี้)
├── DEMO-SCRIPT-PRESENTER.md
├── prompts-cheatsheet.md
├── S2-ba-view-transaction-history.md                  (เกิดจาก demo)
├── S3-sa-view-transaction-history.md                  (เกิดจาก demo)
├── S3-designer-view-transaction-history.md            (เกิดจาก demo)
├── S3-qa-testplan-view-transaction-history.md         (เกิดจาก demo)
├── S4-tl-view-transaction-history.md                  (เกิดจาก demo)
├── S4-openapi.yaml                                    (เกิดจาก demo)
├── S6-reviewer-be-view-transaction-history.md         (เกิดจาก demo)
├── S6-reviewer-fe-view-transaction-history.md         (เกิดจาก demo)
├── S7-security-view-transaction-history.md            (เกิดจาก demo)
├── FINAL-DoD-view-transaction-history.md              (สรุปท้ายสุด)
└── _fallback/                                         (ใช้เฉพาะ live ล้ม)
    ├── S5-be-fallback.md
    ├── S5-fe-fallback.md
    └── S6-reviewer-fallback.md
```

---

## Key Metrics ที่ presenter ควรอ้างตอน wrap-up

| Metric | Target | จะดูยังไง |
|---|---|---|
| Total demo time | ≤ 17 min | นาฬิกาจับเวลาบนจอ |
| Live agents fired | 8 | นับจาก act 1-6 |
| Parallel fires | 2 (act 2 + act 4) | คำพูดของ presenter ตอน "magic moment" |
| Reviewer findings | ≥ 1 finding catch | เปิดไฟล์ S6 → ดูว่ามี blocker/major/minor |
| Security STRIDE entries | ≥ 6 categories filled | เปิดไฟล์ S7 |
| Money Transfer comparison | 54 files / 40 tests / 0 critical | quote จาก existing artifacts |

---

## What "good" looks like vs "needs intervention"

### Good signals (continue as planned)
- Agent ตอบใน 60-90 sec ต่อ tool call
- Output มี structure ตาม handoff schema (`artifact_id`, `from_agent`, `to_agent`, `payload`, `metadata`)
- Presenter narration ไหลลื่น (audience nod / ask questions ระหว่าง wait)

### Needs intervention (switch to fallback)
- Agent ใช้เวลา > 2 min ต่อ tool call
- Output ขาด field สำคัญ (no AC / no STRIDE table)
- Error / context window full
- Network drop ระหว่าง agent ทำงาน

### Fallback action
1. หยุดรอ → กล่าว: "งั้นมาดูตัวอย่างที่ผ่านมาแทน เพื่อไม่ให้เสียเวลา"
2. เปิดไฟล์ใน `docs/artifacts/S<N>-...money-transfer.md` ที่ตรงกับ step ที่ stuck
3. Highlight ส่วนสำคัญในเวลา 30 วินาที แล้วข้ามไป act ถัดไป
4. หลัง demo จบ run agent ที่ skip ไปแบบ offline เพื่อให้ artifact tree ครบ
