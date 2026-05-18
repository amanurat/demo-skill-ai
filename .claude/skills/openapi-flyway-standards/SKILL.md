---
name: openapi-flyway-standards
description: Tech Lead artifact standards — OpenAPI 3 conventions (operationId, Problem-Detail RFC 7807, Idempotency-Key, URI versioning), DB schema design (UUID PK, optimistic lock, NUMERIC money), Flyway migration authoring (reversible, naming). Use when authoring API specs or DB migrations for banking services.
---

# OpenAPI & Flyway Standards — Tech Lead Authoring Skill

Authoring conventions for the **specs and migrations** a Tech Lead hands off to backend / frontend developers. Owned by `banking-tech-lead`; cross-referenced by `banking-reviewer` when judging contract or schema PRs.

## When to Use

- Drafting an OpenAPI 3 specification for a new banking microservice or endpoint
- Designing a new table (or evolving an existing one) for a banking service
- Authoring Flyway migration files (`V<seq>__<verb>_<object>.sql`)
- Writing ADRs that justify contract / schema decisions
- Reviewing whether a spec or migration meets banking-grade standards

## Quick Reference

| Need | Where to Look |
|---|---|
| OpenAPI conventions — operationId, Problem-Detail, security, pagination, headers, versioning | [references/openapi-standards.md](references/openapi-standards.md) |
| DB schema authoring + Flyway migration rules (tech-lead perspective) | [references/db-schema-flyway-rules.md](references/db-schema-flyway-rules.md) |

---

## Banking-Specific Decisions to Specify (inline checklist)

Every Tech Lead handoff for a financial endpoint must explicitly pin down:

- **Idempotency-Key strategy** — header name, TTL, scope (per user? global?)
- **Error code taxonomy** — `INSUFFICIENT_FUNDS`, `DAILY_LIMIT_EXCEEDED`, `PAYEE_NOT_FOUND`, etc.
- **Retry strategy** — which operations are retryable, max attempts, backoff
- **Saga step granularity** — what's a step, what's its compensation
- **Audit event shape** — required fields for every audit record

If any of the above is undecided, raise an ADR before emitting the handoff artifact.

---

## Tech Lead Anti-Patterns (inline)

Auto-fail criteria when reviewing your own spec / schema before handoff:

- Vague OpenAPI (e.g., "returns user data" with no schema $ref)
- Missing error responses (only `200` documented; no `4xx`/`5xx`)
- Non-reversible migrations without a documented `down` plan
- `FLOAT` / `DOUBLE` for money columns
- Implicit casts or missing constraints (NOT NULL, CHECK, UNIQUE)
- Skipping ADRs for "obvious" choices that aren't obvious six months later
- `DELETE`-without-archive on audit / financial tables

---

## Reference Index

- [openapi-standards.md](references/openapi-standards.md) — operationId naming, Problem-Detail schema, bearerAuth, pagination, headers, URI versioning, each with YAML examples
- [db-schema-flyway-rules.md](references/db-schema-flyway-rules.md) — tech-lead authoring view of schema conventions + Flyway migration rules

## Cross-Link: Developer-Side Persistence

The implementation-side counterpart lives in the `spring-boot-banking` skill:

- [`spring-boot-banking/references/persistence-jpa-flyway.md`](../spring-boot-banking/references/persistence-jpa-flyway.md) — JPA entity design, query strategy, HikariCP tuning, read replicas, partitioning

This skill (`openapi-flyway-standards`) is about **authoring the spec / migration**; the spring-boot-banking reference is about **consuming and implementing** them. Do not duplicate — link.

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:

- [`.claude/agents/banking-tech-lead.md`](../../agents/banking-tech-lead.md) — Read on every spec / migration authoring task

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before drafting OpenAPI specs or Flyway migrations. The agent persona instructs them to do so.
