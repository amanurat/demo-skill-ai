# Sample Banking Project — AI Multi-Agent System

> **Mission:** Demonstrate end-to-end SDLC automation using AI agents for a banking application (Money Transfer feature) built with Angular + Spring Boot Microservices.

---

## 🧭 How to Use This Project

You (main Claude) are the **Player / Orchestrator**. When the user gives a banking requirement or task:

1. **Read** the request → identify which SDLC phase it touches (Discovery / Planning / Design / Dev / Test / Deploy).
2. **Plan** the chain of agents needed.
3. **Delegate** to specialized agents via the **Task tool** with `subagent_type: banking-<role>`.
4. **Validate** each handoff artifact against the schema in [docs/architecture/handoff-schema.md](docs/architecture/handoff-schema.md).
5. **Manage feedback loops** per [docs/architecture/workflow.md](docs/architecture/workflow.md) — max 3 retries per loop, then escalate to user.
6. **Track quality gates** per phase from [docs/architecture/quality-gates.md](docs/architecture/quality-gates.md).
7. **Confirm DoD** per [docs/architecture/definition-of-done.md](docs/architecture/definition-of-done.md) before marking work complete.

---

## 🏗 Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Angular (latest LTS) |
| Backend | Java 21 + Spring Boot 3.x Microservices |
| Backend layout | **Monorepo, multi-module Maven** |
| Messaging | Kafka |
| Database | PostgreSQL (per service) |
| Cache | Redis |
| Containers | Docker + Kubernetes |
| Observability | OpenTelemetry, Prometheus, Grafana |
| Auth | OAuth2 / OIDC + JWT |

---

## 🤖 Agent Registry

All agents live under [.claude/agents/](.claude/agents/). Trigger them via Task tool.

| # | `subagent_type` | Role | SPMC Role | When to invoke |
|---|---|---|---|---|
| 0 | `banking-player` | Orchestrator (self-doc) | ITPM/Squad Lead | (You are this — reference for your own playbook) |
| 1 | [`banking-ba`](.claude/agents/banking-ba.md) | Business Analyst | Business Analyst | Raw requirement → user stories + AC + NFRs |
| 2 | [`banking-designer`](.claude/agents/banking-designer.md) | UX/UI Designer | Designer | Phase 1: LO-FI after BA · Phase 2: HI-FI after SA (parallel with SA) |
| 3 | [`banking-solution-architect`](.claude/agents/banking-solution-architect.md) | Solution Architect | Solution Architect | User stories → service map + tech decisions (parallel with Designer) |
| 4 | [`banking-tech-lead`](.claude/agents/banking-tech-lead.md) | Tech Lead | Technical Lead | Architecture + HI-FI design → OpenAPI + DB schema + ADRs |
| 5 | [`banking-frontend-dev`](.claude/agents/banking-frontend-dev.md) | Angular Dev | Developer | API contract + design spec → UI implementation |
| 6 | [`banking-backend-dev`](.claude/agents/banking-backend-dev.md) | Spring Boot Dev | Developer | API contract → microservice implementation |
| 7 | [`banking-reviewer-fe`](.claude/agents/banking-reviewer-fe.md) | Frontend Reviewer | Technical Lead | Angular artifacts → parallel review with BE reviewer |
| 8 | [`banking-reviewer-be`](.claude/agents/banking-reviewer-be.md) | Backend Reviewer | Technical Lead | Spring Boot artifacts → parallel review with FE reviewer |
| 9 | [`banking-security`](.claude/agents/banking-security.md) | AppSec / Compliance | DevSecOps | After both reviewers approve → security review + audit |
| 10 | [`banking-qa`](.claude/agents/banking-qa.md) | QA Automation | TQA | Phase 1 (shift-left): test plan after BA · Phase 2: automation after security |
| 11 | [`banking-devops`](.claude/agents/banking-devops.md) | DevOps | DevSecOps | Phase 1 (shift-left): CI/CD skeleton after TL · Phase 2: full deploy after QA |
| 12 | [`banking-reviewer`](.claude/agents/banking-reviewer.md) | Principal Engineer (generic) | Technical Lead | Single-stack PRs or when FE+BE split is not needed |

