# Banking SDLC AI — Multi-Agent Demo

> **Money Transfer feature built end-to-end by 9 specialized AI agents, running autonomously through every SDLC phase — from raw requirement to a Docker image passing smoke tests.**

---

## What This Proves

A chain of AI agents can replace the human handoffs across an entire software development lifecycle — not just code generation, but requirements analysis, architecture, API design, code review, security audit, QA, and DevOps — all with structured handoff artifacts and enforced quality gates at each transition.

**No human wrote a line of backend code.** The agents collaborated, reviewed each other's output, filed bugs, applied security patches, and produced a containerized service.

---

## The Numbers

| Metric | Result |
|---|---|
| SDLC phases automated | **8** (BA → Architect → Tech Lead → Dev → Review → Security → QA → DevOps) |
| Total files produced | **~120** |
| Unit tests (AI-written) | **40 / 40 passing** |
| Money-path code coverage | **95–98%** |
| Security findings — critical/high | **0** |
| Architecture decisions recorded | **16 ADRs** |
| Docker smoke test | **✅ HTTP 200** (liveness + readiness + Prometheus) |
| Infra files | **23** (Dockerfile, Helm chart, Grafana, Prometheus, CI/CD) |
| Quality gates passed | **8 / 8** |

---

## The Agent Chain

```
User requirement
      ↓
  banking-ba           →  11 user stories, 43 acceptance criteria
      ↓
  banking-solution-architect  →  9 microservices, 14 Kafka events, 12 ADRs
      ↓
  banking-tech-lead    →  OpenAPI spec, 5 Flyway migrations, 3 Avro schemas
      ↓
  banking-backend-dev  →  54 Java files, hexagonal architecture, 40 unit tests
      ↓
  banking-reviewer     →  0 blockers · 5 major · 13 minor — APPROVED
      ↓
  banking-security     →  0 critical · 0 high · STRIDE threat model — APPROVED
      ↓
  banking-qa           →  40/40 pass · 95%+ money-path coverage · 2 bugs filed
      ↓
  banking-devops       →  Docker image built · Helm chart · CI/CD · HTTP 200 ✅
```

Each transition produces a structured JSON + Markdown artifact validated against a handoff schema.

---

## Quick Navigation

| Goal | Link |
|---|---|
| **Run the demo in 5 min** | [docs/QUICKSTART.md](docs/QUICKSTART.md) |
| **Present to executives (10 min script)** | [docs/demo/DEMO-SCRIPT.md](docs/demo/DEMO-SCRIPT.md) |
| **Visual HTML dashboard** | [docs/demo/index.html](docs/demo/index.html) — open in browser |
| **Agent communication sequence** | [docs/demo/sequence-diagram.md](docs/demo/sequence-diagram.md) |
| **Live SDLC phase board** | [docs/agents-comms/dashboard.md](docs/agents-comms/dashboard.md) |
| **Handoff timeline** | [docs/agents-comms/timeline.md](docs/agents-comms/timeline.md) |
| **16 Architecture Decisions** | [docs/agents-comms/decisions-log.md](docs/agents-comms/decisions-log.md) |
| **Outstanding work (US-006)** | [docs/agents-comms/open-issues.md](docs/agents-comms/open-issues.md) |
| **Full DoD checklist** | [docs/artifacts/FINAL-DOD-money-transfer-v1.md](docs/artifacts/FINAL-DOD-money-transfer-v1.md) |
| **Per-phase artifact summaries** | [docs/artifacts/](docs/artifacts/) |
| **Backend service source** | [backend/transfer-service/](backend/transfer-service/) |
| **Infrastructure configs** | [infra/](infra/) |
| **CI/CD pipeline** | [.github/workflows/transfer-service-ci.yml](.github/workflows/transfer-service-ci.yml) |

---

## Run It Yourself

### Prerequisites
- Java 21+ and Maven 3.9+
- Docker (for integration tests and smoke test)

### Step 1 — Unit tests
```bash
cd backend/transfer-service
mvn test
# 40/40 tests pass in ~8s
# Coverage report: target/site/jacoco/index.html
```

### Step 2 — Build Docker image
```bash
docker build -t bank/transfer-service:1.0.0-SNAPSHOT backend/transfer-service/
```

### Step 3 — Start with dependencies
```bash
docker compose -f infra/docker-compose.yml up -d
```

### Step 4 — Smoke test
```bash
bash infra/smoke/smoke-test.sh
# liveness → 200, readiness → 200, prometheus → 200
```

### Step 5 — Send a transfer
```bash
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"sourceAccountId":"ACCT-001","destinationAccountId":"ACCT-002","amount":"500.00","currency":"THB","description":"Rent"}'
```

> **v1 note:** SecurityConfig is `permitAll` for local dev. JWT enforcement is the first item in US-006.

---

## Agent System Architecture

Agents live in [.claude/agents/](.claude/agents/) as slim Markdown files (~80-120 lines). They load specialized domain knowledge on-demand from [.claude/skills/](.claude/skills/), reducing per-call token cost by 35–60%.

```
.claude/
├── agents/                        # 10 specialized agents
│   ├── banking-player.md          # Orchestrator
│   ├── banking-ba.md
│   ├── banking-solution-architect.md
│   ├── banking-tech-lead.md
│   ├── banking-backend-dev.md
│   ├── banking-reviewer.md
│   ├── banking-security.md
│   ├── banking-qa.md
│   ├── banking-devops.md
│   └── banking-frontend-dev.md
└── skills/                        # 9 skill packs (loaded on-demand by agents)
    ├── spring-boot-banking/        # Hexagonal arch, JPA, idempotency, outbox
    ├── banking-security-patterns/  # STRIDE, OWASP, PCI-DSS, GDPR
    ├── angular-banking-ui/
    ├── banking-devops-platform/
    ├── banking-test-automation/
    ├── code-review-checklists/
    ├── openapi-flyway-standards/
    ├── kafka-spring-patterns/
    └── resilience4j-patterns/
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.x |
| Architecture | Hexagonal / Ports & Adapters |
| Database | PostgreSQL + Flyway |
| Messaging | Kafka (Transactional Outbox) |
| Testing | JUnit 5, Testcontainers, JaCoCo |
| Containers | Docker + Kubernetes / Helm |
| Observability | Micrometer → Prometheus → Grafana |
| Tracing | OpenTelemetry (OTLP → Tempo) |
| CI/CD | GitHub Actions (11-stage pipeline) |
| Auth | OAuth2 / OIDC + JWT (RS256) |

---

## What's Next — US-006

The v1 scaffold runs locally. Before staging:

1. Activate JWT validation in `SecurityConfig`
2. Read `customer_id` from JWT claim (remove `STUB_CUSTOMER_ID`)
3. Add `@Profile("!staging & !prod")` guard on `AccountClientStub`
4. Add `MDC.clear()` ServletFilter + Micrometer tracing filter
5. Upgrade CI runner to Docker Desktop ≥ 4.19

See [open-issues.md](docs/agents-comms/open-issues.md) for the full backlog.
