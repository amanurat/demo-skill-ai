# ADR Index — Balance Comparison Dashboard

All Architecture Decision Records for the `balance-comparison` feature (`SPRINT-2026-Q2-BC-01`).

ADR-001 through ADR-004 are owned by `banking-solution-architect` and live under `docs/sa/balance-comparison/adrs/`.
ADR-005 through ADR-007 are owned by `banking-tech-lead` and live under `docs/tech-lead/balance-comparison/adrs/`.

---

| # | Title | Decision summary | Status | Owner | Path |
|---|---|---|---|---|---|
| ADR-001 | New `balance-dashboard-service` microservice | Stand up a new `balance-dashboard-service` sibling service in the Accounts bounded context rather than adding an endpoint to the existing `account-service`. Rationale: SRP, independent deploy/rollback, cache namespace isolation, CQRS read-side projection, and protection of `account-service` from dashboard load on the transfer critical path. | Accepted | `banking-solution-architect` | `docs/sa/balance-comparison/adrs/ADR-001-service-boundary.md` |
| ADR-002 | Redis TTL-only cache (no explicit invalidation) | v1 uses a 30-second TTL-only cache (`SETEX`) — no event-driven invalidation. Rationale: event-driven invalidation requires resolving `customerId` from `accountId` on every `AccountDebited/Credited` event, which is too costly for the demo deadline. Staleness is bounded at 30 seconds and communicated to the user via `meta.freshness` and per-row `isStale`. Event-driven invalidation is deferred to v1.1. | Accepted | `banking-solution-architect` | `docs/sa/balance-comparison/adrs/ADR-002-cache-strategy.md` |
| ADR-003 | Audit Avro schema v1 → v2 (BACKWARD-compatible extension) | Extend the existing `AuditEventRecorded` Avro schema to v2 by adding three optional (`union[null, ...]`) fields: `purpose`, `cacheHit`, `accountCount`. Do not create a separate `BalanceInquiryAudited` event type. Rationale: backward compatibility with the existing audit-service consumer, no consumer code change required, first-class fields for compliance queries (`purpose=balance-inquiry`), and Apicurio BACKWARD compatibility mode satisfaction. | Accepted | `banking-solution-architect` | `docs/sa/balance-comparison/adrs/ADR-003-audit-event-evolution.md` |
| ADR-004 | Server-side balance ranking | Rank accounts by `balance` DESC with `accountId` ASC tie-break inside `balance-dashboard-service` before caching. The cached result is pre-ranked; the frontend renders in array order and must not re-sort. Rationale: cache-friendly (no per-request re-computation), deterministic across all clients, ARIA `aria-label="Account {rank} of {total}"` is correct at first render, and audit `accountCount` originates from the same code path as the response. | Accepted | `banking-solution-architect` | `docs/sa/balance-comparison/adrs/ADR-004-server-side-ranking.md` |
| ADR-005 | Style Dictionary for design token consumption (CI-enforced, dual-emit) | Adopt Style Dictionary v4+ with `style-dictionary-utils` W3C transform pack as the locked tool for converting `docs/design/_shared/tokens.json` into `frontend/src/styles/tokens.scss` (SCSS variables) and `frontend/src/styles/tokens.css` (CSS custom properties). Alias resolution at build time. CI job `design-tokens-up-to-date` fails the PR if generated files are stale. Angular import order and component-layer rules are locked. Cross-feature policy for the entire banking monorepo. | Accepted | `banking-tech-lead` | `docs/tech-lead/balance-comparison/adrs/ADR-005-design-token-consumption.md` |
| ADR-006 | `CustomerIdResolver` pattern (IDOR defense, structurally enforced) | Adopt a two-collaborator pattern to make using the wrong `customerId` source structurally impossible: `IborCheckFilter` is the sole permitted reader of `X-Customer-Id` header (IDOR detection only); `CustomerIdResolver` is the sole abstraction that derives `customerId` from JWT `sub` for all business logic. ArchUnit rule `CustomerIdSourceRule` enforces both invariants at compile time. Resolves Security Condition C-3. | Accepted | `banking-tech-lead` | `docs/tech-lead/balance-comparison/adrs/ADR-006-customerid-resolver-pattern.md` |
| ADR-007 | `AuditEventPublisher` hexagonal port contract (metadata-only, async fire-and-forget, no outbox) | Define `AuditEventPublisher` as a hexagonal output port with `AuditEventRecord` as the metadata-only value object (9 fields, no balance/account data). Implement as `KafkaAuditEventPublisher` (async fire-and-forget, `producer.send()` with callback — no `.get()`, no transactional outbox). Three mandatory call sites: SUCCESS, FORBIDDEN, ERROR — cache layer never short-circuits audit (BR-014). Resolves Security Condition C-2 (PDPA §22 data minimization). At-most-one-event-loss-per-Kafka-outage is an accepted v1 risk (SA ADR-001). | Accepted | `banking-tech-lead` | `docs/tech-lead/balance-comparison/adrs/ADR-007-audit-event-publisher.md` |

---

## Cross-references

- OpenAPI spec: `docs/tech-lead/balance-comparison/openapi/balance-dashboard-service.openapi.yaml`
- DB schema: `docs/tech-lead/balance-comparison/db-schema.md`
- Implementation notes: `docs/tech-lead/balance-comparison/implementation-notes.md`
- Security review (G8 final): `docs/security/balance-comparison/security-review-final-2.md`
- API reference: `docs/generated/balance-comparison/api-reference.md`
- CHANGELOG: `docs/generated/balance-comparison/CHANGELOG.md`
- Developer guide: `docs/generated/balance-comparison/developer-guide.md`

---

*ADR Index · balance-comparison · banking-docs · 2026-05-22*
