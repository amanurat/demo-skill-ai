-- V002__add_accounts_customer_id_idx.sql
-- Supports balance-dashboard-service GET /api/v1/accounts?customerId={uuid}.
-- B-tree on a UUID column; ~16 bytes per leaf entry; negligible storage cost.
-- CONCURRENTLY: avoids locking the live accounts table during deploy (impl-notes §7).
--
-- Reversibility: DROP INDEX CONCURRENTLY IF EXISTS idx_accounts_customer_id;
-- (file as down_sql in the migration's accompanying ADR-note)
--
-- Per impl-notes §7: "verify, don't assume" — the index did not exist in this
-- account-service scaffold; created here for balance-dashboard-service query support.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_customer_id
    ON accounts (customer_id);
