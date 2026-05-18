-- V004__create_outbox.sql
-- Ticket: TRANSFER-20
-- Author: banking-tech-lead
-- Rationale: Transactional outbox per architect ADR-003.
--            Business write + outbox row committed in the same DB transaction;
--            background relay polls and publishes to Kafka with SKIP LOCKED.
--            Marks dispatched=true on Kafka ack. See ADR-014 for poll cadence.
-- Down (runbook only):
--   DROP TABLE IF EXISTS transfer_outbox;

CREATE TABLE transfer_outbox (
    outbox_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type     VARCHAR(64)     NOT NULL
        CHECK (aggregate_type IN ('TRANSFER','AML_CASE')),
    aggregate_id       UUID            NOT NULL,
    event_type         VARCHAR(64)     NOT NULL
        CHECK (event_type IN (
            'TransferRequested',
            'TransferCompleted',
            'TransferFailed',
            'TransferCompensated',
            'AmlThresholdBreached'
        )),
    event_id           UUID            NOT NULL,
    -- SMALLINT matches Java short in OutboxJpaEntity.schemaVersion (Hibernate maps short -> int2/SMALLINT)
    schema_version     SMALLINT        NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    topic              VARCHAR(128)    NOT NULL,
    partition_key      VARCHAR(128)    NOT NULL,
    -- DevOps smoke-test fix (S9): JSONB -> TEXT to match @Column(columnDefinition="TEXT") in OutboxJpaEntity
    headers            TEXT            NOT NULL DEFAULT '{}',
    payload            TEXT            NOT NULL,
    correlation_id     VARCHAR(64)     NOT NULL,
    dispatched         BOOLEAN         NOT NULL DEFAULT false,
    dispatched_at      TIMESTAMPTZ,
    attempt_count      INTEGER         NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error         TEXT,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfer_outbox_event_id UNIQUE (event_id)
);

COMMENT ON TABLE transfer_outbox IS
    'Transactional outbox for Kafka publishing. Polled by in-service relay (ADR-014).';

-- Read pattern: relay worker — SELECT ... WHERE dispatched=false FOR UPDATE SKIP LOCKED.
CREATE INDEX idx_transfer_outbox_undispatched
    ON transfer_outbox (created_at)
    WHERE dispatched = false;
COMMENT ON INDEX idx_transfer_outbox_undispatched IS
    'Read pattern: relay polls oldest-first undispatched rows with SKIP LOCKED.';
