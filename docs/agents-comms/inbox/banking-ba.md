# Inbox — banking-ba

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-player (Orchestrator) | 2026-05-18 — session start | DISCOVERY | (informal raw requirement) | "Money Transfer feature — intra-bank, THB, retail internet banking. Implement happy path + idempotency + limits + saga + AML + payees + notifications." First in the SDLC chain — no upstream JSON artifact. |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-solution-architect | 2026-05-18 09:00 UTC | DISCOVERY → PLANNING | [S2 `a3f7c2e1-84b0-4d9f-b6e3-2c51f0d8a749`](../../artifacts/S2-ba-money-transfer.md) | Quality gate PASS — 11 user stories (US-001..US-011), 43 acceptance criteria total, 6 data entities (Transfer, Account, Payee, IdempotencyKey, AuditLog, DailyTransferAccumulator), quantified NFRs (p95<1s, 99.95% availability, 100/500 TPS, 7y retention), 3 process flows (happy path / saga compensation / payee add-lookup), 13 out-of-scope items, 8 open questions flagged for SME |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | DISCOVERY → PLANNING | PASS — all 8 required stories present (US-001 happy, US-002 insufficient, US-003 idempotency, US-004 per-tx limit, US-005 daily limit, US-006 frozen, US-007 double-debit, US-008 saga compensation) + 3 optional (US-009 AML, US-010 payee, US-011 notifications). All 7 hard constraints traced to ≥1 AC. |

## Open Items / Action Required

8 open questions surfaced — all forwarded to downstream agents as documented assumptions while awaiting SME confirmation:

- **OQ-001** Daily limit reset timezone — Bangkok (UTC+7) vs UTC midnight. → Solution Architect adopted Asia/Bangkok in ADR-006 (assumption).
- **OQ-002** Per-tier limit governance — self-service tier upgrade vs ops-only. Affects admin API scope.
- **OQ-003** Own-to-own transfers — different validation flow? Confirm UX.
- **OQ-004** Notification delivery SLA — "2 of 3 channels" threshold needs business sign-off.
- **OQ-005** AML scope — outbound only vs combined? → Solution Architect adopted "outbound only, customer-level rollup" in ADR-007 (assumption).
- **OQ-006** Saga `COMPENSATION_FAILED` SLA — regulatory timebound for manual resolution? Affects Ops runbook.
- **OQ-007** Idempotency-Key generation — client-side UUID v4 vs server-side. Tech Lead confirmed client-side via ADR-013.
- **OQ-008** Payee masked-name display — "first name + last initial" sufficient for common names? UX sign-off needed.

→ **Action**: OQ-001 and OQ-005 marked must-resolve-before-go-live by Solution Architect. Other OQs may resolve during US-004/005/008/010 implementation cycles. No action required until SME feedback arrives.

## Skills Referenced When Working

- (none) — banking-ba is a lean agent that operates from its persona and the project's overview / constraints docs only. No SKILL.md dependency.

## Workflow Hooks

- On new requirement from Player → ingest, decompose into INVEST user stories, write Gherkin AC, surface open questions, emit S2 artifact.
- On clarification from Solution Architect or downstream → revise affected stories, bump iteration counter, re-emit S2.
- On SME answer to any OQ → update the relevant AC and notify downstream agents of the change.
