-- V001__create_transfers.sql
-- Ticket: TRANSFER-17
-- Author: banking-tech-lead
-- Rationale: Core transfer aggregate for the money-transfer Saga. Holds the
--            business state machine (PENDING -> COMPLETED / FAILED /
--            FAILED_COMPENSATED / COMPENSATION_FAILED) plus references to
--            saga state and the compensating transfer (US-008).
--            Schema designed so US-001/003 backend dev can implement now, and
--            US-004..US-011 can land later without breaking changes.
-- Down (runbook only — Flyway does not auto-run):
--   DROP TABLE IF EXISTS transfers;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE transfers (
    transfer_id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number         VARCHAR(32)     NOT NULL,
    source_account_id        UUID            NOT NULL,
    destination_account_id   UUID            NOT NULL,
    initiator_user_id        UUID            NOT NULL,
    initiator_customer_id    UUID            NOT NULL,
    amount                   NUMERIC(19,4)   NOT NULL CHECK (amount > 0),
    -- DevOps smoke-test fix (S9): CHAR(3) -> VARCHAR(3) to match Hibernate varchar mapping
    currency                 VARCHAR(3)      NOT NULL CHECK (currency = 'THB'),
    memo                     VARCHAR(200),
    status                   VARCHAR(32)     NOT NULL
        CHECK (status IN (
            'PENDING',
            'COMPLETED',
            'FAILED',
            'COMPENSATION_PENDING',
            'FAILED_COMPENSATED',
            'COMPENSATION_FAILED'
        )),
    channel                  VARCHAR(32)     NOT NULL
        CHECK (channel IN ('INTERNET_BANKING','MOBILE_BANKING')),
    saga_id                  UUID,
    compensation_transfer_id UUID,
    failure_reason           VARCHAR(64),
    limit_snapshot_per_tx    NUMERIC(19,4),
    limit_snapshot_daily     NUMERIC(19,4),
    correlation_id           VARCHAR(64)     NOT NULL,
    version                  BIGINT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    CONSTRAINT uq_transfers_reference_number UNIQUE (reference_number),
    CONSTRAINT fk_transfers_compensation
        FOREIGN KEY (compensation_transfer_id) REFERENCES transfers(transfer_id)
);

-- Read pattern: customer transfer history page (US-001 history endpoint, planned).
COMMENT ON TABLE transfers IS 'Money transfer aggregate root (intra-bank, THB). State machine per ADR-001.';
CREATE INDEX idx_transfers_initiator_customer_created_at
    ON transfers (initiator_customer_id, created_at DESC);
COMMENT ON INDEX idx_transfers_initiator_customer_created_at IS
    'Read pattern: list current customer transfers most-recent-first (US-001 history).';

-- Read pattern: list transfers per source account (ops investigations, US-006/007).
CREATE INDEX idx_transfers_source_account_created_at
    ON transfers (source_account_id, created_at DESC);
COMMENT ON INDEX idx_transfers_source_account_created_at IS
    'Read pattern: per-account transfer history for ops + reconciliation.';

-- Read pattern: saga recovery on pod restart (US-008 AC4, ADR-001).
CREATE INDEX idx_transfers_status_updated_at
    ON transfers (status, updated_at)
    WHERE status IN ('PENDING','COMPENSATION_PENDING','COMPENSATION_FAILED');
COMMENT ON INDEX idx_transfers_status_updated_at IS
    'Partial index for saga recovery scan: in-flight + failed-compensation states only.';
