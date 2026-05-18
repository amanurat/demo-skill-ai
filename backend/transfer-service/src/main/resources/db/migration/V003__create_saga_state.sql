-- V003__create_saga_state.sql
-- Ticket: TRANSFER-19
-- Author: banking-tech-lead
-- Rationale: Persistent saga state machine per ADR-001 (orchestration).
--            Survives pod restarts so a recovery worker can resume in-flight
--            sagas (US-008 AC4 + RISK-001). One row per saga; transitions are
--            recorded by updating current_step + last_event + version.
-- Down (runbook only):
--   DROP TABLE IF EXISTS saga_state;

CREATE TABLE saga_state (
    saga_id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id          UUID            NOT NULL,
    saga_type            VARCHAR(64)     NOT NULL
        CHECK (saga_type IN ('INTRA_BANK_TRANSFER')),
    status               VARCHAR(32)     NOT NULL
        CHECK (status IN (
            'IN_PROGRESS',
            'COMPLETED',
            'COMPENSATING',
            'COMPENSATED',
            'COMPENSATION_FAILED'
        )),
    current_step         VARCHAR(64)     NOT NULL
        CHECK (current_step IN (
            'STARTED',
            'LIMIT_CHECK_DONE',
            'DEBITED',
            'CREDITED',
            'COMPLETED',
            'COMPENSATION_DEBIT_REVERSED',
            'COMPENSATION_FAILED'
        )),
    last_event           VARCHAR(64),
    retry_count          INTEGER         NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error           TEXT,
    context              JSONB           NOT NULL,
    correlation_id       VARCHAR(64)     NOT NULL,
    version              BIGINT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    CONSTRAINT uq_saga_state_transfer UNIQUE (transfer_id),
    CONSTRAINT fk_saga_state_transfer
        FOREIGN KEY (transfer_id) REFERENCES transfers(transfer_id)
);

COMMENT ON TABLE saga_state IS
    'Persistent saga coordinator state. One row per saga. Survives pod restarts (ADR-001, US-008).';

-- Read pattern: recovery worker picks up unfinished sagas after restart.
CREATE INDEX idx_saga_state_status_updated_at
    ON saga_state (status, updated_at)
    WHERE status IN ('IN_PROGRESS','COMPENSATING','COMPENSATION_FAILED');
COMMENT ON INDEX idx_saga_state_status_updated_at IS
    'Read pattern: saga recovery scan for unfinished sagas (RISK-001 mitigation).';
