---
name: angular-banking-ui
description: Angular (latest LTS) patterns for banking UI — reactive forms for money operations, HttpClient integration with Idempotency-Key, accessibility (WCAG 2.1 AA), i18n, anti-patterns. Use when implementing or reviewing Angular features for banking web apps.
---

# Angular Banking UI — Implementation Skill

Reusable frontend patterns for Angular (latest LTS) banking/fintech web apps. Loaded by `banking-frontend-dev` and `banking-reviewer` agents.

## When to Use

- Implementing a new Angular feature in a banking workspace
- Reviewing PRs for Angular components / forms / API integration
- Adding new screens or flows touching money operations
- Refactoring legacy code toward standalone components + signals
- Diagnosing a11y, i18n, or performance regressions

## Quick Reference

| Need | Where to Look |
|---|---|
| Reactive forms for money, validators, masking, confirmation flows | [references/forms-money-handling.md](references/forms-money-handling.md) |
| HttpClient, interceptors (auth, idempotency, correlation), error UX, a11y, i18n | [references/api-integration-a11y.md](references/api-integration-a11y.md) |
| Frontend code smells to flag in review | [references/angular-anti-patterns.md](references/angular-anti-patterns.md) |

---

## Architecture (inline — apply to every feature)

- **Standalone components** + lazy-loaded routes (no NgModules unless legacy)
- **Feature libraries** (`libs/feature-transfer`) per business area; one bounded context per lib
- **Smart vs presentational** components separated — smart owns state + I/O, presentational is pure
- **Signals** for local state; **NgRx** only when state is cross-cutting / shared across features
- Generate typed API clients from OpenAPI (e.g., `openapi-generator`) — never hand-roll HTTP DTOs

---

## Performance Budgets (inline — enforced in CI)

- **Lighthouse**: performance ≥ 90, a11y = 100, best-practices ≥ 95 on key pages
- **Change detection**: `OnPush` everywhere; signal-based inputs preferred
- **Bundle budget** enforced in `angular.json` (initial + per-route)
- Lazy-load feature modules / routes
- Use `NgOptimizedImage` for all `<img>` tags
- No synchronous work > 50ms on main thread for critical flows

---

## Security Baseline (inline — auto-fail in review if violated)

- **JWT** in httpOnly cookie (preferred) or in-memory; **never `localStorage`** for sensitive tokens
- **No `bypassSecurityTrust*`** — Angular auto-escapes by default; don't bypass
- **CSP** headers configured at gateway; report-only mode before enforcing
- No secrets / API keys / tenant IDs in code or build artifacts
- Input sanitization for any HTML rendering (`DomSanitizer` only with explicit review)
- Forms for money operations: **no optimistic UI** — wait for server confirmation

For backend security counterparts see the `banking-security` agent.

---

## Pre-Handoff Self-Checks

Before emitting handoff artifact to `banking-reviewer`:

- [ ] `ng build --configuration production` green (no warnings)
- [ ] Unit coverage ≥ 80%; E2E for every story happy path
- [ ] `axe-core` a11y check pass (CI)
- [ ] Lighthouse perf ≥ 90, a11y = 100 on key pages
- [ ] All user-facing strings have i18n keys (TH + EN)
- [ ] No `any` in TS; no `bypassSecurityTrust*`
- [ ] Loading + error states present on every async UI
- [ ] No console errors / warnings in dev or prod build
- [ ] Bundle budget not exceeded

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-frontend-dev.md`](../../agents/banking-frontend-dev.md) — Read on every task
- [`.claude/agents/banking-reviewer.md`](../../agents/banking-reviewer.md) — Read when reviewing frontend PRs

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before starting work. The agent persona instructs them to do so.