---

## 🔄 Optimized Forward Flow (Parallel + Shift-Left)

```
User requirement
    ↓
[banking-ba] → user stories, AC
    ↓               ↓                    ↘ shift-left
[banking-sa]  [banking-designer P1]    [banking-qa P1] — test plan
    ↓               ↓
[banking-designer P2 — HI-FI]
    ↓
[banking-tech-lead] → OpenAPI + DB schema + design tokens
    ↓                    ↓                   ↘ shift-left
[banking-frontend-dev]  [banking-backend-dev] [banking-devops P1] — CI/CD skeleton
    ↓                            ↓
[banking-reviewer-fe]  +  [banking-reviewer-be]   (parallel)
    ↓                            ↓
              [banking-security]
                       ↓
              [banking-qa P2] — full automation
                       ↓
              [banking-devops P2] — full deploy
                       ↓
                     ✅ DoD met
                       ↓
                     ✅ DoD met
```

See [docs/architecture/workflow.md](docs/architecture/workflow.md) for feedback loops and escalation rules.

---

## 📐 Standards

- **Handoff Artifact:** Every agent emits a JSON artifact matching [docs/architecture/handoff-schema.md](docs/architecture/handoff-schema.md).
- **Quality Gates:** Pass criteria per phase in [docs/architecture/quality-gates.md](docs/architecture/quality-gates.md).
- **Definition of Done:** [docs/architecture/definition-of-done.md](docs/architecture/definition-of-done.md).
- **Project Structure (future code):** [docs/architecture/project-structure.md](docs/architecture/project-structure.md).
- **System Overview:** [docs/architecture/overview.md](docs/architecture/overview.md).

---

## 🗣 Language Convention

- **Narrative / explanations** → Thai (professional tone)
- **Code, schemas, identifiers, file contents** → English (mandatory)

---

## 🚦 Quick Start (for the user)

> **📘 Full step-by-step guide:** [docs/playbook.md](docs/playbook.md) — interactive checklist สำหรับทุก session (Discovery → Deploy) พร้อมตัวอย่างคำสั่ง + git commit templates

ตัวอย่างคำสั่งที่ใช้ได้ทันที:

```
"ลองวิเคราะห์ Money Transfer feature แบบ end-to-end"
"BA agent ช่วยแตก user stories จาก requirement นี้: ..."
"Backend dev ช่วย scaffold transfer-service ตาม API contract"
"Reviewer ช่วยตรวจโค้ดที่เพิ่ง implement"
```

Player จะอ่านคำสั่ง → เลือก agent → Task tool launch → รวบ artifact → ส่งต่อตาม workflow

---

## 📚 Source Prompts (Journey)

โครงการนี้พัฒนา prompt มาแล้ว 3 รอบ — ดูที่:
- [prompts/requirement.md](prompts/requirement.md) — raw input
- [prompts/claude_prompt.md](prompts/claude_prompt.md), [prompts/gemini_prompt.md](prompts/gemini_prompt.md), [prompts/codex_prompt.md](prompts/codex_prompt.md) — 3 versions แยกกัน
- [prompts/final_prompt.md](prompts/final_prompt.md) — **best-of-breed v3** (source of truth)

---

## ⚠️ Out-of-Scope (current session)

- ไม่มี source code Angular / Spring Boot (จะถูก generate โดย agents ใน future sessions)
- ไม่มี Dockerfile / CI/CD pipeline
- ไม่มี git repo

ทั้งหมดนี้คือผลผลิตของ agents — agent definitions ที่นี่คือ "instructions" ให้พวกเขาทำงานนั้น
