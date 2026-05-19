# Enterprise Architect's Guide — Banking AI Multi-Agent Demo

> **Reading time:** 10–15 min | **Best viewed in:** any Markdown renderer (VS Code, GitHub, etc.)

---

## What This Is

A proof-of-concept showing that a **chain of 9 specialized AI agents** can execute an entire software development lifecycle autonomously — from raw business requirement to a containerized, smoke-tested microservice — with structured quality gates at every transition.

**Feature delivered:** Money Transfer (intra-bank) for a Thai retail banking platform.
**No human wrote backend code.** Every artifact, decision, and review was agent-generated.

---

## By the Numbers

| Metric | Result |
|---|---|
| SDLC phases automated | **8** (BA → Architect → Tech Lead → Dev → Review → Security → QA → DevOps) |
| Microservices designed | **9** (across 4 bounded contexts) |
| Kafka events defined | **14** |
| Architecture decisions (ADRs) | **16** (12 Architect + 4 Tech Lead) |
| Unit tests — AI-written | **40 / 40 passing** |
| Money-path code coverage | **95–98%** |
| Security findings — critical / high | **0** |
| Quality gates passed | **8 / 8** |
| Infrastructure files | **23** (Dockerfile, Helm, Grafana, Prometheus, CI/CD) |

---

## Recommended Reading Path for EA

Read in this order — each link builds on the previous.

### 1. System Architecture (5 min)
[`docs/architecture/overview.md`](architecture/overview.md)

Service map with Mermaid diagram, 9 microservices across 4 bounded contexts (Edge, IAM, Payments, Compliance), key patterns, and cross-cutting concerns.

**EA focus:** Bounded context design, service isolation rationale, event-driven topology.

---

### 2. Architecture Decision Records (10 min)
[`docs/agents-comms/decisions-log.md`](agents-comms/decisions-log.md)

All 16 ADRs with full rationale and rejected alternatives. Highlights for EA:

| ADR | Decision | Why it matters |
|---|---|---|
| ADR-001 | Saga **Orchestration** (not Choreography) | Compensation order enforced; observable state machine; testable |
| ADR-002 | Idempotency: per-service RDBMS table (not Redis) | Transactional atomicity with the business write — prevents double-debit on retry |
| ADR-003 | Transactional Outbox (not Debezium CDC) | At-least-once event delivery with no new infrastructure; `FOR UPDATE SKIP LOCKED` for scale-out |
| ADR-009 | No service mesh in v1 | Resilience4j + NetworkPolicy sufficient at 9 services; defer Istio to >20 services |
| ADR-010 | Apicurio Registry + Avro (not Confluent) | Open-source, Apache 2.0 — avoids Confluent license restriction flagged by legal |
| ADR-012 | Audit log: DB role INSERT-only + trigger | Defense-in-depth; 7-year BoT retention; date-partitioned cold-tier archival |
| ADR-016 | Money as decimal **string** on the wire | Prevents IEEE 754 float precision loss on JS clients; `NUMERIC(19,4)` in DB |

**2 ADRs are assumptions pending Compliance SME sign-off** (ADR-006: timezone, ADR-007: AML scope). Both are isolated changes — no schema migration required if the decision changes.

---

### 3. SDLC Audit Trail (3 min)
[`docs/agents-comms/dashboard.md`](agents-comms/dashboard.md)

Phase-by-phase gate status, scope, and what each agent produced. Every gate is traceable to a JSON + Markdown artifact.

---

### 4. Security Posture (5 min)
[`docs/artifacts/S7-security-money-transfer.md`](artifacts/S7-security-money-transfer.md)

STRIDE threat model against the two primary endpoints + OWASP Top 10 review. **0 critical, 0 high.** 3 medium findings are pre-staging blockers (auth hardening, stub removal, IDOR fix) — all owned by US-006.

Compliance coverage: **PCI-DSS** (data residency, audit immutability, rate limiting), **GDPR/PDPA** (IP hashing, K8s topology pinned to Thailand region).

---

