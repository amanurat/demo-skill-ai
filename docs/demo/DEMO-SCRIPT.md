# Demo Script — "Agentic AI in the SDLC" (8–10 minutes)

> **Audience:** Executives, customers, or technical managers who want to understand what multi-agent AI can do in enterprise software development.
> **Format:** Screen share + live terminal (or walkthrough of pre-run artifacts)
> **Goal:** Show that AI agents can replace the human handoffs across a full SDLC — not just code, but requirements, architecture, security review, QA, and DevOps.

---

## Setup (Before You Start)

1. Have this repo open in VS Code or a terminal
2. Browser tabs ready:
   - `docs/demo/index.html` (HTML dashboard)
   - `docs/agents-comms/dashboard.md` (phase board)
   - `docs/agents-comms/timeline.md` (handoff log)
   - Any one artifact pair, e.g. `docs/artifacts/S5-backend-dev-money-transfer.md`
3. Terminal in `backend/transfer-service/` ready to run `mvn test`

---

## Minute 0-1 — The Problem Setup

**Say:**
> "Traditional software development has 8-10 handoffs between roles — BA, architect, tech lead, developer, reviewer, security engineer, QA, DevOps. Each handoff takes days. Knowledge gets lost. Quality gates are inconsistent.
>
> What if every one of those roles was an AI agent, and they handed off to each other automatically — with structured contracts, quality gates, and feedback loops — just like a real engineering team?"

**Show:** The [README.md](../../README.md) agent chain diagram.

---

## Minute 1-2 — The Team of Agents

**Say:**
> "This project defines 9 specialized AI agents, each with a narrow expertise. They're not just LLM prompts — they're knowledge workers with domain-specific skills loaded on demand."

**Show:** `ls .claude/agents/` — 10 agent files, each ~80-120 lines.

**Say:**
> "Each agent body is intentionally small — about 100 lines. But they can load deep expertise from skill packs on demand. For example, the Backend Dev agent loads the Spring Boot Banking skill, which contains the complete coding standards, security rules, idempotency patterns, and anti-pattern checklist for a Java banking service."

**Show:** `.claude/skills/spring-boot-banking/SKILL.md` — skim the Quick Reference table.

---

## Minute 2-4 — The SDLC Run

**Say:**
> "We gave the Player agent — the orchestrator — a single requirement: 'Build a Money Transfer feature.' It planned the full 8-phase SDLC chain and delegated each phase to the right specialist. Let me show you what they produced."

**Show:** `docs/agents-comms/dashboard.md`

Walk the table, hitting the highlights:
- **S2 (BA):** 11 user stories, 43 acceptance criteria, 8 open questions flagged for compliance review
- **S3 (Architect):** 9 microservices, 14 Kafka events, 12 architectural decisions — all written in ~90 minutes
- **S4 (Tech Lead):** Full OpenAPI spec, 5 database migrations, 3 Avro event schemas
- **S5 (Backend Dev):** 54 Java files, hexagonal architecture, 40 unit tests — all in one agent run
- **S6-S9:** Code review found 0 blockers. Security found 0 critical/high issues. QA ran the actual test suite. DevOps built and smoke-tested a Docker image.

**Key point:**
> "Every phase transition produced a structured handoff artifact — a JSON envelope with machine-validated fields — plus a human-readable Markdown summary. The Player validated each artifact against a schema before passing it forward. This is how you get auditability."

---

## Minute 4-5 — Live Test Run

**In terminal:**
```bash
cd backend/transfer-service
mvn test -q
```

**While it runs, say:**
> "This is running the actual test suite. The QA agent didn't just write a test plan — it ran Maven and verified 40/40 tests pass, measured code coverage to 95–98% on money-handling code, and filed two bug reports back to the backend dev when it found gaps."

**When it completes:**
> "40 out of 40. Green. The AI-written code passes the AI-written tests, reviewed by the AI reviewer, cleared by the AI security agent."

---

## Minute 5-6 — The Handoff Artifact

**Show:** `docs/artifacts/S5-backend-dev-money-transfer.md`

**Say:**
> "Here's the handoff artifact from the Backend Dev agent. It's not just code — it's a structured report: which files changed, build status, coverage metrics, known limitations, and a self-check result. The Reviewer agent consumed this artifact, not just the code."

Scroll to the bottom, show the `files_changed` list and test results.

**Then show:** `docs/artifacts/S6-reviewer-money-transfer.md`

