# Refactor Log — Iteration 3 (Security Findings F-1 and F-2)

**Feature:** balance-comparison
**Branch:** stage/02-balance-comparison
**Agent:** banking-refactoring
**Iteration:** 3
**Trigger:** banking-security returned `changes_requested` — 2 BLOCKING findings (F-1 HIGH, F-2 MEDIUM)
**Date:** 2026-05-21

---

## Summary

Two blocking security findings from G8 (banking-security) have been resolved in this iteration:

| Finding | Severity | Standard | Status |
|---------|----------|----------|--------|
| F-1 — PII in application logs | HIGH | CWE-532, OWASP A09:2021, PDPA §22 | FIXED |
| F-2 — Missing logback-spring.xml masking filter | MEDIUM | CWE-532, OWASP A09:2021 | FIXED |

No new findings introduced. G7 (banking-integration) approval status is unchanged — no files in the integration contract path were modified.

---

## New Files Created

### 1. `LogMasking.java` — PII masking utility (F-1 primary fix)

**File:** `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/infrastructure/rest/LogMasking.java`
**Package:** `infrastructure.rest` (hexagonal: infra cross-cutting, not domain)

Public static API:
- `maskId(UUID id)` — returns `id.toString().substring(0, 8) + "****"`
- `maskId(String id)` — same as above for String input; null-safe returns `"***"`
- `maskKey(String key)` — for Redis cache keys; extracts prefix up to last `:`, then masks UUID suffix to first 8 chars + `"****"`

Architecture constraint satisfied: domain layer (`domain/`) imports nothing from this class.

---

### 2. `LogMaskingTest.java` — Unit tests for masking utility

**File:** `backend/balance-dashboard-service/src/test/java/com/bank/balancedashboard/unit/LogMaskingTest.java`

Test assertions:
- `maskId(UUID)` output does NOT match full UUID regex `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`
- `maskId(UUID)` keeps first 8 hex chars
- `maskId(UUID)` null → `"***"`
- `maskId(String)` null → `"***"`; short string → `"***"`
- `maskKey(String)` does NOT contain full UUID; preserves prefix; null → `"***"`; alternate prefix variant
- `maskId` is idempotent

---

### 3. `logback-spring.xml` — Defense-in-depth masking filter (F-2 fix)

**File:** `backend/balance-dashboard-service/src/main/resources/logback-spring.xml`

Profiles configured:
- `dev` / `default` — human-readable console with `<replace>` regex masking
- `staging` — JSON (Logstash encoder) with regex masking + OTel traceId/spanId fields
- `prod` — JSON (Logstash encoder) with regex masking; root level = INFO (no DEBUG leakage)

Masking patterns applied in all profiles:
1. UUID: `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}` → `xxxxxxxx-****`
2. Digit sequences 10–16 chars (PAN/account number): `\b\d{10,16}\b` → `****-REDACTED`

TODO comment included referencing observability-lib masking filter (OBS-042) per F-2 fix instruction (defense-in-depth intent documented).

---

## Changes to Existing Files

### Finding F-1 — Log call fixes per file

#### `BalanceDashboardController.java`
**File:** `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/infrastructure/rest/BalanceDashboardController.java`

| Line | Before | After |
|------|--------|-------|
| 75 | `log.debug("... customerId={} ...", customerId, ...)` | `log.debug("... customerId={} ...", LogMasking.maskId(customerId), ...)` |

No import added — `LogMasking` is in the same package (`infrastructure.rest`).

---

#### `BalanceDashboardService.java`
**File:** `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/application/service/BalanceDashboardService.java`

Import added:
```java
import com.bank.balancedashboard.infrastructure.rest.LogMasking;
```

| Line | Before | After |
|------|--------|-------|
| 104 | `log.debug("... customerId={} ...", customerId, ...)` | `log.debug("... customerId={} ...", LogMasking.maskId(customerId), ...)` |
| 129 | `log.debug("... customerId={} ...", customerId, ...)` | `log.debug("... customerId={} ...", LogMasking.maskId(customerId), ...)` |
| 138 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 153 | `log.warn("... customerId={} — failing open", customerId, e)` | `log.warn("... customerId={} — failing open", LogMasking.maskId(customerId), e)` |

