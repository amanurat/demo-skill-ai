# Process Flow — Account Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Artifact:** BA-001 (initial)
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Diagram format:** Mermaid sequence diagram

---

## Overview

The flow covers two main paths:

1. **Cache hit path** — Redis has a fresh entry for the customer; audit is still emitted; response returned quickly (target p95 < 500ms).
2. **Cache miss path** — Redis has no entry (cold start or TTL expired); `AccountClient` is called with a single batched request; result is cached; audit is emitted; response returned (target p95 < 800ms).

The audit-emit path is shown as a parallel async fork — it is never skipped regardless of cache state.

---

## Sequence Diagram

```mermaid
sequenceDiagram
    autonumber

    actor Customer as Customer (Mobile Browser)
    participant MobileUI as Angular <balance-dashboard>
    participant Gateway as API Gateway (JWT filter)
    participant BDS as balance-dashboard-service
    participant Cache as Redis Cache<br/>customer:{id}:accounts TTL=30s
    participant ACS as AccountClient (Feign)<br/>→ account-service
    participant Ledger as account-service / ledger-service<br/>(source of balance_as_of)
    participant Audit as audit-service<br/>(append-only)

    Customer->>MobileUI: Open Balance Dashboard (authenticated session)
    MobileUI->>Gateway: GET /v1/balance-dashboard<br/>Authorization: Bearer {JWT}
    Gateway->>Gateway: Validate JWT (signature, expiry, scope: accounts:read)

    alt JWT invalid or expired
        Gateway-->>MobileUI: HTTP 401 Unauthorized
        MobileUI-->>Customer: "Session expired — please log in again"
    end

    Gateway->>BDS: Forward request with X-Customer-Id: {customerId from JWT sub}

    BDS->>BDS: Guard: assert X-Customer-Id == JWT sub (IDOR check)

    alt IDOR attempt detected (customerId mismatch)
        BDS-->>Gateway: HTTP 403 Forbidden
        BDS-)Audit: async emit BALANCE_INQUIRY result=FORBIDDEN actorId={caller}
        Gateway-->>MobileUI: HTTP 403 Forbidden
    end

    BDS->>Cache: GET customer:{customerId}:accounts

    alt Cache HIT (warm path — target p95 < 500ms)
        Cache-->>BDS: Cached account list (includes balance_as_of per account)
        BDS->>BDS: Apply ranking: sort by balance DESC, accountId ASC (tie-break)
        BDS-)Audit: async emit BALANCE_INQUIRY result=SUCCESS accountCount=N cacheHit=true
        BDS-->>Gateway: HTTP 200 ranked account list
        Gateway-->>MobileUI: HTTP 200 ranked account list
        MobileUI-->>Customer: Render ranked dashboard (balance, type, masked acctNo, balance_as_of)

    else Cache MISS (cold path — target p95 < 800ms)
        Cache-->>BDS: nil / key not found
        BDS->>ACS: listAccountsByCustomer(customerId)<br/>[single batched call — NOT N round-trips]

        alt AccountClient returns successfully
            ACS->>Ledger: Fetch account list with balance_as_of from ledger<br/>(accountType IN SAVINGS,CURRENT,FIXED_DEPOSIT AND status=ACTIVE)
            Ledger-->>ACS: Account records with balance_as_of timestamps
            ACS-->>BDS: AccountInfo[] (accountId, maskedAccountNumber,<br/>accountType, balance, currency, balance_as_of)
            BDS->>BDS: Filter: status=ACTIVE, accountType IN (SAVINGS,CURRENT,FIXED_DEPOSIT)
            BDS->>BDS: Apply ranking: sort by balance DESC, accountId ASC (tie-break)
            BDS->>Cache: SET customer:{customerId}:accounts TTL=30s<br/>(payload includes balance_as_of per account)
            Cache-->>BDS: OK
            BDS-)Audit: async emit BALANCE_INQUIRY result=SUCCESS accountCount=N cacheHit=false
            BDS-->>Gateway: HTTP 200 ranked account list
            Gateway-->>MobileUI: HTTP 200 ranked account list
            MobileUI-->>Customer: Render ranked dashboard

        else AccountClient unavailable / timeout (Redis also missed)
            ACS-->>BDS: Error (timeout / 5xx)
            BDS->>BDS: Log CACHE_MISS_REASON=ACCOUNTCLIENT_UNAVAILABLE<br/>increment error metric
            BDS-)Audit: async emit BALANCE_INQUIRY result=ERROR errorReason=UPSTREAM_UNAVAILABLE
            BDS-->>Gateway: HTTP 503 Service Unavailable (with Retry-After header)
            Gateway-->>MobileUI: HTTP 503
            MobileUI-->>Customer: "Unable to load accounts — please try again shortly"
        end

    else Redis unavailable (fail-open path)
        Cache-->>BDS: Connection error / timeout
        BDS->>BDS: Log CACHE_MISS_REASON=REDIS_UNAVAILABLE<br/>increment cache_unavailable metric
        BDS->>ACS: Proceed as cache-miss path above (fail-open)
        Note over BDS,ACS: Identical to Cache MISS path from here
    end

    Note over BDS,MobileUI: "May be stale" indicator is shown in UI<br/>when now() - balance_as_of > 60s (staleness threshold)
```

