---
name: code-review-checklists
description: Code review checklists for banking PRs — backend (Java/Spring/JPA/security/observability) and frontend (Angular/TypeScript/UX/a11y). Includes anti-pattern catalog with severity ratings (blocker/major/minor/nit). Use when reviewing PRs from banking-backend-dev or banking-frontend-dev.
---

# Code Review Checklists — Banking PRs

Reusable review playbooks for banking microservice and Angular UI PRs. Loaded by the `banking-reviewer` agent before every code review.

## When to Use

- Reviewing a PR emitted by `banking-backend-dev` (Spring Boot service)
- Reviewing a PR emitted by `banking-frontend-dev` (Angular UI)
- Triaging severity of findings (blocker vs nit) before posting comments
- Onboarding a new reviewer to the banking quality bar

## Quick Reference

| Need | Where to Look |
|---|---|
| Backend (Java/Spring) PR checklist — architecture, code quality, tests, security, observability | [references/backend-review-checklist.md](references/backend-review-checklist.md) |
| Frontend (Angular) PR checklist — code quality + UX/a11y | [references/frontend-review-checklist.md](references/frontend-review-checklist.md) |

---

## Severity Legend

You distinguish **blocker** (must fix), **major** (should fix), **minor** (improve), **nit** (style).

---

## Common Anti-Patterns to Flag (Quick Reference)

| Pattern | Severity | Why |
|---|---|---|
| Anemic Domain Model | major | Business rules scattered, bypassable |
| God Service | major | Hard to test, deploy, scale |
| Distributed Monolith | blocker | Sync chains break independent deploy |
| Hardcoded secret | blocker | Audit + leak risk |
| Logging PII / cards | blocker | Compliance violation |
| `Exception` catch-all | major | Hides real bugs |
| `OneToMany` to big collection | major | Memory blow-up |
| `@Data` on entity | minor | Mutable + broken hashCode |
| Floats for money | blocker | Precision loss |
| Missing `@Transactional` boundary | major | Inconsistent writes |
| `@Transactional` on controller | minor | Wrong layer |
| Direct Kafka send in tx | major | Risk of message lost on rollback |
| Optimistic UI on money tx | blocker | Customer confusion / disputes |
| `any` in TS | minor | Type safety lost |
| Skipped a11y | major | Legal + UX |

---

## Anti-Patterns (For Reviewer Itself)

- Being pedantic on style when substance is fine
- Demanding rewrites instead of suggesting fixes
- Reviewing the person, not the code
- Missing big issues while debating bikesheds
- Approving without reading actual diffs

---

## Reference Index

- [references/backend-review-checklist.md](references/backend-review-checklist.md) — Architecture/Design, Code Quality, Tests, Security/Banking Hard Rules, Observability
- [references/frontend-review-checklist.md](references/frontend-review-checklist.md) — Angular Code Quality, UX/a11y

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read on every PR review

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work.

Complementary skills the reviewer should also load:
- [`spring-boot-banking/references/spring-anti-patterns.md`](../spring-boot-banking/references/spring-anti-patterns.md) — full Java/Spring anti-pattern catalog
- [`angular-banking-ui/references/angular-anti-patterns.md`](../angular-banking-ui/references/angular-anti-patterns.md) — full Angular/TypeScript anti-pattern catalog