### 5. Open Architecture Questions (2 min)
[`docs/agents-comms/open-issues.md`](agents-comms/open-issues.md)

48 tracked items. The 2 that need EA/SME input before production go-live:

| Item | Question | Impact if wrong |
|---|---|---|
| OQ-001 / ADR-006 | Daily limit reset: Bangkok time (UTC+7) or UTC? | 1 config flag change — no schema migration |
| OQ-005 / ADR-007 | AML THB 2M threshold: outbound only, or combined volume? | View-level aggregation change — no schema migration |

---

## What Is Complete vs In-Progress

| Layer | Status | Notes |
|---|---|---|
| Architecture design | ✅ Complete | 9 services, 14 events, 16 ADRs |
| API contract (OpenAPI 3) | ✅ Complete | 5 Flyway migrations, 3 Avro event schemas |
| Backend scaffold | ✅ v1 | US-001 (happy path) + US-003 (idempotency) — 44 Java files, 40/40 tests pass |
| Infrastructure | ✅ Complete | Docker + Helm chart + Grafana + Prometheus + CI/CD pipeline |
| Frontend (Angular) | ⏳ Deferred | Out of scope for this iteration |
| Auth hardening (JWT) | ⏳ US-006 | SecurityConfig currently permit-all (dev scaffold only) |
| Kafka relay (Outbox) | ⏳ US-007 | Outbox writes atomically; publisher stub only |
| Saga compensation | ⏳ US-008 | State machine defined; compensation path not yet exercised |
| User stories US-004–011 | ⏳ Future | Architecture and API contract cover all; implementation deferred |

---

## How the Agent System Works

```
User Requirement
      ↓
  banking-ba           →  User stories + Acceptance Criteria (S2)
      ↓
  banking-solution-architect  →  Service map, Kafka events, ADRs (S3)
      ↓
  banking-tech-lead    →  OpenAPI spec, DB schema, Flyway, Avro (S4)
      ↓
  banking-backend-dev  →  Spring Boot implementation (S5)
      ↓
  banking-reviewer     →  Code review: 0 blockers, 5 majors, 13 minor (S6)
      ↓
  banking-security     →  STRIDE + OWASP: 0 crit, 0 high (S7)
      ↓
  banking-qa           →  Test plan + 40/40 pass + 2 bugs filed to dev (S8)
      ↓
  banking-devops       →  Docker + Helm + CI/CD + HTTP 200 smoke test (S9)
```

Each transition produces a **JSON handoff artifact** validated against a schema by the orchestrator before the next agent is invoked. This gives the chain auditability and machine-verifiable quality gates — not just narrative output.

Agent definitions: [`.claude/agents/`](../.claude/agents/) — each ~100 lines, extended by domain skill packs on demand.

---

## Key Files at a Glance

| Purpose | File |
|---|---|
| System architecture diagram | [`docs/architecture/overview.md`](architecture/overview.md) |
| All 16 ADRs with rationale | [`docs/agents-comms/decisions-log.md`](agents-comms/decisions-log.md) |
| Phase gate dashboard | [`docs/agents-comms/dashboard.md`](agents-comms/dashboard.md) |
| Open issues + risks | [`docs/agents-comms/open-issues.md`](agents-comms/open-issues.md) |
| Security report (STRIDE + OWASP) | [`docs/artifacts/S7-security-money-transfer.md`](artifacts/S7-security-money-transfer.md) |
| QA test plan + results | [`docs/artifacts/S8-qa-money-transfer.md`](artifacts/S8-qa-money-transfer.md) |
| Helm chart + observability infra | [`infra/helm/`](../infra/helm/) · [`infra/observability/`](../infra/observability/) |
| Definition of Done (final) | [`docs/artifacts/FINAL-DOD-money-transfer-v1.md`](artifacts/FINAL-DOD-money-transfer-v1.md) |
| Agent definitions | [`.claude/agents/`](../.claude/agents/) |

---

*Generated from the Banking AI Multi-Agent Demo — v1 Money Transfer chain, 2026-05-18.*
