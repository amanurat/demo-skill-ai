-- V002__create_transfer_idempotency.sql
-- Ticket: TRANSFER-18
-- Author: banking-tech-lead
-- Rationale: Per-service idempotency table per ADR-002 of solution-arch.
--            Holds SHA-256 hash of the Idempotency-Key (scoped per customer),
--            request payload checksum for conflict detection (US-003 AC4),
--            and the cached response body for replay (US-003 AC1/AC2).
--            Atomic with the business write — same DB transaction as transfers.
-- Down (runbook only):
--   DROP TABLE IF EXISTS transfer_idempotency;

-- DevOps smoke-test fix (S9): CHAR(64) -> VARCHAR(64) to match Hibernate mapping.
-- CHAR(64) is bpchar in Postgres; Hibernate @Column(length=64) maps to varchar.
-- SHA-256 hex strings are fixed-length in practice but varchar is the correct type
-- for a string column without a padding guarantee. No data migration required.
CREATE TABLE transfer_idempotency (
    key_hash             VARCHAR(64)     NOT NULL,
    owner_customer_id    UUID            NOT NULL,
    transfer_id          UUID,
    request_checksum     VARCHAR(64)     NOT NULL,
    result_status        VARCHAR(32)     NOT NULL
        CHECK (result_status IN (
            'PENDING','COMPLETED','FAILED','REJECTED','CONFLICT'
        )),
    -- DevOps smoke-test fix (S9): SMALLINT -> INTEGER to match Java int field
    -- in IdempotencyJpaEntity.cachedResponseCode. Hibernate maps primitive int -> int4 (INTEGER).
    cached_response_code INTEGER         NOT NULL,
    -- DevOps smoke-test fix (S9): JSONB -> TEXT to match @Column(columnDefinition="TEXT")
    -- in IdempotencyJpaEntity. TEXT allows portability with H2 in unit tests;
    -- JSON content validated at application layer.
    cached_response_body TEXT            NOT NULL,
    correlation_id       VARCHAR(64)     NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_transfer_idempotency PRIMARY KEY (key_hash, owner_customer_id),
    CONSTRAINT fk_transfer_idempotency_transfer
        FOREIGN KEY (transfer_id) REFERENCES transfers(transfer_id),
    CONSTRAINT chk_transfer_idempotency_ttl
        CHECK (expires_at > created_at)
);

COMMENT ON TABLE transfer_idempotency IS
    'Idempotency-Key store, SHA-256 hashed, scoped per (key, customer). TTL 24h. See ADR-013.';

-- Read pattern: hourly TTL purge job (ADR-013 / RISK-004).
CREATE INDEX idx_transfer_idempotency_expires_at
    ON transfer_idempotency (expires_at);
COMMENT ON INDEX idx_transfer_idempotency_expires_at IS
    'Read pattern: TTL cleanup job DELETE WHERE expires_at < now() LIMIT N.';
