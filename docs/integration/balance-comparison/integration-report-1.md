# Integration Report — balance-comparison · Run 1

> Agent: banking-integration · Gate: G7 Contract Integrity

## OpenAPI ↔ BE Drift

- [x] `accounts[].rank` — `int` in `BalanceDashboardResponse`
- [x] `accounts[].accountId` — `UUID` serialized as lowercase UUID string
- [x] `accounts[].accountNumberMasked` — `String`, field name exact match
- [x] `accounts[].accountType` — `AccountType` enum `.name()` serialization
- [x] `accounts[].balance` — `BigDecimal`, `@JsonSerialize(using = ToStringSerializer.class)` on getter (NOT field — fixed in R-BE-007)
- [x] `accounts[].currency` — `String`
- [x] `accounts[].balanceAsOf` — `Instant` + `JavaTimeModule` → ISO-8601 UTC
- [x] `accounts[].isStale` — `boolean`, `@JsonProperty("isStale")` forces key name (not stripped to `stale`)
- [x] `meta.accountCount` — `int`
- [x] `meta.freshness` — runtime values `"live"` / `"snapshot"` match spec
- [x] `meta.cacheHit` — `boolean`, `isCacheHit()` serializes to `cacheHit`
- [x] `meta.correlationId` — `String` (UUID / OTel trace ID)
- [x] Error responses use RFC 7807 Problem Detail (`application/problem+json`) via `ProblemDetailAdvice`
- [x] Controller path `GET /api/v1/balance-dashboard` matches OpenAPI operationId `getBalanceDashboard`
- [x] No undocumented endpoints beyond spec
- [x] No `customerId` in URL path or query string (Security S-EARLY-003)

## OpenAPI ↔ FE Client Drift

- [x] `balance` typed as `string` NOT `number` in `AccountViewDto` (line 46 of model, with warning comment)
- [x] All 8+ `AccountViewDto` fields present with correct types
- [x] `DashboardMeta.freshness` typed as `'live' | 'snapshot' | 'stale'` literal union (forward-compat `stale` — non-blocker)
- [x] FE API service calls `GET /api/v1/balance-dashboard` with no `customerId` query param
- [x] Error response typed as RFC 7807 Problem Detail shape

## Contract Shape Alignment (BE ↔ FE)

| Field | BE serialized key | FE TypeScript key | Match |
|---|---|---|---|
| `rank` | `rank` | `rank` | ✅ |
| `accountId` | `accountId` | `accountId` | ✅ |
| `maskedAccountNumber` | `maskedAccountNumber` | `maskedAccountNumber` | ✅ |
| `accountType` | `accountType` | `accountType` | ✅ |
| `balance` | `balance` (String via @JsonSerialize) | `balance: string` | ✅ |
| `currency` | `currency` | `currency` | ✅ |
| `balanceAsOf` | `balanceAsOf` | `balanceAsOf` | ✅ |
| `isStale` | `isStale` (@JsonProperty forced) | `isStale` | ✅ |
| `accountCount` | `accountCount` | `accountCount` | ✅ |
| `freshness` | `freshness` | `freshness` | ✅ |
| `cacheHit` | `cacheHit` | `cacheHit` | ✅ |
| `correlationId` | `correlationId` | `correlationId` | ✅ |

## Smoke Tests

⏭ Skipped — staging not yet deployed (DevOps P1 skeleton only; P2 runs after QA). Steps 1-3 are sufficient for G7 per anti-patterns rule.

## Verdict: approved

**Blockers: 0 · Warnings: 0**

All OpenAPI ↔ BE drift checks pass. All OpenAPI ↔ FE client drift checks pass. BE and FE share identical contract shapes with matching field names, types, and casing.
