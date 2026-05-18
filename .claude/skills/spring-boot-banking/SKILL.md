---
name: spring-boot-banking
description: Spring Boot 3.x banking microservice patterns — hexagonal architecture, JPA/Flyway persistence, idempotency + Saga + Outbox, observability, anti-patterns. Use when implementing or reviewing Java banking services (transfer, account, ledger, audit, notification).
---

# Spring Boot Banking — Implementation Skill

Reusable technical patterns for Java 21 + Spring Boot 3.x microservices in banking/fintech. Loaded by `banking-backend-dev` and `banking-reviewer` agents.

## When to Use

- Implementing a new Spring Boot service in the monorepo
- Reviewing PRs for backend services
- Adding endpoints to an existing service
- Refactoring legacy code toward hexagonal layout
- Diagnosing distributed-tx, idempotency, or observability issues

## Quick Reference

| Need | Where to Look |
|---|---|
| Hexagonal layout, service composition, sync vs async | [references/hexagonal-architecture.md](references/hexagonal-architecture.md) |
| JPA entities, optimistic locking, Flyway migrations, HikariCP | [references/persistence-jpa-flyway.md](references/persistence-jpa-flyway.md) |
| Idempotency-Key, Saga orchestration, Outbox pattern | [references/idempotency-saga-outbox.md](references/idempotency-saga-outbox.md) |
| Logging, metrics (Micrometer), tracing (OTel), health probes, perf tuning | [references/observability.md](references/observability.md) |
| Code smells to flag in review | [references/spring-anti-patterns.md](references/spring-anti-patterns.md) |
| Security baseline (OWASP, JWT RS256, encryption) | See agent [`banking-security`](../../agents/banking-security.md) — will move to `banking-security-patterns` skill in future |

---

## Coding Standards (inline — apply to every PR)

### Architecture

- **Hexagonal (Ports & Adapters)** per [project-structure.md](../../../docs/architecture/project-structure.md)
  - `domain/` — pure, framework-free (entities, value objects, domain services)
  - `application/` — use cases, sagas, orchestrations
  - `infrastructure/` — adapters (JPA, Kafka, REST clients)
  - `interfaces/` — controllers, listeners
- **SOLID** strictly applied — especially SRP and DIP
- **Domain-rich entities** — business rules live in the entity, not in services
- **Value objects** for money, account numbers, currency (avoid primitive obsession)

### Java / Spring

- Java 21 features: records, pattern matching, sealed types where appropriate
- **Constructor injection** (no `@Autowired` on fields)
- `@Transactional` only at application/use-case layer; never on controllers
- Immutability by default; mutability requires justification
- Lombok: `@Getter`, `@Builder`, `@RequiredArgsConstructor` OK. **Avoid `@Data`** (mutable + equals/hashCode pitfalls).

### DTO ↔ Entity Mapping

- Use **MapStruct** for explicit mapping
- **Never leak JPA entities** to the controller / API layer
- DTOs are immutable records

### Exception Handling

- Domain exceptions extend a sealed `DomainException` hierarchy
- Global `@ControllerAdvice` converts to **Problem-Detail (RFC 7807)** response
- Never catch `Exception` generically — catch specific types
- Don't swallow exceptions; log + rethrow with context

### Code Style

- Checkstyle + Spotless enforced
- 100-char line limit
- Javadoc on public APIs of `application` and `domain` layers

---

## API & Versioning

- **OpenAPI 3** via springdoc-openapi — generated from code annotations
- Spec published to `docs/api/<service>.yaml` on each build
- **URI versioning**: `/api/v1/...`, `/api/v2/...`
- Never break v1 silently; deprecation requires `Deprecation` header + sunset date
- All responses include `X-Request-Id` header

---

## Testing Policy (inline — coverage gates)

| Type | Tool | Coverage Target |
|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | ≥ 80% overall, ≥ 95% for money-handling |
| Integration | Spring Boot Test + Testcontainers (real Postgres + Kafka) | All API endpoints + Saga happy/sad paths |
| Contract | Spring Cloud Contract / Pact | Every consumer-provider pair |
| Mutation | PIT | On critical financial logic (≥ 70% mutation score) |
| Performance | Gatling / k6 (in `infra/`) | Baseline established, run nightly |

**Test rules:**
- No `@MockBean` of the class under test
- Integration tests use **real DB via Testcontainers** (not H2 — divergence trap)
- One test = one behavior; no kitchen-sink tests
- Test names describe behavior: `should_reject_transfer_when_balance_insufficient`

---

## Security Baseline (summary — full detail in `banking-security` agent)

Banking-grade hard rules — auto-fail in review if violated:

- **OAuth2 / OIDC** via Spring Security; JWT signed with **RS256** (not HS256), TTL ≤ 15 min
- **TLS 1.3** external; **mTLS** between services
- **Encryption at rest** for PII columns (AES-256-GCM)
- **No secrets in code or config files** — use Vault
- **No PII / card / JWT in logs** — even at DEBUG
- **Money in `BigDecimal`** never `float`/`double`
- **`@PreAuthorize`** on application services for fine-grained checks
- **Idempotency-Key required** on all financial POST/PUT — see [idempotency-saga-outbox.md](references/idempotency-saga-outbox.md)
- **Audit event** emitted for every state-changing financial op — append-only, immutable, ≥ 7 yr retention

For the full OWASP Top 10 + STRIDE checklist see the `banking-security` agent.

---

## Pre-Handoff Self-Checks

Before emitting handoff artifact to `banking-reviewer`:

- [ ] `mvn clean verify` green (build + tests + coverage)
- [ ] Checkstyle / Spotless clean (no warnings)
- [ ] OpenAPI spec regenerated and committed
- [ ] No new high/critical SAST findings (`mvn sonar:sonar` or equivalent)
- [ ] Coverage thresholds met (unit ≥ 80%, money paths ≥ 95%)
- [ ] All AC from BA stories satisfied
- [ ] Idempotency-Key honored on every financial endpoint
- [ ] Audit events wired up for state changes
- [ ] Observability hooks present (structured logs, metrics, traces)
- [ ] Migration scripts reversible (document down plan)
- [ ] `CHANGELOG.md` entry added

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-backend-dev.md`](../../agents/banking-backend-dev.md) — Read on every task
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read when reviewing backend PRs

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work. The agent persona instructs them to do so.
