# Glossary — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Artifact:** BA-001 (initial)
> **Scope:** BA-level term definitions. Architecture and technical terms are owned by SA/TL.

---

## Terms

### Active Account
An account with `status = 'ACTIVE'` in account-service. Only active accounts appear in the balance dashboard. Accounts with any other status (DORMANT, CLOSED, FROZEN, INACTIVE) are excluded from the ranked list and from the aggregated response.

### balance_as_of
The ledger timestamp of the most recent balance-affecting event for an account, as recorded by ledger-service. This is the authoritative "last updated" value displayed in the UI. It is NOT the database row `updated_at` column (which reflects the last write to the account record, not necessarily a balance change). Decision rationale: `balance_as_of` is more truthful to the customer — a row `updated_at` can change due to metadata updates unrelated to the balance. Source: OPEN-002 stakeholder decision.

### Dormant Account
An account that has had no customer-initiated transactions for a period as defined by the bank's dormancy policy (typically 12 months in Thai banking). Dormant accounts are excluded from the balance dashboard view per OPEN-003 decision. They remain on the audit trail — the exclusion is a display filter, not a data deletion.

### IDOR (Insecure Direct Object Reference)
An access control vulnerability where a customer could access another customer's accounts by manipulating an identifier in the request (e.g., substituting their `customerId` for another customer's). The balance-dashboard service mitigates IDOR by deriving the `customerId` exclusively from the JWT `sub` claim and rejecting any request that attempts to override it.

### In-Scope Account Types
For this feature, account types eligible to appear on the balance dashboard are: `SAVINGS` (บัญชีออมทรัพย์), `CURRENT` (บัญชีกระแสรายวัน), and `FIXED_DEPOSIT` (บัญชีเงินฝากประจำ). Loan accounts and credit-card accounts are explicitly excluded. Source: OPEN-004 stakeholder decision.

### Native Currency Display
Each account is displayed in the currency it is denominated in, with no FX conversion applied. No total-in-home-currency aggregation is computed or displayed in v1. This is the result of the OPEN-001 stakeholder decision ("native currency only"). Multi-currency total (US-BC-004) is deferred to a follow-up sprint.

### Ranking Determinism
The property that the order of accounts in the ranked list is always the same for identical input data, across requests, page loads, and cache hits. The ranking rule is: **balance DESC as primary sort key; `accountId` ASC as tie-break when two accounts have equal balance**. This rule must be implemented identically in the aggregation service and must produce the same result whether the response is served from cache or computed live.

### Staleness Threshold
The maximum age of `balance_as_of` before the UI displays a "may be stale" indicator alongside the account's balance. Default: 60 seconds (configurable via feature flag `balance-dashboard.staleness-threshold-seconds`). When `now() - balance_as_of > 60s`, the row shows the indicator. The account is still displayed — staleness does not suppress the row.

### Warm Cache
The state where Redis holds a valid (non-expired) entry for `customer:{customerId}:accounts`. A warm-cache request serves data from Redis without calling `AccountClient`. The p95 < 500ms performance target applies to warm-cache requests.

### Cold Cache
The state where Redis has no entry for `customer:{customerId}:accounts` — either because this is the first request, or the TTL (30 seconds) has expired. A cold-cache request triggers a live call to `AccountClient`. The p95 < 800ms graceful degradation target applies to cold-cache requests.

### AccountClient
A Spring Boot Feign client defined in the `money-transfer` feature codebase that wraps calls to `account-service`. Reused without modification for this feature. The specific method used is `listAccountsByCustomer(customerId)` — a single batched call returning all accounts for a customer. Do not rebuild; reference the existing client.

### AccountInfo DTO
The data transfer object returned by `AccountClient`, defined in the `money-transfer` feature. Fields relevant to this feature: `accountId`, `accountNumber` (already masked to last 4 digits), `accountType`, `balance`, `currency`, `status`, `balance_as_of`. Source of truth: `docs/artifacts/S2-ba-money-transfer.json` (entity: Account).

### Purpose Tagging (Audit)
Every audit event emitted by the balance dashboard carries `purpose = 'balance-inquiry'`. This tag enables BoT compliance queries to filter and report on all balance-inquiry access events separately from transfer events, supporting data access transparency obligations under PDPA and BoT IT-Risk Guidelines.
