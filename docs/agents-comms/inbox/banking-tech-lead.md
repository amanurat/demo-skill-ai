# Inbox — banking-tech-lead

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-solution-architect | 2026-05-18 11:30 UTC | PLANNING → DESIGN | [S3 `b5e2d8a4-4f1c-4a7e-9c3b-7d6f1e2a8c34`](../../artifacts/S3-solution-architect-money-transfer.md) | 9 services, 14 Kafka events on Apicurio/Avro, 12 ADRs (saga style, idempotency, outbox, accumulator, AML scope, timezone, etc.), full NFR traceability, 6 risks, Mermaid C4 context diagram |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-backend-dev | 2026-05-18 13:30 UTC | DESIGN → DEVELOPMENT | [S4 `c8d4e9f6-3a72-4b1d-8e5c-1f9d2a6b7c83`](../../artifacts/S4-tech-lead-money-transfer.md) | Quality gate PASS — full OpenAPI 3.0.3 contract (POST /api/v1/transfers + GET /api/v1/transfers/{transferId}; lookupPayee + listTransfers stubbed `x-status: planned`), 5 reversible Flyway migrations (transfers, transfer_idempotency, saga_state, transfer_outbox, daily_transfer_accumulator), 3 Avro event schemas, 4 ADRs (ADR-013 error taxonomy + Idempotency-Key hashing, ADR-014 outbox poll cadence, ADR-015 STRIDE threat model, ADR-016 money string-on-wire), 9 implementation notes, 6 frontend notes |
| 1b | banking-frontend-dev | (deferred — out of scope this run) | DESIGN → DEVELOPMENT | (same S4) | 6 frontend notes attached to S4 (UUID v4 Idempotency-Key generated at form-open, decimal.js for amount handling, 409 / 503 / 5xx handling, idempotencyStatus messaging, poll-on-unknown pattern) — picked up when Frontend agent runs |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | DESIGN → DEVELOPMENT | PASS — contract + DB schema + threat model + event schemas all satisfied; no deviations from architect ADRs (ADR-013..016 build on top of architect ADR-001..012) |

## Open Items / Action Required

Surfaced to downstream / sibling sessions (out-of-scope for this Tech Lead sub-session, picked up when those services are designed):

- **account-service RPC design** — RISK-006 suggested a single combined "reserve-with-check" RPC to replace the current 4-5 calls per transfer (status + balance + status + debit + credit). Owned by account-service per-service Tech Lead session.
- **Kafka topic naming** — current convention `<service>.<entity>.<event>.v<n>` per project-structure.md (e.g. `transfer.transfer.requested.v1`). Confirm with platform team that topic auto-creation + ACLs are in place before DevOps wires the relay.
- **Payee proxy decision** — payee-service proxies the account-service lookup (per ADR-008). Cache policy (60s Redis TTL, customer-scoped) needs cache-key design in the payee-service Tech Lead session.
- **Saga state schema for non-transfer use cases** — current `saga_state.saga_type` CHECK is hard-coded to `INTRA_BANK_TRANSFER`. When inter-bank / FX is added (out of v1), schema needs migration to widen the enum.
- **AML accumulator customer-level rollup** — ADR-007 assumes account_id-keyed accumulator with view-based rollup if Compliance later requires customer-level. Materialise as a `daily_transfer_accumulator_customer_view` only if needed.
- **OpenAPI / AsyncAPI publication** — contract is in `backend/transfer-service/api/openapi.yaml`. DevOps to publish to internal API portal during S8/S9.

Sibling sessions (other services not designed yet): account-service, payee-service, audit-service, ledger-service, notification-service, compliance-service, identity-service contracts will each need a Tech Lead pass.

## Skills Referenced When Working

- `.claude/skills/openapi-flyway-standards/SKILL.md` — OpenAPI 3.0.3 conventions (RFC 7807 ProblemDetail, Idempotency-Key header, `x-status: planned` markers) and Flyway forward-only migration rules (header block with ticket + author + rationale + down_sql runbook, `CHECK` constraints on enums, partial index design).

## Workflow Hooks

- On S3 from Solution Architect → produce per-service OpenAPI, DB schema (Flyway migrations), event schemas (Avro), ADRs supplementing architect's; emit S4.
- On Backend Dev pushback (contract impractical, schema gap) → revise ADR, contract or schema; bump iteration; re-emit S4.
- On Security finding affecting contract (e.g., new auth scope, header) → patch OpenAPI + ADR-015 STRIDE matrix; coordinate redeploy.
- On Architect ADR change (e.g., OQ-005 SME resolution flips AML scope) → re-evaluate accumulator schema + event schema; potentially emit V006__ migration.
