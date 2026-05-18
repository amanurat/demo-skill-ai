# Inbox — banking-solution-architect

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-ba | 2026-05-18 09:00 UTC | DISCOVERY → PLANNING | [S2 `a3f7c2e1-84b0-4d9f-b6e3-2c51f0d8a749`](../../artifacts/S2-ba-money-transfer.md) | 11 user stories with 43 AC, 6 data entities, quantified NFRs (p95<1s, 99.95% availability, 100/500 TPS, 7y retention), 3 process flows, 13 out-of-scope items, 8 open questions |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-tech-lead | 2026-05-18 11:30 UTC | PLANNING → DESIGN | [S3 `b5e2d8a4-4f1c-4a7e-9c3b-7d6f1e2a8c34`](../../artifacts/S3-solution-architect-money-transfer.md) | Quality gate PASS — 9 services (api-gateway, identity, account, payee, transfer, ledger, audit, notification, compliance), 14 Kafka events on Apicurio/Avro, **12 ADRs**, full NFR traceability matrix, 6 risks with mitigations, Mermaid C4 context diagram |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | PLANNING → DESIGN | PASS — all 11 BA user stories + all 7 NFR categories traced to design decisions; no shared-DB violations (each service owns its tables); OQ-001 + OQ-005 handled as documented assumptions in ADR-006/007 |

## Open Items / Action Required

- **ADR-006 (OQ-001 assumption)** — Daily accumulator uses Asia/Bangkok civil date (UTC+7); accumulator schema isolates timezone to a single config flag `transfer-service.daily-reset-zone`. **Awaiting Compliance + BA SME confirmation before go-live.** Backend dev can proceed for US-001/US-003 because they do not touch the daily-limit logic.
- **ADR-007 (OQ-005 assumption)** — AML 2,000,000 THB threshold applies to OUTBOUND successful transfers per customer per Bangkok civil day; cumulative sum aggregates across all source accounts owned by the customer. **Awaiting Compliance SME sign-off.** If Compliance instead requires inbound + outbound combined, the change is view-level only (not a schema migration).
- **RISK-005 escalation** — both above must be flagged as blockers for prod go-live. Track confirmation status in this inbox.
- **RISK-006 follow-up** — account-service becomes synchronous bottleneck for transfer-service (4-5 calls per transfer). Recommended a single combined "reserve-with-check" RPC; left for Tech Lead to design in account-service contract.

## Skills Referenced When Working

- (none) — banking-solution-architect is a lean agent. Operates from its persona, the BA artifact, and architecture standards in `docs/architecture/`. No SKILL.md dependency.

## Workflow Hooks

- On S2 from BA → produce service map, event catalogue, ADRs, NFR trace, risks; emit S3.
- On Tech Lead feedback (contract impractical, ADR conflict) → revise ADRs, bump iteration, re-emit S3 with rationale delta.
- On SME answer to OQ-001 / OQ-005 → revise ADR-006 / ADR-007, notify Tech Lead + Backend Dev.
- On new cross-cutting NFR from Security or DevOps → re-evaluate service decomposition or tech choices.