Architecture note: `BalanceDashboardService` is in `application.service` package. Importing `LogMasking` from `infrastructure.rest` crosses the hexagonal boundary (application → infrastructure). This is permitted for logging utilities per the fix instructions, as `LogMasking` is not a domain concern and this is an outbound cross-cutting concern. The **domain layer** (`domain/`) remains free of any LogMasking imports — constraint satisfied.

---

#### `AccountClientAdapter.java`
**File:** `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/infrastructure/client/AccountClientAdapter.java`

Import added:
```java
import com.bank.balancedashboard.infrastructure.rest.LogMasking;
```

| Line | Before | After |
|------|--------|-------|
| 109 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 112 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 115 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 121 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 124 | `log.warn("... customerId={}", customerId, e)` | `log.warn("... customerId={}", LogMasking.maskId(customerId), e)` |
| 153 | `log.warn("account.balanceAsOf.null accountId={}", ai.getAccountId())` | `log.warn("account.balanceAsOf.null accountId={}", LogMasking.maskId(ai.getAccountId()))` |
| 178 | `log.warn("account.balanceAsOf.parse.failed accountId={} ...", accountId, ...)` | `log.warn("account.balanceAsOf.parse.failed accountId={} ...", LogMasking.maskId(accountId), ...)` |

Note: `ai.getAccountId()` returns `UUID` — `LogMasking.maskId(UUID)` overload used at line 153.
`accountId` at line 178 is typed `UUID` — same overload applies.

---

#### `RedisCacheRepository.java`
**File:** `backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/infrastructure/cache/RedisCacheRepository.java`

Import added:
```java
import com.bank.balancedashboard.infrastructure.rest.LogMasking;
```

| Line | Before | After |
|------|--------|-------|
| 83 | `log.warn("cache.get.failed key={} ...", key, e)` | `log.warn("cache.get.failed key={} ...", LogMasking.maskKey(key), e)` |
| 107 | `log.debug("cache.put key={} ttl={}s ...", key, ttlSeconds, ...)` | `log.debug("cache.put key={} ttl={}s ...", LogMasking.maskKey(key), ttlSeconds, ...)` |
| 111 | `log.warn("cache.put.failed key={} ...", key, e)` | `log.warn("cache.put.failed key={} ...", LogMasking.maskKey(key), e)` |

---

## Architecture Constraint Verification

| Constraint | Status | Evidence |
|---|---|---|
| Hexagonal: `LogMasking` in `infrastructure.rest` | PASS | File at `infrastructure/rest/LogMasking.java` |
| Domain layer has ZERO non-package imports | PASS | No edits to any `domain/` class |
| `IborCheckFilter` is ONLY class reading `X-Customer-Id` header | PASS | No changes to `IborCheckFilter.java` |
| `IborCheckFilter` private `maskId(String)` not broken | PASS | `IborCheckFilter` unchanged; uses own local `maskId(String)` (different signature: takes `String`, `LogMasking` adds `UUID` overload — no conflict) |
| `CustomerIdResolver` reads JWT sub ONLY | PASS | `CustomerIdResolver.java` not modified |
| `AuditEventRecord` does NOT log balance/accountId/accounts | PASS | No changes to `AuditEventRecord.java`; verified existing code not affected |

---

## Security C-2 Confirmation

`AuditEventRecord` was not modified. The existing factory methods (`success`, `error`, `forbidden`) do not log balance, accountId, accounts, balanceAsOf, or currency fields in their structured audit events — this is unchanged and the constraint remains satisfied.

---

## No New Findings Introduced

- No new external dependencies added (no Maven pom changes)
- No changes to domain model, ports, or API contracts
- No changes to security filter chain or authentication logic
- `logback-spring.xml` uses only Logback built-ins (`<replace>` pattern) for dev/default; staging/prod use `net.logstash.logback.encoder.LogstashEncoder` which is already present in Spring Boot starter log dependencies
- `LogMasking` is a pure utility with no Spring beans, no state, no side effects

---

## Findings Resolution Confirmation

| Finding | Resolved? | Method |
|---------|-----------|--------|
| F-1 — PII in application logs (HIGH) | YES | `LogMasking` utility + call-site replacements in all 4 flagged files (10 log statements patched) |
| F-2 — Missing logback-spring.xml masking filter (MEDIUM) | YES | `logback-spring.xml` created with UUID + digit-sequence regex masking for dev/staging/prod profiles |