---

## Alt Paths Summary

| Alt Path | Trigger | Outcome |
|---|---|---|
| JWT invalid / expired | Gateway JWT validation failure | HTTP 401; no data returned; no audit event (request rejected before BDS) |
| IDOR attempt | `customerId` in request != JWT `sub` | HTTP 403; audit event emitted with `result=FORBIDDEN`; no data returned |
| Cache HIT (warm) | Redis has fresh entry within TTL | Data served from cache; audit emitted (cache does NOT suppress audit); p95 < 500ms target |
| Cache MISS (cold) | Redis key absent or expired | Single batched `AccountClient` call; result cached; audit emitted; p95 < 800ms target |
| Redis unavailable | Redis connection failure | Fail-open: proceed as cache-miss; observability counter incremented |
| AccountClient unavailable | Upstream timeout / 5xx after retries | HTTP 503 returned; audit emitted with `result=ERROR`; customer shown retry message |
| Customer has zero in-scope accounts | Filter returns empty list | HTTP 200 with empty `accounts` array; audit emitted with `accountCount=0` |
| Staleness threshold exceeded | `balance_as_of` is older than 60 seconds | Response still returned; UI shows "may be stale" indicator on affected rows |

---

## Audit-Emit Path (always fires)

```
BDS -)  Audit : async emit (fire-and-forget, non-blocking)
        eventType    = BALANCE_INQUIRY
        actorId      = customerId (from JWT sub)
        purpose      = balance-inquiry
        channel      = MOBILE_BANKING
        correlationId = (traceparent from OTel)
        timestamp    = UTC now
        result       = SUCCESS | FORBIDDEN | ERROR
        accountCount = N (count of accounts returned; 0 on empty; absent on FORBIDDEN/ERROR)
        cacheHit     = true | false (not applicable on FORBIDDEN)
```

The audit emit is decoupled from the response path using fire-and-forget async invocation (same pattern as money-transfer audit-service events). Response to customer is not held waiting for audit confirmation.

---

## Notes for SA (Architecture Decisions)

1. **Service boundary:** Where does the ranking + caching logic live — new `balance-dashboard-service` or extension of `account-service`? SA owns this ADR. The flow above is drawn as `balance-dashboard-service` (separate) as the default, but the sequence steps are valid either way.
2. **`AccountClient` batch API:** The flow assumes a single `listAccountsByCustomer(customerId)` call that returns all accounts in one response. If `AccountClient` currently supports only individual account lookups, TL must add or expose the batch endpoint. This is flagged as SUBDEC-002 (no default provided — SA/TL decision required).
3. **Audit event schema:** The flow reuses the existing `audit-service` event emitter from money-transfer. SA/TL to confirm whether the existing event schema accommodates `purpose = balance-inquiry` and `cacheHit` fields, or whether a new event type is needed (see DEP-003 in risk-register.md).
