---
name: banking-test-automation
description: Banking QA / SDET patterns — test pyramid strategy, Testcontainers integration tests, banking-specific test cases (idempotency, daily limits, saga compensation), coverage and mutation thresholds, performance SLA, anti-patterns. Use when writing or reviewing automated tests for banking services.
---

# Banking Test Automation — QA / SDET Skill

Reusable test-engineering patterns for banking microservices and Angular front-ends. Loaded by the `banking-qa` agent (and any reviewer assessing test quality).

## When to Use

- Writing or extending a test plan for a banking feature
- Adding unit / integration / contract / E2E / performance tests
- Reviewing PRs for test completeness, naming, anti-patterns
- Assessing whether coverage + mutation thresholds are met
- Diagnosing flaky tests or environment-divergence issues (H2 vs Postgres, embedded vs real Kafka)
- Designing the test pyramid for a new service

## Quick Reference

| Need | Where to Look |
|---|---|
| Pyramid shape, layer responsibilities, coverage gates, performance SLA | [references/test-pyramid-strategy.md](references/test-pyramid-strategy.md) |
| Banking-specific test cases (idempotency, daily limit, saga compensation, audit) — with sample assertions and BA-story coverage | [references/banking-test-cases.md](references/banking-test-cases.md) |

---

## Coverage Rules (inline — apply on every test pass)

- Overall coverage **≥ 80%**
- Money-handling code **≥ 95%**
- Mutation score **≥ 70%** on money paths (PIT)
- Branches: every error path has **≥ 1 test**

Coordinate these numbers with the backend `spring-boot-banking` skill's Testing Policy table — they must stay in sync.

---

## Best Practices (inline — short list)

- **Real DB + Kafka via Testcontainers** — H2 / embedded Kafka diverge from prod and hide bugs
- **Test naming describes behavior, not method**: `should_X_when_Y` (e.g., `should_reject_transfer_when_balance_insufficient`)
- **One assertion per test** when possible — keep failures diagnosable
- **Test data builders** for entity creation (no copy-paste setup)
- **Reset state** between tests (`@DirtiesContext` or transactional rollback)
- **No flaky tests** — fix or quarantine immediately with an explicit ticket; never re-run hoping it passes
- **Fast unit tests** (< 100ms each); slow integration / E2E suites run separately

---

## QA Anti-Patterns (inline — short list)

- Tests that mock the class under test (`@MockBean` of the SUT)
- Tests asserting nothing (always pass — green theatre)
- Test names like `test1`, `testTransfer` (no behavior described)
- Sleep-based waits (`Thread.sleep(2000)`) — use **Awaitility** with conditions
- Sharing mutable state between tests
- **H2 instead of Testcontainers Postgres** — behavior diverges from prod
- **Embedded Kafka instead of Testcontainers Kafka** — same divergence
- Snapshot tests for everything (brittle, passes on irrelevant changes)
- Disabling tests instead of fixing them (coverage rot)

Full anti-pattern catalog with severity tags lives in the backend skill: see [`spring-boot-banking/references/spring-anti-patterns.md`](../spring-boot-banking/references/spring-anti-patterns.md) (Testing section).

---

## Reference Index

- [references/test-pyramid-strategy.md](references/test-pyramid-strategy.md) — pyramid layers, what belongs where, coverage gates, performance SLA
- [references/banking-test-cases.md](references/banking-test-cases.md) — concrete Money Transfer test cases with BA-story coverage, structure, and sample assertions

---

## How This Skill is Loaded

This skill is referenced (not auto-injected) by:
- [`.claude/agents/banking-qa.md`](../../agents/banking-qa.md) — Read on every QA task

Subagents have `Read` tool access — they must read this SKILL.md (and any relevant `references/*.md`) explicitly before writing or assessing tests. The `banking-qa` agent persona instructs them to do so. Coordinate coverage thresholds with the [`spring-boot-banking`](../spring-boot-banking/SKILL.md) skill so the QA and backend agents do not drift.
