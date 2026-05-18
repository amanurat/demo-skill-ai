-- V005__create_daily_transfer_accumulator.sql
-- Ticket: TRANSFER-21
-- Author: banking-tech-lead
-- Rationale: Daily cumulative limit (US-005) + AML threshold (US-009) state.
--            Per architect ADR-004: PostgreSQL row with optimistic lock
--            updated in the same transaction as the transfer write.
--            Per ADR-006: accumulation_date stores Bangkok (UTC+7) civil date.
--            Per ADR-007: keyed by account_id; customer-level aggregation
--            handled via a view if Compliance later requires it.
--            US-001/003 backend dev does NOT touch this table yet, but schema
--            is here so subsequent stories can land without migration churn.
-- Down (runbook only):
--   DROP TABLE IF EXISTS daily_transfer_accumulator;

CREATE TABLE daily_transfer_accumulator (
    accumulator_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id              UUID            NOT NULL,
    customer_id             UUID            NOT NULL,
    accumulation_date       DATE            NOT NULL,
    cumulative_amount       NUMERIC(19,4)   NOT NULL DEFAULT 0 CHECK (cumulative_amount >= 0),
    transfer_count          INTEGER         NOT NULL DEFAULT 0 CHECK (transfer_count >= 0),
    aml_threshold_breached  BOOLEAN         NOT NULL DEFAULT false,
    aml_breach_notified_at  TIMESTAMPTZ,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_accumulator_account_date UNIQUE (account_id, accumulation_date)
);

COMMENT ON TABLE daily_transfer_accumulator IS
    'Per-account per-Bangkok-civil-day cumulative transfer counters. Used for daily limit (US-005) + AML threshold (US-009). Optimistic locked.';
COMMENT ON COLUMN daily_transfer_accumulator.accumulation_date IS
    'Bangkok (UTC+7) civil date — see ADR-006.';

-- Read pattern: customer-level rollup for AML scope (ADR-007 alternative path).
CREATE INDEX idx_accumulator_customer_date
    ON daily_transfer_accumulator (customer_id, accumulation_date);
COMMENT ON INDEX idx_accumulator_customer_date IS
    'Read pattern: future customer-level AML aggregation (ADR-007 hedge).';
