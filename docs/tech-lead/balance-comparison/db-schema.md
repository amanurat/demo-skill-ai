# DB Schema Decisions — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Artifact:** TL-001
> **Companion:** [openapi/](./openapi/), [adrs/](./adrs/), [implementation-notes.md](./implementation-notes.md)
> **SA decisions ratified:** [ADR-001](../../sa/balance-comparison/adrs/ADR-001-service-boundary.md), [ADR-002](../../sa/balance-comparison/adrs/ADR-002-cache-strategy.md), [ADR-003](../../sa/balance-comparison/adrs/ADR-003-audit-event-evolution.md)

---

## 1. Summary — Database Footprint per Service

| Service | RDBMS | New tables | Flyway migrations | Redis namespace |
|---|---|---|---|---|
| `balance-dashboard-service` (NEW) | **NONE** | 0 | **0** — explicitly do NOT scaffold `src/main/resources/db/migration/` | `balance-dashboard:customer:{customerId}` |
| `account-service` (EXTENDED) | Existing | 0 (new endpoint queries existing columns) | 0 | Existing |
| `audit-service` (EXTENDED) | Existing | 0 (v1 — JSONB fallback; see §4) | 0 | Existing |

> **Quality-gate self-check:** `grep -r 'src/main/resources/db/migration' backend/balance-dashboard-service/` MUST return empty after scaffolding (per SA open item #7).

---

## 2. `balance-dashboard-service` — Explicit NO-RDBMS Decision

### 2.1 What "no RDBMS" means concretely

- No PostgreSQL database connection in `application.yml`.
- No `spring-boot-starter-data-jpa` dependency in `pom.xml`.
- No `spring-boot-starter-jdbc` dependency.
- No `flyway-core` dependency.
- No `db/migration/` directory under `src/main/resources/`.
- No `DataSource` bean — Spring Boot's `DataSourceAutoConfiguration` MUST be excluded:

  ```java
  @SpringBootApplication(exclude = {
      DataSourceAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class
  })
  public class BalanceDashboardServiceApplication { ... }
  ```

### 2.2 Why (recap)

Per [ADR-001 §Decision Outcome](../../sa/balance-comparison/adrs/ADR-001-service-boundary.md): the service is a thin CQRS read-side aggregator. Its state lives in (a) the upstream `account-service` (authoritative) and (b) Redis (derived cache). Introducing an RDBMS would:
- Add a 2-phase-commit-like surface area between Redis and Postgres that the feature does not need.
- Tempt future engineers to add an `audit_outbox` table (deliberately rejected for v1 per SA event-flows §3.4).
- Increase ops burden (one more Flyway pipeline, one more backup target, one more failover scenario) without benefit.

### 2.3 Redis cache schema (the only "data store" decision)

| Property | Value | Rationale |
|---|---|---|
| Key pattern | `balance-dashboard:customer:{customerId}` | Per SA service-decomposition §1.2; one entry per customer; UUID is opaque so prefix scans are safe |
| Key length | ≤ 60 bytes | Well below Redis 512MB key limit |
| Value format | JSON-encoded `BalanceDashboardResponse` (see [openapi](./openapi/balance-dashboard-service.openapi.yaml#BalanceDashboardResponse)) | Cache stores post-rank, post-filter result; warm hits skip Ranker + EligibilityPolicy |
| Serializer | Jackson `ObjectMapper` (single shared bean) | `BigDecimal` configured with `WRITE_BIGDECIMAL_AS_PLAIN=true` to preserve precision per ADR/openapi |
| Value size | ~2 KB per customer (10 accounts) | 50 peak × 2 KB = 100 KB cluster footprint — negligible |
| TTL | **30 seconds** via `SETEX` | Per [ADR-002](../../sa/balance-comparison/adrs/ADR-002-cache-strategy.md); never use `EXPIRE` separately (race window) |
| Encryption at rest | AES-256-GCM (cluster default) | Per Security C-4; DevOps verifies cluster capability before BDS deploy |
| Cross-customer isolation | Key scoped to single `customerId` from JWT `sub` | NEVER bulk-fetch / scan across customers; Redis `KEYS` and `SCAN` are forbidden in production code (use only in ops scripts) |
| Eviction policy | Cluster default (`allkeys-lru`) — TTL is the only expected expiration | No explicit `DEL` from BDS code in v1 (per ADR-002) |
| Connection pool | Lettuce, 16 connections per pod | Per SA service-decomposition §1.2; verify against shared cluster `maxclients` (open item — see [implementation-notes §risk](./implementation-notes.md#redis-pool-risk)) |
| Failure mode | **Fail-open** | On Lettuce exception → log + metric `cache_miss_reason=REDIS_UNAVAILABLE` → fall through to AccountClient. NO circuit breaker on Redis (cheap fail-fast retry per request). |

### 2.4 Redis payload Java type (cache contract)

The cached JSON deserializes back into a strongly-typed record (immutable, no Spring deps — pure domain):

```java
package com.bank.balancedashboard.infrastructure.cache;

public record CachedBalanceDashboard(
    List<AccountView> accounts,    // ranked, eligibility-filtered
    ResponseMeta meta              // freshness, cacheHit, accountCount, correlationId
) {
    // Jackson-deserializable via canonical record constructor
}
```

> **Important:** When serving from cache, the service MUST recompute `meta.cacheHit = true` and refresh `meta.correlationId` to the *current* request's trace ID. Only `accounts[]` + `meta.freshness=snapshot` come verbatim from the cache. (Otherwise audit and trace correlation break.)

---

## 3. `account-service` — Repository Method Signature

The new endpoint `GET /api/v1/accounts?customerId={uuid}` queries existing tables — **no new migration**. The repository contract:

```java
package com.bank.account.domain.repository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // ... existing methods ...

    /**
     * Returns every account owned by `customerId` regardless of status or type.
     * Eligibility filtering is the caller's responsibility per SUBDEC-002.
     *
     * Query path: indexed scan on accounts(customer_id).
     * Expected cardinality: N <= 10 for retail customers.
     *
     * NOTE: No ORDER BY at the repository layer — BalanceDashboardService applies
     * server-side ranking deterministically (ADR-004). account-service callers that
     * need a different ordering can sort client-side; we do NOT mix sort responsibility
     * across the service boundary.
     */
    @Query("""
        SELECT a FROM Account a
        WHERE a.customerId = :customerId
        """)
    List<Account> findAllByCustomerId(@Param("customerId") UUID customerId);
}
```

### 3.1 Index requirement (verify, do not author)

`accounts(customer_id)` index MUST already exist on `account-service`'s `accounts` table — money-transfer's `getAccountInfo(accountId)` does not use it, but transfer flow does (via `customer_id` filtering on payee verification). **TL action for backend-dev:** confirm by inspecting `account-service`'s existing Flyway migrations during scaffolding; if absent, file as `V<next>__add_accounts_customer_id_idx.sql` in `account-service` (NOT `balance-dashboard-service`).

### 3.2 Why no new migration in `account-service`?

Per SA service-decomposition §3.1: `account-service` is REUSED with a new endpoint only — schema is unchanged. The new endpoint reads the same `accounts` table that money-transfer's `getAccountInfo` reads. Any column additions (e.g., `accrued_interest` if SUBDEC-001 falls through — see [implementation-notes §subdec-001-fallback](./implementation-notes.md#subdec-001-fallback)) would be `account-service`/`ledger-service` team scope, not BDS.

---

## 4. `audit-service` — Storage Path Decision (resolves SA open item #4)

> **Decision:** **JSONB for v1**. Promote to first-class columns in a follow-up sprint.

### 4.1 Trade-off recap (from [SA ADR-003 §audit-service Storage Side](../../sa/balance-comparison/adrs/ADR-003-audit-event-evolution.md))

| Option | Pros | Cons |
|---|---|---|
| First-class columns (`purpose`, `cache_hit`, `account_count`) via Flyway in audit-service | Indexed compliance queries; cleaner DDL | Requires audit-service team review cycle on D5; adds a critical-service migration to the sprint — RISK-002 (deadline) exposure |
| JSONB storage in existing `payload` column | Zero audit-service code/DDL change; unblocks demo immediately | Slower compliance queries (JSONB extract); column promotion = follow-up sprint |

### 4.2 Why JSONB for v1

1. **Demo-day discipline.** RISK-002 (10-day deadline) — every avoidable cross-team migration we cut from the critical path buys us schedule slack.
2. **Zero coordination cost.** No audit-service team review window required.
3. **Reversible decision.** Promotion to columns is a follow-up Flyway migration with `INSERT INTO ... SELECT (payload->>'purpose')...` backfill — well-understood pattern.
4. **PostgreSQL JSONB performance is acceptable for compliance batch queries.** Even at 7-year retention the audit_log JSONB extract path is < 1s for typical compliance queries with a partial index on `eventType`.

### 4.3 Consumer-side JSON shape (what `balance-dashboard-service` puts in `payload`)

The Avro v2 envelope carries the 3 new fields at top level. `audit-service` already has logic to merge top-level Avro extra fields into the JSONB `payload` column under their own key. So balance-dashboard-service's emitted record:

```json
{
  "eventType": "BALANCE_INQUIRY",
  "actorId": "11111111-2222-3333-4444-555555555555",
  "channel": "MOBILE_BANKING",
  "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60",
  "timestamp": 1747900800000,
  "result": "SUCCESS",
  "payload": null,
  "purpose": "balance-inquiry",
  "cacheHit": true,
  "accountCount": 3
}
```

…lands in `audit_log` as:

| audit_log column | Value source |
|---|---|
| `event_type` | `"BALANCE_INQUIRY"` |
| `actor_id` | `"11111111-..."` |
| `channel` | `"MOBILE_BANKING"` |
| `correlation_id` | `"7f4e2b91-..."` |
| `timestamp_utc` | `2026-05-22 03:00:00+00` |
| `result` | `"SUCCESS"` |
| `payload` (JSONB) | `{"purpose": "balance-inquiry", "cacheHit": true, "accountCount": 3}` |

### 4.4 Compliance query examples (v1, JSONB)

```sql
-- "How many balance inquiries served from cache in the past 7 days?"
SELECT
    COUNT(*) FILTER (WHERE (payload->>'cacheHit')::boolean = true) AS cache_hits,
    COUNT(*) FILTER (WHERE (payload->>'cacheHit')::boolean = false) AS cache_misses
FROM audit_log
WHERE event_type = 'BALANCE_INQUIRY'
  AND timestamp_utc >= NOW() - INTERVAL '7 days';

-- "All balance-inquiry events for a given customer (PDPA data subject request)"
SELECT *
FROM audit_log
WHERE event_type = 'BALANCE_INQUIRY'
  AND actor_id = '11111111-2222-3333-4444-555555555555'
  AND timestamp_utc >= NOW() - INTERVAL '7 years';
```

A partial index on `event_type` is sufficient for v1 throughput (audit-service already has one). A GIN index on `payload` is optional — not required for v1 demo.

### 4.5 Promotion criteria (v1.1 follow-up)

Trigger a follow-up Flyway migration in `audit-service` when ANY of:
1. Compliance reports take > 5s on JSONB queries (production scale).
2. Auditors request indexed queries on `purpose` or `cacheHit`.
3. Additional read services (loan-dashboard, statement-service) start emitting the same fields and JSONB grows in volume.

### 4.6 What is explicitly NOT stored in audit (Security C-2)

Per [Security C-2](../../security/balance-comparison/early-review-consent-coverage.md#condition-c-2-audit-event-schema--metadata-only) — payload MUST be metadata-only:

| Field | Stored in audit? | Reason |
|---|---|---|
| `balance` (per-account) | **NEVER** | 7-year retention of balance history is purpose-stretch (PDPA §22) |
| `accountNumber` (full or masked) | **NEVER** | Defense-in-depth — no account-level identifier in audit |
| `accountId` | **NEVER** | Same reason |
| `balanceAsOf` per account | **NEVER** | Linkable to balance via timestamp correlation |
| `accounts[]` array | **NEVER** | Don't reconstruct response payload in audit |
| `accountCount` (aggregate integer only) | YES — Avro v2 first-class | Aggregate is metadata; individual balances cannot be reconstructed |
| `cacheHit` (boolean) | YES — Avro v2 first-class | Audit-side observability of cache-bypass attempts |

The contract test in `balance-dashboard-service` MUST assert: serialized audit record byte-grep does not match `"balance"`, `"accountId"`, `"accountNumber"`, or `"accounts"` keys (regex over the JSON envelope). See [implementation-notes §audit-publisher-contract-test](./implementation-notes.md#audit-publisher-contract-test).

---

## 5. Apicurio Avro Schema — `AuditEventRecorded v2` (Avro-IDL reference)

The Avro schema BDS produces against. Owned by DevOps to register; included here for canonical reference:

```json
{
  "type": "record",
  "name": "AuditEventRecorded",
  "namespace": "com.bank.compliance.audit.v2",
  "doc": "Banking audit event envelope. v2 adds 3 optional fields (purpose, cacheHit, accountCount) — BACKWARD-compatible with v1.",
  "fields": [
    { "name": "eventType",     "type": "string",
      "doc": "Discriminator. Existing: TRANSFER_REQUESTED, etc. NEW: BALANCE_INQUIRY." },
    { "name": "actorId",       "type": "string",
      "doc": "UUID of acting customer (from JWT sub)." },
    { "name": "channel",       "type": { "type": "enum", "name": "Channel",
                                         "symbols": ["MOBILE_BANKING", "WEB", "API"] } },
    { "name": "correlationId", "type": "string",
      "doc": "OTel trace ID (lowercase UUID form)." },
    { "name": "timestamp",     "type": "long",
      "doc": "Epoch millis UTC." },
    { "name": "result",        "type": { "type": "enum", "name": "Result",
                                         "symbols": ["SUCCESS", "FAILURE", "FORBIDDEN", "ERROR"] } },
    { "name": "payload",       "type": ["null", { "type": "map", "values": "string" }],
                               "default": null,
      "doc": "Free-form map for legacy v1 producers. v2 producers SHOULD leave null." },
    { "name": "purpose",       "type": ["null", "string"],   "default": null,
      "doc": "NEW v2. e.g. 'balance-inquiry'." },
    { "name": "cacheHit",      "type": ["null", "boolean"],  "default": null,
      "doc": "NEW v2. True if response served from BDS Redis cache." },
    { "name": "accountCount",  "type": ["null", "int"],      "default": null,
      "doc": "NEW v2. Aggregate account count returned (0 for empty state)." }
  ]
}
```

> **Critical:** Java package name `com.bank.compliance.audit.v2` MUST match the Avro `namespace` exactly — otherwise Confluent/Apicurio deserializer will reject records at the consumer (see banking-tech-lead skill gotcha). DevOps verifies during D7 smoke test.

---

## 6. ERD — Empty (intentional)

```mermaid
%% balance-dashboard-service owns NO RDBMS entities. This is an intentional empty ERD.
%% The "data" the service touches lives in:
%%   - account-service.accounts (read-only via HTTP)
%%   - Redis cluster (key-value, schemaless — see §2.3)
%%   - audit-service.audit_log (via Kafka — see §4)
erDiagram
    NOTE_NO_RDBMS {
        note "balance-dashboard-service has no relational schema by design (ADR-001)"
    }
```

---

## 7. Cross-references

- [ADR-001 service boundary (no RDBMS rationale)](../../sa/balance-comparison/adrs/ADR-001-service-boundary.md)
- [ADR-002 cache strategy (Redis TTL 30s)](../../sa/balance-comparison/adrs/ADR-002-cache-strategy.md)
- [ADR-003 audit event v1 → v2](../../sa/balance-comparison/adrs/ADR-003-audit-event-evolution.md)
- [SA event-flows §2 cache strategy detail](../../sa/balance-comparison/event-flows.md)
- [Security C-2 audit metadata-only](../../security/balance-comparison/early-review-consent-coverage.md#condition-c-2-audit-event-schema--metadata-only)
- [Security C-4 Redis at-rest encryption](../../security/balance-comparison/early-review-consent-coverage.md#condition-c-4-redis-encryption-at-rest-verify-cluster-capability)
- [OpenAPI spec for BDS](./openapi/balance-dashboard-service.openapi.yaml)
- [OpenAPI delta for account-service](./openapi/account-service-extension.openapi.yaml)
- [Implementation notes](./implementation-notes.md)
