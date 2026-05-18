# DB Schema & Flyway Authoring Rules — Tech Lead Perspective

Reference loaded on demand by `openapi-flyway-standards` skill. Covers what the **Tech Lead** writes into the handoff payload (`db_schema.migration_files[]`). For implementation-time concerns (JPA entity mapping, query tuning, HikariCP sizing) see the cross-link at the bottom.

## DB Schema Standards (authoring checklist)

Apply every rule, every time. Justify any deviation in an ADR.

- **Primary key**: `UUID` for banking (distribution-friendly, no business-volume leakage). `BIGSERIAL` only for non-sensitive internal tables.
- **Audit timestamps**: `created_at`, `updated_at` as `TIMESTAMPTZ NOT NULL DEFAULT now()`.
- **Optimistic lock**: `version BIGINT NOT NULL DEFAULT 0` on every money-bearing entity (Account, Transfer, Balance, Ledger, etc.).
- **Money columns**: `NUMERIC(19,4)` — never `FLOAT` / `DOUBLE`.
- **Currency**: `CHAR(3)` ISO 4217.
- **Audit linkage**: FK to `audit_log_id` (or equivalent) wherever a state change is regulated.
- **Indexes**: explicit; every index has a one-line comment in the migration explaining the read pattern it serves.
- **Constraints**: `NOT NULL`, `CHECK`, `UNIQUE` wherever applicable. Constraints belong in the DB, not just in code.

## Flyway Migration Rules (authoring)

- **One change per file** — atomic, reviewable, easy to revert from runbook.
- **Naming**: `V<seq>__<verb>_<object>.sql` — e.g. `V001__create_transfers.sql`, `V012__add_index_transfers_created_at.sql`.
- **Reversible**: include `down_sql` in the handoff payload. Flyway does not auto-run it; this is the rollback runbook source of truth.
- **Never edit a released migration** — author a new one.
- **Header comment** mandatory: ticket ID, author agent, rationale, down command.

### Example — Authored Migration

```sql
-- V001__create_transfers.sql
-- Ticket: TRANSFER-17
-- Author: banking-tech-lead
-- Rationale: Initial transfers table for Money Transfer feature (Saga-driven).
-- Down: DROP TABLE transfers;

CREATE TABLE transfers (
    transfer_id        UUID         PRIMARY KEY,
    from_account_id    UUID         NOT NULL,
    to_account_id      UUID         NOT NULL,
    amount             NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency           CHAR(3)      NOT NULL,
    status             VARCHAR(32)  NOT NULL
        CHECK (status IN ('REQUESTED','RESERVED','POSTED','FAILED','COMPENSATED')),
    idempotency_key    VARCHAR(64)  NOT NULL,
    audit_log_id       UUID         NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_idem UNIQUE (idempotency_key)
);

-- Read pattern: list transfers per account, latest first.
CREATE INDEX idx_transfers_from_account_created_at
    ON transfers (from_account_id, created_at DESC);
```

### Handoff Payload Shape (recap)

Inside the Tech Lead handoff JSON:

```json
"db_schema": {
  "migration_files": [
    {
      "path": "backend/transfer-service/src/main/resources/db/migration/V001__create_transfers.sql",
      "content": "<full SQL above>",
      "reversible": true,
      "down_sql": "DROP TABLE transfers;"
    }
  ],
  "erd_mermaid": "erDiagram ..."
}
```

## Authoring-Time Anti-Patterns

- Money column declared as `FLOAT` or `DOUBLE PRECISION`.
- Missing `version` column on an entity that holds balance / amount.
- Index added with no comment explaining the read pattern.
- Migration that drops or alters a column without a `down_sql` companion.
- `DELETE` statements against audit / financial tables — always archive, never delete.
- Reusing an existing `V<seq>__` file to "fix" a migration that already shipped.

## Cross-Link — Developer / Runtime Concerns

The Tech Lead **authors** the schema; the Backend Dev **implements** against it. For the implementation-side reference covering JPA entity design, query strategy (N+1, projections), HikariCP tuning, read replicas, and partitioning, see:

- [`spring-boot-banking/references/persistence-jpa-flyway.md`](../../spring-boot-banking/references/persistence-jpa-flyway.md)

Keep authoring concerns here; keep runtime / mapping concerns there. If a rule must live in both places, define it here and link from the other side.
