# Final Prompt: Autonomous AI Multi-Agent System for End-to-End SDLC
## Sample Banking Project — Angular + Spring Boot Microservices

> **Journey Note:** ไฟล์นี้คือ prompt ฉบับ **Best-of-Breed v3** ที่รวมจุดเด่นจาก 3 แหล่ง:
> - [claude_prompt.md](claude_prompt.md) — ตาราง agent / acceptance criteria / Backend best-practice checklist
> - [gemini_prompt.md](gemini_prompt.md) — Role priming เข้ม / feedback loops / handoffs / output convention
> - [codex_prompt.md](codex_prompt.md) — Security & Reviewer agents / Anti-patterns / DoD / checklists
>
> **Scope correction (v3):** ทำ **1 Sample Project** เท่านั้น (ไม่ใช่ 16 — เกิดจาก speech-to-text error)

---

## 🧠 Role (Persona Priming)

You are simultaneously:
- **Master AI Orchestrator** — designing autonomous multi-agent workflows
- **Senior Enterprise Software Architect** — 15+ years in cloud-native, microservices, banking
- **AI Delivery Coach** — guiding teams through Agile + DevSecOps + CI/CD

Your responses must reflect **enterprise-grade rigor**, **security-first thinking**, **regulatory awareness** (PCI-DSS, GDPR), and **production-readiness**.

---

## 🎯 Objective

Design and deliver **one fully-working Sample Banking Project** built by a **fully autonomous Agentic AI workflow** that simulates a complete **Software Development Life Cycle (SDLC)**.

Coverage must span all phases:
```
Discovery → Planning → Design → Development → Testing → Deployment → Monitoring/Optimization
```

Humans act **only as reviewer/approver** — the system runs autonomously by default.

The Sample Project will serve as a **reference implementation** showcasing how AI agents collaborate end-to-end.

---

## 📋 Project Context

| Item | Value |
|---|---|
| **Project Type** | Single Sample Project (reference implementation) |
| **Domain** | Banking — feature focus: **Money Transfer** |
| **Frontend** | Angular (latest LTS) |
| **Backend** | Java Spring Boot Microservices |
| **Architecture** | Cloud-native, API-first, Event-driven |
| **Methodology** | Agile + DevSecOps + CI/CD |
| **Goal** | Demonstrate seamless AI Agent collaboration on a realistic banking feature |

### Why "Money Transfer" as the Sample Feature?
- Covers **authentication & authorization** (OAuth2/JWT)
- Requires **input validation** & business rules (limits, fees, FX)
- Triggers **distributed transactions** (debit + credit + ledger)
- Demands **idempotency** (no double-debit)
- Generates **audit trail** (regulatory requirement)
- Fires **async notifications** (email/SMS/push)
- Touches **fraud detection** hooks
- Exercises **observability** (tracing across services)

➡️ One feature, but exercises ~80% of enterprise banking concerns.

---

## 🤖 Deliverable 1 — AI Agents Architecture (Hybrid Multi-Agent)

### Tier 1 — Orchestrator

**Player Agent (Project Manager)**
- Plans, decomposes, prioritizes, tracks
- Owns handoff contracts and feedback-loop control
- Escalates to human after N failed retries (default N=3)

### Tier 2 — Specialized Agents

| # | Agent | Persona | Core Responsibility |
|---|---|---|---|
| 1 | **BA Agent** | Banking Business Analyst | Requirements, user stories, acceptance criteria, process flows |
| 2 | **Solution Architect Agent** | Enterprise Architect | Service decomposition, tech-stack decisions, event schemas |
| 3 | **Tech Lead Agent** | Senior Tech Lead | Detailed design, API contracts (OpenAPI), DB schema, ADRs |
| 4 | **Frontend Dev Agent** | Senior Angular Developer | UI components, state management, routing, API integration |
| 5 | **Backend Dev Agent** | Senior Java/Spring Boot Developer | Microservices, domain logic, persistence, integration |
| 6 | **QA Agent** | QA Automation Engineer | Test plans, unit/integration/E2E/performance tests |
| 7 | **DevOps Agent** | DevOps / Platform Engineer | CI/CD, IaC, containerization, observability |
| 8 | **Security & Compliance Agent** | AppSec / Compliance | OWASP, secure coding review, audit trail, policy enforcement |
| 9 | **Reviewer Agent** | Principal Engineer | Code review, quality gates, best-practice enforcement, anti-pattern detection |

---

## 🔄 Deliverable 2 — Workflow & Communication Protocol

### 2.1 Handoff Contract (Inter-Agent Artifact Schema)

```json
{
  "artifact_id": "uuid",
  "from_agent": "BA",
  "to_agent": "SolutionArchitect",
  "phase": "DESIGN",
  "payload": { /* role-specific content */ },
  "metadata": {
    "version": "1.0",
    "timestamp": "ISO-8601",
    "quality_gate_passed": true,
    "iteration": 1
  }
}
```

### 2.2 Forward Flow

```
BA → Solution Architect → Tech Lead → [Frontend Dev | Backend Dev]
                                              ↓
                                     Reviewer → Security → QA → DevOps
```

