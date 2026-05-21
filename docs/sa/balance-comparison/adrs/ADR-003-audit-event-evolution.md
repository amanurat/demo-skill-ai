# ADR-003. Audit Event Schema — Extend Existing `AuditEventRecorded` (v1 → v2) vs. New Event Type

- **Status:** accepted
- **Date:** 2026-05-21
- **Deciders:** banking-solution-architect
- **Tags:** event-schema, audit, avro, schema-evolution, compliance
- **Resolves:** SUBDEC-003 (from BA-001); addresses DEP-003 in PM risk register

## Context and Problem

Every dashboard retrieval must emit an audit event per BoT IT-Risk Guidelines (BR-014, NFR §4, AC-001-H3). The audit event must carry:

- `eventType = BALANCE_INQUIRY` (new value, not in existing money-transfer enum: `TRANSFER_REQUESTED` etc.)
- `purpose = balance-inquiry` (NEW field; money-transfer v1 doesn't have this)
- `cacheHit` boolean (NEW field; meaningful only for read events)
- `accountCount` integer (NEW field; meaningful only for aggregated reads)
- Plus existing fields: `actorId`, `correlationId`, `timestamp`, `result`, `channel`.

The money-transfer audit event today (per money-transfer S3 §audit-service responsibility + S5 backend dev artifact) is `AuditEventRecorded v1` — a generic envelope with `eventType` enum, `actorId`, `correlationId`, `timestamp`, `result`, `payload` (free-form map). The Apicurio schema registry has it under subject `audit.event-recorded` with BACKWARD compatibility mode.

Three options for adding our fields:

1. **Extend `AuditEventRecorded v1` → v2 with optional fields** (BACKWARD-compatible).
2. **Create new `BalanceInquiryAudited` event type** with its own schema.
3. **Use the existing `payload` map** field to stash `purpose`, `cacheHit`, `accountCount` as string entries.

## Decision Drivers

- **BoT 7-year audit retention** — schema choices we make today bind future querying capabilities.
- **audit-service consumer must NOT need code change** — RISK-002 (deadline). audit-service is critical infrastructure; touching its code requires its own review cycle.
- **Apicurio BACKWARD compatibility** (money-transfer ADR-010) — any schema change must pass compat check.
- **Demo-day risk** — schema change must be uncontroversial.
- **Future query patterns** — compliance team will want to query "all audit events where `purpose=balance-inquiry`" and "how many BALANCE_INQUIRY events had `cacheHit=true`". Both should be cheap queries, not regex over a string payload.
- **DRY across services** — if every read-side service invents its own audit event type, audit-service grows a long enum and the query surface fragments.

## Considered Options

### Option A — Extend `AuditEventRecorded v1` → v2 with 3 OPTIONAL fields ← CHOSEN

Schema change:
```avro
v1:
  eventType, actorId, channel, correlationId, timestamp, result, payload (nullable map)

v2:  (add 3 optional fields with null defaults)
  + purpose:      ["null", "string"]   default null
  + cacheHit:     ["null", "boolean"]  default null
  + accountCount: ["null", "int"]      default null
```

- ✅ **BACKWARD-compatible:** old v1 producers (transfer-service) continue to emit valid messages; consumer reads them with null defaults.
- ✅ **FORWARD-compatible:** v1 consumers ignore the new fields gracefully (Avro behavior).
- ✅ **audit-service consumer code: zero change.** audit-service stores the event envelope as-is; the 3 new fields land as additional columns or JSON keys without parser changes.
- ✅ **Compliance queries are first-class:** `SELECT * WHERE purpose='balance-inquiry'` is an indexed column lookup, not a `payload LIKE '%purpose%'` regex.
- ✅ **Consistent event taxonomy:** one `AuditEventRecorded` event per audit emission, regardless of which service produces it. Discriminator is `eventType` enum value.
- ✅ **Apicurio compatibility check:** passes BACKWARD mode (optional with default = safe additive change).
- ⚠️ Topic naming: per money-transfer convention `<service>.<entity>.<event>.v<n>`, the new topic is `audit.event-recorded.v2`. Existing producers (transfer-service) continue publishing to `audit.event-recorded.v1`; audit-service consumes from both topics. **Operational implication:** DevOps must configure audit-service to consume both topics; alternative is to use a single topic + Apicurio handles version evolution. **Decision:** use a single topic `audit.event-recorded` (no version suffix) per Apicurio convention; topic carries any registered schema version; consumer uses Apicurio serializer to resolve. This matches Confluent/Apicurio idiomatic usage. **Override the project naming convention here** because Apicurio's schema-evolution model assumes single-topic-multi-version.

### Option B — New `BalanceInquiryAudited` event type with its own schema

- ✅ Cleanly scopes the new fields to the new use case.
- ✅ Schema validation is tighter — `purpose` is always present.
- ❌ **audit-service must change code** to consume the new event type — adds dependency + review cycle on a critical service.
- ❌ **Taxonomy fragmentation:** if every new read feature gets its own audit event type, compliance query surface gets messy (must `UNION` across many types).
- ❌ **More schemas to govern** — separate Apicurio subject, separate evolution rules.
- ❌ **Topic proliferation** — new topic = new Kafka partition planning, new consumer group, new monitoring.
- ❌ Goes against the existing money-transfer pattern: 14 event types share one audit subscription path; we'd be the first read-side service to fork.

### Option C — Reuse existing `payload` map for `purpose`, `cacheHit`, `accountCount` as stringified values

- ✅ Zero schema change.
- ✅ Zero coordination cost.
- ❌ **Loss of type safety:** `cacheHit: "true"` is a string; consumers can't filter by boolean.
- ❌ **Query unfriendly:** compliance team querying "% cache-hit rate by purpose" gets messy SQL extracting from JSON map values.
- ❌ **Naming collisions risk** if other services dump unrelated keys into the same `payload` map.
- ❌ Future-self / future-team will be confused why the structured fields are in a string map.

## Decision Outcome

**Chosen: Option A — extend `AuditEventRecorded` schema to v2 with 3 optional fields.**

The cost is one Apicurio schema registration (DevOps task, ~10 minutes). The benefits are durable: clean compliance queries, no audit-service code change, no taxonomy fragmentation, future-proof for other read services (loan-dashboard, statement-service) that need the same fields.

### Implementation Sequencing (critical to avoid demo-day breakage)

DevOps must register v2 schema in Apicurio **before** `balance-dashboard-service` is deployed. Order:

1. **D6:** DevOps registers `AuditEventRecorded` v2 Avro schema to Apicurio under subject `audit.event-recorded` with compatibility BACKWARD. Apicurio compatibility check must pass (it will; only-additive change with defaults).
2. **D7:** DevOps verifies audit-service consumer reads v2 messages OK (test in staging by manually producing one v2 message and checking it lands in audit_log table with the new columns/keys populated).
3. **D8:** `balance-dashboard-service` deploys to staging, begins emitting v2 messages.
4. **D9+:** transfer-service continues emitting v1 messages; both flow through the same topic without interference (Apicurio resolves schema version per-message).

### Choice of v2 fields (justifying each)

| Field | Type | Why optional with null default | Example value |
|---|---|---|---|
| `purpose` | `["null", "string"]` | Money-transfer existing producer emits no purpose; null preserves compatibility. Future producers can populate. | `"balance-inquiry"` |
| `cacheHit` | `["null", "boolean"]` | Meaningful only for read events backed by cache; transfer write events leave it null. | `true`, `false`, `null` |
| `accountCount` | `["null", "int"]` | Meaningful only for aggregated reads; null for single-account or write events. | `0`, `3`, `null` |

These three fields cover not just balance-dashboard but plausibly any future aggregated read endpoint (statement viewer, loan portfolio viewer). Reusable.

### audit-service Storage Side (no code change verification)

The existing `audit-service` (per money-transfer S3 ADR-012) stores events into `audit_log` with the envelope columns indexed and the `payload` map as JSONB. With v2 the audit-service has two paths:

- **Preferred:** add 3 columns (`purpose`, `cache_hit`, `account_count`) to `audit_log` via a Flyway migration in audit-service. Indexed for compliance queries. This is the "first-class column" approach.
- **Acceptable for demo:** if adding columns is too disruptive on D6, audit-service can read the 3 new envelope fields and serialize them into the existing `payload` JSONB column. No schema change to audit-service DB. Slower compliance queries (JSONB path) but unblocked for demo.

**Recommendation:** DevOps + audit-service owner confirm by D5 which path. SA preference: first-class columns (cleaner for the 7-year audit horizon). If audit-service team is unavailable on D5, fall back to JSONB path and file a follow-up ADR for the column promotion in a future sprint.

### Consequences

- ✅ One additive Avro change covers this sprint AND future read-aggregation features.
- ✅ Compliance queries (`purpose=balance-inquiry`, cacheHit aggregations) are efficient.
- ✅ Zero code change required in audit-service for the event consumption path (envelope is opaque to the consumer beyond storing it).
- ⚠️ DevOps must register the v2 schema in Apicurio before BDS deploy — sequencing risk if missed.
- ⚠️ The 3 new fields are nullable for v1; consumers must handle null when querying. Documented in audit-service runbook.
- ⚠️ Topic naming: deviates from `<service>.<entity>.<event>.v<n>` project convention by using a single topic `audit.event-recorded` (no version suffix) per Apicurio's schema-evolution model. This is consistent with money-transfer's existing usage and Apicurio idiom. Calling it out explicitly so reviewers don't flag it.

## Rejected Alternatives Summary

| Option | Why rejected |
|---|---|
| New `BalanceInquiryAudited` event type | Requires audit-service code change; fragments taxonomy; topic proliferation |
| Stuff new fields into existing `payload` map | Loses type safety; compliance queries become messy |
| Wait for audit-service team to add fields | Schedule risk; we own the producer side, can make additive change unilaterally |

## Links

- [ADR-001 service boundary](ADR-001-service-boundary.md)
- [Event flows §3 audit event schema](../event-flows.md)
- [Service decomposition §3](../service-decomposition.md)
- Money-transfer S3 ADR-010: Apicurio schema registry
- Money-transfer S3 ADR-012: audit immutability (extended by this ADR's retention claim)
- BA SUBDEC-003: `docs/ba/balance-comparison/handoff-ba-001.json`
- PM DEP-003: `docs/pm/balance-comparison/risk-register.md`
