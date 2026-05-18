# Persistence — JPA, Hibernate, Flyway, HikariCP

Reference loaded on demand by `spring-boot-banking` skill. Cover database access patterns for banking services.

## JPA / Hibernate Rules

### Entity Design
- **Entity per aggregate root** — child entities lazy-loaded
- **`@Version`** for **optimistic locking** on all money-related entities (Account, Transfer, Balance, etc.)
- **No `OneToMany` to large collections** — page or use child repo
- Use `@EmbeddedId` for composite keys when natural
- Prefer immutable IDs (UUID) for distributed systems

### Query Strategy
- **N+1 prevention** — prefer JPQL/Criteria with explicit fetch joins
- Enable Hibernate SQL logging in dev (`spring.jpa.show-sql=true`)
- For complex reads, prefer **projection DTOs** (interface or class projection) over fetching full entities
- Use `@QueryHint` for query-level cache hints when applicable

### Money Handling
- **Money columns: `NUMERIC(19,4)`** in DB, **`BigDecimal`** in Java
- **NEVER use `FLOAT` / `DOUBLE` for money** — precision loss
- For `BigDecimal` comparisons use `compareTo()` not `equals()` (scale matters)

### Transactions
- `@Transactional` only at application/use-case layer (`application/usecase/`)
- Use `@Transactional(readOnly = true)` for queries (Hibernate optimizes)
- Avoid `@Transactional` on `@RestController` — wrong layer
- For long-running orchestrations, use Saga pattern (see [idempotency-saga-outbox.md](idempotency-saga-outbox.md)), not big transactions

## Flyway Migrations

### Rules
- **One change per file** — atomic, reviewable
- **Naming:** `V<seq>__<verb>_<object>.sql` — e.g. `V001__create_transfers.sql`, `V002__add_index_transfers_created_at.sql`
- **Reversible** — provide `down` SQL in the handoff payload (Flyway itself doesn't auto-revert; this is for rollback runbook)
- **Never edit a released migration** — create a new one
- Header comment: ticket ID, author agent, rationale

### Required Schema Conventions
- **Primary key**: `UUID` (banking → UUID for distribution; avoid sequential IDs that leak business volume)
- `created_at`, `updated_at` as `TIMESTAMPTZ NOT NULL DEFAULT now()`
- **Optimistic lock**: `version BIGINT NOT NULL DEFAULT 0` on money entities
- **Money**: `NUMERIC(19,4)` — never `FLOAT`
- **Currency**: `CHAR(3)` ISO 4217
- **Audit FK**: link to `audit_log_id` where applicable
- **Indexes**: explicit; document reason in migration comment
- **Constraints**: NOT NULL, CHECK, UNIQUE wherever applicable

### Example Migration Header
```sql
-- V003__add_transfer_idempotency.sql
-- Ticket: TRANSFER-42
-- Author: banking-tech-lead
-- Rationale: Support Idempotency-Key for POST /transfers (TTL 24h)
-- Down: DROP TABLE transfer_idempotency;

CREATE TABLE transfer_idempotency (
    key_hash         VARCHAR(64) PRIMARY KEY,
    transfer_id      UUID NOT NULL REFERENCES transfers(transfer_id),
    request_checksum VARCHAR(64) NOT NULL,
    cached_response  JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_transfer_idempotency_expires_at
    ON transfer_idempotency (expires_at)
    WHERE expires_at > now();
```

## HikariCP Connection Pool

- **`maximum-pool-size`** baseline: `2 × #CPUs + 1` — tune via load test
- **`connection-timeout`** ≤ 5 seconds
- **`leak-detection-threshold`** = 30 seconds (catches forgotten close in dev/staging)
- **`idle-timeout`** = 10 minutes
- Separate pool per logical workload if read/write contention emerges

## Read Replicas

For heavy-read services (`account-service` for balance queries):
- Routing via AbstractRoutingDataSource or Spring `@Transactional(readOnly=true)` dispatch
- Tolerate replication lag — surface "may be stale by X ms" in API contract
- **Never read from replica for write-side decisions** (e.g., balance check during debit)

## Partitioning

For high-volume tables (`audit_log`, `transfers` over years):
- Partition by month (`PARTITION BY RANGE (created_at)`)
- Drop old partitions per retention policy (audit ≥ 7 yr, transfers per regulation)
- Use pg_partman for automated partition maintenance

## When to Use This Reference

- Designing a new entity / table for a banking service
- Writing a Flyway migration
- Debugging a query performance issue
- Sizing HikariCP for a load test
- Reviewing a PR that introduces JPA entities or migrations