**Say:**
> "The Reviewer found 0 blockers, 5 major issues, and 13 minor issues. The major issues were things like a hardcoded stub customer ID and a missing `@Profile` guard — real engineering concerns. They were accepted for v1 with explicit tracking and will gate the staging deployment."

---

## Minute 6-7 — Security Review + Agent Collaboration

**Show:** `docs/artifacts/S7-security-money-transfer.md`

**Say:**
> "The Security agent ran a full STRIDE threat model on both API endpoints, checked OWASP Top 10, verified PCI-DSS scope, and reviewed PDPA/GDPR compliance. Zero critical findings, zero high findings. It flagged a DB_PASSWORD default in YAML as a low finding, and those items are now blocking the staging deploy."

**Show:** `docs/agents-comms/decisions-log.md` — skim the ADR list.

**Say:**
> "16 architecture decisions, recorded with context and rationale. Every major choice — from idempotency key hashing to money representation as a string on the wire — has a written decision. This is the audit trail."

---

## Minute 7-8 — Docker + Production Readiness

**Show:** `docs/artifacts/S9-devops-money-transfer.md`

**Say:**
> "The DevOps agent built a multi-stage Docker image, wrote a 9-template Helm chart, a 10-panel Grafana dashboard with 6 Prometheus alert rules, and an 11-stage CI/CD pipeline. It ran a smoke test and got HTTP 200 on liveness, readiness, and Prometheus endpoints."

Open `infra/observability/grafana/dashboards/transfer-service.json` briefly.

**Say:**
> "This Grafana dashboard was written by an AI agent. It includes RED metrics, idempotent replay counters, saga compensation tracking, and a daily limit rejection counter — domain-specific panels that a DevOps human would spend hours configuring."

---

## Minute 8-9 — What It Means

**Say:**
> "What you just saw was an entire sprint — normally 2-3 weeks of work across 8 specialists — executed autonomously in a single session. The output is not a prototype: it's production-quality code with tests, security review, infra-as-code, and a CI/CD pipeline.
>
> The v1 scaffold is local-dev only — 5 backend hardening items gate the staging deploy, which is the right engineering decision. The AI agents knew what they were building and what needed to come next.
>
> The implication is not that this replaces engineers. It's that engineers can now move from writing boilerplate to reviewing and directing AI output — the highest-leverage work."

---

## Minute 9-10 — Q&A Anchors

**Anticipated questions:**

**"Can it handle more complex requirements?"**
> Yes — the BA artifact has 11 user stories, 43 acceptance criteria. We only implemented 2 in this run (the happy path and idempotency). The other 9 stories are in the architecture and tech-lead artifacts, ready for the next sprint.

**"How do you control what the agents do?"**
> Each agent has a strict decision-rules table. For example, the Backend Dev agent has a rule: if test coverage drops below 80%, it retries once then escalates to the Player. The Player orchestrates feedback loops with a max 3 retries before escalating to the human.

**"What if an agent makes a mistake?"**
> That's what the review chain is for. Every piece of work passes through Reviewer → Security → QA before reaching DevOps. The Reviewer found real issues in this run. The pattern is defense-in-depth — the same principle as the best engineering teams.

**"How long did this take?"**
> The full 8-phase SDLC chain ran in a single Claude Code session — a few hours of wall time including artifact generation, actual Maven test execution, and Docker image build.

---

## Demo Artifacts Cheat Sheet

| What to show | File |
|---|---|
| Agent team | `.claude/agents/` |
| Skill system | `.claude/skills/spring-boot-banking/SKILL.md` |
| Phase board | `docs/agents-comms/dashboard.md` |
| Timeline | `docs/agents-comms/timeline.md` |
| BA output | `docs/artifacts/S2-ba-money-transfer.md` |
| Architecture | `docs/artifacts/S3-solution-architect-money-transfer.md` |
| API spec | `backend/transfer-service/api/openapi.yaml` |
| Backend code | `backend/transfer-service/src/` |
| Code review | `docs/artifacts/S6-reviewer-money-transfer.md` |
| Security review | `docs/artifacts/S7-security-money-transfer.md` |
| Test report | `docs/artifacts/S8-qa-money-transfer.md` |
| Docker + infra | `docs/artifacts/S9-devops-money-transfer.md` |
| 16 ADRs | `docs/agents-comms/decisions-log.md` |
| HTML dashboard | `docs/demo/index.html` |
