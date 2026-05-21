-- V001__create_accounts.sql
-- Initial accounts table for account-service.
-- customer_id is indexed (see V002) for balance-dashboard-service query support.

CREATE TABLE IF NOT EXISTS accounts (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      UUID        NOT NULL,
    account_number   VARCHAR(20) NOT NULL UNIQUE,
    account_type     VARCHAR(20) NOT NULL CHECK (account_type IN ('SAVINGS','CURRENT','FIXED_DEPOSIT','LOAN','CREDIT_CARD')),
    status           VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','DORMANT','CLOSED','FROZEN','INACTIVE')),
    balance          NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    currency         CHAR(3)     NOT NULL DEFAULT 'THB',
    balance_as_of    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT      NOT NULL DEFAULT 0,  -- optimistic locking (banking gotcha)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