### 2.3 Feedback Loops & Auto-Fix Cycle

| Trigger | Loop Back To | Action |
|---|---|---|
| QA finds bug | Dev Agent | Auto re-fix + re-test |
| Reviewer rejects | Dev Agent | Refactor per comments |
| Security finds vuln | Dev Agent + Architect | Patch + re-design if needed |
| Tech Lead spots ambiguity | BA Agent | Clarify spec |
| DevOps finds infra constraint | Solution Architect | Re-design |
| Compliance issue | BA + Architect | Re-scope |

**Retry limits:** Each loop has max 3 iterations → escalate to human.

### 2.4 Quality Gates per Phase

- **Discovery → Planning:** Requirement completeness ≥ 90%
- **Design → Dev:** API contract validated, threat model done
- **Dev → Review:** Unit coverage ≥ 80%, lint pass, build pass
- **Review → Security:** No critical anti-patterns, SOLID compliance
- **Security → QA:** SAST/DAST pass, no high CVEs
- **QA → DevOps:** All test suites green, performance SLA met
- **DevOps → Done:** Deploy success, smoke tests pass, observability live

### 2.5 Definition of Done (DoD)

A feature is "Done" only when:
- [ ] Code merged to main with green CI
- [ ] Test coverage thresholds met
- [ ] Security scan passed (SAST/DAST/SCA)
- [ ] API documented (OpenAPI)
- [ ] Observability hooks added (logs/metrics/traces)
- [ ] Audit trail recorded for financial operations
- [ ] Deployed to staging with smoke tests green
- [ ] Reviewer + Security agents both approve

---

## 📁 Deliverable 3 — Project Structure

Recommend monorepo vs multi-repo **with rationale**. Provide folder tree for:
- `frontend/` — Angular workspace
- `backend/` — Spring Boot microservices (per-service or monorepo modules)
- `agents/` — Skill files + orchestration logic
- `infra/` — IaC (Terraform/Pulumi), K8s manifests, Helm charts
- `docs/` — ADRs, runbooks, API specs
- `.github/` or `.gitlab/` — CI/CD pipelines

Include: naming conventions, branching strategy (GitFlow vs Trunk-based), commit conventions.

### Recommended Microservices for Money Transfer Sample

| Service | Responsibility |
|---|---|
| `identity-service` | OAuth2/OIDC, user auth, JWT issuance |
| `account-service` | Account balances, holds |
| `transfer-service` | Transfer orchestration (Saga coordinator) |
| `ledger-service` | Double-entry bookkeeping, immutable journal |
| `notification-service` | Email/SMS/push (async via Kafka) |
| `audit-service` | Append-only audit log |
| `api-gateway` | Routing, rate limiting, auth enforcement |

---

## 🛠 Deliverable 4 — Agent Skill Files (`*.skill.md`)

One file per agent. Each must define:
- **Persona** (role, seniority, mindset)
- **Inputs** consumed
- **Outputs** produced (with schema)
- **Core capabilities** & **best practices**
- **Anti-patterns to avoid**
- **Decision rules** (when to escalate, when to loop back)
- **Acceptance criteria** for own deliverable
- **Pre-merge / pre-release checklist**

---

## ⭐ Deliverable 5 — `backend_java_spring_boot.skill.md` (PRIORITY)

Generate the **complete content** for the Backend Agent skill file as a **single Markdown code block**, ready to save.

Must cover comprehensively:

### Coding Standards
- Clean Architecture / Hexagonal Architecture
- SOLID principles, GoF Design Patterns
- DTO ↔ Entity mapping (MapStruct); never leak JPA entities to API
- Exception handling (`@ControllerAdvice`, Problem-Detail RFC 7807)
- Null-safety, immutability defaults, Lombok guidelines

### Microservices Best Practices
- API Gateway (Spring Cloud Gateway)
- Service Discovery (Eureka / Consul)
- Centralized config (Spring Cloud Config)
- Resilience (Resilience4j: circuit breaker, retry, bulkhead)
- Event-driven (Kafka / RabbitMQ)
- Saga pattern for distributed transactions
- **Idempotency keys** mandatory for financial operations
- Outbox pattern for reliable event publishing

### Security Baseline (Banking-grade)
- OAuth2 / OIDC + JWT, short-lived tokens, refresh rotation
- Spring Security configuration
- mTLS between services, TLS 1.3 external
- Encryption at rest (column-level for PII)
- Secrets management (Vault / AWS Secrets Manager) — **never** in code/config
- **OWASP Top 10** + OWASP ASVS Level 2 minimum
- Audit logging for every financial transaction (immutable)
- PCI-DSS / GDPR considerations

### Persistence
- Spring Data JPA + Hibernate best practices
- N+1 prevention, lazy/eager rules
- Flyway / Liquibase for migrations — versioned, reversible
- HikariCP tuning, read replicas, partitioning
- Optimistic locking for concurrent updates

### Testing Policy
- Unit (JUnit 5 + Mockito) — coverage ≥ 80%, critical paths ≥ 95%
- Integration (Spring Boot Test + Testcontainers — real DB/Kafka)
- Contract testing (Spring Cloud Contract / Pact)
- Mutation testing (PIT) for money-handling code
- Performance baselines (Gatling / k6)

### Observability
- Structured logging (Logback + JSON, correlation IDs)
- Metrics (Micrometer → Prometheus)
- Distributed tracing (OpenTelemetry → Jaeger/Tempo)
- Health checks (Spring Boot Actuator)
- SLI/SLO definitions per service

### API & Versioning
- OpenAPI 3 (springdoc-openapi) — published to dev portal
- Versioning via URI (`/v1`, `/v2`) — never break consumers silently
- Backward-compatibility policy

### Performance Tuning
- JVM tuning (heap, GC choice — G1 / ZGC for low latency)
- Caching (Redis / Caffeine) with explicit TTL & invalidation
- Async + reactive (`@Async`, Project Reactor) when appropriate
- DB query plans reviewed for hot paths

### ❌ Anti-Patterns to Avoid
- Anemic Domain Model (logic in services only, entities as data bags)
- God Service (microservice doing everything)
- Distributed monolith (synchronous chains across services)
- Hardcoded secrets, logging PII / card numbers
- Hidden side effects in getters
- Skipping migrations / editing prod DB manually
- Catching `Exception` generically
- Returning JPA entities from controllers

### ✅ Pre-Merge Checklist
- [ ] All tests green locally + CI
- [ ] Coverage thresholds met
- [ ] Lint (Checkstyle / Spotless) clean
- [ ] No new SAST findings
- [ ] OpenAPI updated
- [ ] CHANGELOG entry added
- [ ] Reviewer + Security agent approved

### ✅ Pre-Release Checklist
- [ ] Migration scripts reviewed & reversible
- [ ] Feature flags in place for risky changes
- [ ] Rollback plan documented
- [ ] Observability dashboards updated
- [ ] Load test executed against staging
- [ ] Security scan (DAST) green
- [ ] On-call team briefed

---

## 🎬 Deliverable 6 — End-to-End Money Transfer Walkthrough

Demonstrate the **full agent flow** from raw requirement → deployed code for the Money Transfer feature, showing **each handoff artifact concretely**.

Required scenes:
1. **Raw input** — "I want users to transfer money between accounts."
2. **BA Agent output** — User stories, acceptance criteria, edge cases (insufficient funds, daily limit, FX, scheduled transfer)
3. **Solution Architect output** — Service map, event flow diagram, tech decisions
4. **Tech Lead output** — OpenAPI spec for `/transfers`, DB schema, Saga design, ADR for idempotency
5. **Backend Dev output** — Sample code for `TransferService`, `LedgerService`, Saga steps
6. **Frontend Dev output** — Angular component skeleton, form validation, API integration
7. **Reviewer feedback** — Sample code review comments
8. **Security feedback** — Sample threat-model findings + fixes
9. **QA output** — Test plan + sample test code
10. **DevOps output** — Sample CI/CD pipeline + K8s manifest + dashboard config
11. **Closure** — DoD checklist completed, deployment evidence

---

## ✅ Acceptance Criteria

- [ ] System operates autonomously; human is reviewer/approver only
- [ ] Each agent has a dedicated, reusable `*.skill.md` file
- [ ] Best practices **embedded inside** skill files (not just listed)
- [ ] Anti-patterns explicitly enumerated per agent
- [ ] Handoff contract is explicit and validated
- [ ] Feedback loops have retry limits + human-escalation rules
- [ ] DoD and quality gates defined per phase
- [ ] Output code is **production-ready** (security, observability, tests)
- [ ] Banking concerns (audit, compliance, idempotency, PCI/GDPR) addressed
- [ ] Money Transfer use case fully traced end-to-end with real artifacts

---

## 📤 Output Format

Provide a well-structured Markdown response:
1. **Executive Summary** (≤ 1 page)
2. **Architecture Overview** (Mermaid diagrams preferred)
3. **Project Folder Tree** (code block)
4. **All `*.skill.md` files** — each in its own fenced code block, ready to save
5. **Money Transfer end-to-end walkthrough** with artifact examples
6. **Next-step recommendations** for extending the sample

**Language convention:**
- Explanation / narrative → **Thai** (professional tone)
- Code, schemas, file contents, technical identifiers → **English** (mandatory for precision)

---

## 📝 Journey Log

- **2026-05-17** — Revised raw [requirement.md](requirement.md) → [claude_prompt.md](claude_prompt.md)
- **2026-05-18 (am)** — Parallel prompts created: [gemini_prompt.md](gemini_prompt.md), [codex_prompt.md](codex_prompt.md)
- **2026-05-18 (pm)** — Merged Claude + Gemini → `final_prompt.md` v1
- **2026-05-18 (later)** — Codex content restored → merged v2 (16-project roadmap, Security/Reviewer agents)
- **2026-05-18 (final)** — **Scope clarified by user: 1 Sample Project only** (not 16 — speech-to-text artifact). Refocused on Money Transfer feature → **v3 (this file)**
