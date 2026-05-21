# API Reference — Balance Dashboard Service

**Version:** 1.0.0
**Base URL (staging):** `https://api.staging.bank.local`
**Base URL (production):** `https://api.bank.local`
**Feature flag note:** `balance-dashboard.enabled` defaults to `false` in production until BoT sign-off. When disabled, the endpoint returns `501 Not Implemented` with a `ProblemDetail` body (not `404`).

---

## Endpoint

### GET /api/v1/balance-dashboard

Returns the authenticated customer's eligible accounts (ACTIVE + SAVINGS / CURRENT / FIXED_DEPOSIT) ranked by balance DESC, with `accountId` ASC as the tie-break. Ranking is server-authoritative (ADR-004) — clients must render in array order and must not re-sort.

---

## Authentication

**Scheme:** OAuth2/OIDC Bearer JWT (RS256, issued and validated by `identity-service` via the api-gateway)

**Required scope:** `accounts:read`

The `Authorization` header is mandatory. Requests that are missing, expired, or carry an invalid JWT are rejected at the api-gateway with `401` before reaching this service.

```
Authorization: Bearer <JWT>
```

Token claims consumed by this service:

| Claim | Purpose |
|---|---|
| `sub` | UUID of the authenticated customer — the single source of truth for `customerId` (ADR-006) |
| `scope` | Must contain `accounts:read`; missing scope → `403` |
| `exp` | Expiry validated by api-gateway |

---

## Request

**Method:** GET
**Path:** `/api/v1/balance-dashboard`
**Body:** none
**Query parameters:** none
**Path parameters:** none

The endpoint accepts no `customerId` parameter of any kind (path, query, body, or header). The customer identity is derived exclusively from the JWT `sub` claim. This is a structural IDOR defense (ADR-006 / Security C-3).

### Request Headers

| Header | Required | Description |
|---|---|---|
| `Authorization` | Yes | `Bearer <JWT>` — RS256-signed access token with scope `accounts:read` |
| `traceparent` | No | W3C Trace Context header (`00-<traceId>-<spanId>-<flags>`). If present, the trace ID is echoed as `X-Correlation-Id` in the response. If absent, the service generates a correlation ID. |
| `Accept-Language` | No | Locale preference for localized `displayLabel` references (defaults to `th-TH`). The server returns enum keys only — label rendering is owned by the frontend. |
| `X-Customer-Id` | No | Injected by the api-gateway from `jwt.sub`. If present and mismatched against `jwt.sub`, the request is rejected with `403` and an `FORBIDDEN` audit event is emitted. Clients must never send this header directly. |

---

## Response — 200 OK

Cache behavior is indicated by the `X-Cache` response header. Both `HIT` (Redis warm) and `MISS` (fetched from `account-service`) return HTTP 200 with the same schema.

### Response Headers

| Header | Values | Description |
|---|---|---|
| `X-Cache` | `HIT` \| `MISS` | Cache observability marker. `HIT` — served from Redis within TTL (30 seconds). `MISS` — fetched from `account-service` then cached. (`STALE` is defined in the API spec but deferred to v1.1; v1 returns `503` instead.) |
| `Cache-Control` | `private, no-store` | Always this value. Redis is the only intentional cache. Downstream proxies and browsers must not cache the response body, which contains financial data (PDPA compliance). |
| `X-Correlation-Id` | UUID | OTel trace ID. Echoed from `meta.correlationId`. Safe to display to the customer for support contact. |

### Response Body — `BalanceDashboardResponse`

```json
{
  "accounts": [ /* AccountView[] */ ],
  "meta": { /* ResponseMeta */ }
}
```

#### `accounts` array

Eligible accounts, ranked by `balance` DESC with `accountId` ASC tie-break. Always non-null; empty array when the customer has no eligible accounts. Maximum 10 accounts in v1 (no pagination).

Each element is an `AccountView` object:

| Field | Type | Required | Description |
|---|---|---|---|
| `rank` | integer (1–50) | Yes | 1-based server-authoritative rank. The frontend must not re-sort. Powers `aria-label="Account {rank} of {total}"`. |
| `accountId` | string (UUID) | Yes | Internal account UUID. Not displayed in the UI. Used as `@for track` key only. |
| `accountNumberMasked` | string | Yes | Last 4 digits with leading asterisks. Pattern: `^\*+\d{4}$`. Full account number never crosses this service boundary. Example: `****7890` |
| `accountType` | string enum | Yes | One of: `SAVINGS`, `CURRENT`, `FIXED_DEPOSIT`. Loan and credit card accounts are filtered out by `EligibilityPolicy`. |
| `balance` | **string** | Yes | BigDecimal serialized as string with exactly 2 decimal places. Pattern: `^-?\d+\.\d{2}$`. Example: `"128540.25"` |
| `currency` | string (ISO 4217) | Yes | Currency code. v1 expects `THB` only. Pattern: `^[A-Z]{3}$` |
| `balanceAsOf` | string (ISO 8601 UTC) | Yes | Ledger-event timestamp that produced this balance. Used to compute `isStale` and to render "Last updated" in the UI. Example: `"2026-05-21T08:00:00Z"` |
| `isStale` | boolean | Yes | Server-computed: `true` when `now() - balanceAsOf > 60 seconds`. Independent of `meta.freshness`. Drives the per-row staleness badge. |
| `displayLabel` | string enum | Yes | i18n key for the account type label. One of: `account.type.savings`, `account.type.current`, `account.type.fixedDeposit`. The server returns the key only — never the rendered Thai or English string. |

**Important — `balance` field is a string, not a number:**
The `balance` field uses JSON `string` type intentionally to avoid IEEE 754 floating-point precision loss in client-side JSON parsers. Clients must parse this field as a decimal string (e.g., Java `BigDecimal`, JavaScript `Decimal.js`, or display as-is). Never parse it as a JavaScript `number`.

#### `meta` object (`ResponseMeta`)

| Field | Type | Required | Description |
|---|---|---|---|
| `freshness` | string enum | Yes | `live` — fresh fetch from `account-service` (cache MISS). `snapshot` — served from Redis cache within TTL. (`stale` — deferred to v1.1; v1 returns `503` instead.) |
| `cacheHit` | boolean | Yes | `true` if served from Redis without an upstream call. Observability and audit only — never displayed to the customer. |
| `accountCount` | integer (0–50) | Yes | Authoritative count of accounts in the response. Matches `accounts.length` exactly. |
| `correlationId` | string (UUID) | Yes | OTel trace ID (lowercase UUID). Echoed in the `X-Correlation-Id` response header. |

### Response Example — Three Accounts, Cache MISS

```json
{
  "accounts": [
    {
      "rank": 1,
      "accountId": "a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "accountNumberMasked": "****7890",
      "accountType": "SAVINGS",
      "balance": "128540.25",
      "currency": "THB",
      "balanceAsOf": "2026-05-21T08:00:00Z",
      "isStale": false,
      "displayLabel": "account.type.savings"
    },
    {
      "rank": 2,
      "accountId": "b2c3d4e5-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
      "accountNumberMasked": "****1234",
      "accountType": "CURRENT",
      "balance": "45000.00",
      "currency": "THB",
      "balanceAsOf": "2026-05-21T08:00:00Z",
      "isStale": false,
      "displayLabel": "account.type.current"
    },
    {
      "rank": 3,
      "accountId": "c3d4e5f6-7a8b-4c9d-0e1f-2a3b4c5d6e7f",
      "accountNumberMasked": "****5678",
      "accountType": "FIXED_DEPOSIT",
      "balance": "12500.50",
      "currency": "THB",
      "balanceAsOf": "2026-05-20T09:30:00Z",
      "isStale": false,
      "displayLabel": "account.type.fixedDeposit"
    }
  ],
  "meta": {
    "freshness": "live",
    "cacheHit": false,
    "accountCount": 3,
    "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60"
  }
}
```

### Response Example — Empty State (Zero Eligible Accounts)

```json
{
  "accounts": [],
  "meta": {
    "freshness": "live",
    "cacheHit": false,
    "accountCount": 0,
    "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60"
  }
}
```

### Response Example — Cache HIT with a Stale Row

```json
{
  "accounts": [
    {
      "rank": 1,
      "accountId": "a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
      "accountNumberMasked": "****7890",
      "accountType": "SAVINGS",
      "balance": "128540.25",
      "currency": "THB",
      "balanceAsOf": "2026-05-21T07:58:30Z",
      "isStale": false,
      "displayLabel": "account.type.savings"
    },
    {
      "rank": 2,
      "accountId": "c3d4e5f6-7a8b-4c9d-0e1f-2a3b4c5d6e7f",
      "accountNumberMasked": "****5678",
      "accountType": "FIXED_DEPOSIT",
      "balance": "12500.50",
      "currency": "THB",
      "balanceAsOf": "2026-05-20T09:30:00Z",
      "isStale": true,
      "displayLabel": "account.type.fixedDeposit"
    }
  ],
  "meta": {
    "freshness": "snapshot",
    "cacheHit": true,
    "accountCount": 2,
    "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60"
  }
}
```

---

## Error Responses — RFC 7807 Problem Detail

All `4xx` and `5xx` responses use `Content-Type: application/problem+json` and the RFC 7807 Problem Detail format. Error messages are deliberately generic — they never include balance values, account numbers, or upstream service names (Security C-2).

### Error Body Schema (`ProblemDetail`)

| Field | Type | Required | Description |
|---|---|---|---|
| `type` | string (URI) | Yes | URI identifying the problem class. Resolves to internal error documentation. |
| `title` | string | Yes | Short, human-readable summary. Localized via `Accept-Language` (Thai default). |
| `status` | integer | Yes | HTTP status code. |
| `detail` | string | No | Optional human-readable explanation. Always generic — no PII, no balance values. |
| `instance` | string | No | URI of the specific request occurrence. |
| `correlationId` | string (UUID) | No | OTel trace ID. Safe to display to the customer for support contact. |
| `code` | string enum | No | Stable machine-readable error code. One of: `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMIT_EXCEEDED`, `SERVICE_UNAVAILABLE`. |

---

### 401 Unauthorized

Missing, expired, or invalid JWT. The api-gateway emits its own auth-failure audit event; this service does not re-audit `401` responses.

**Response headers:** `WWW-Authenticate: Bearer realm="bank", error="invalid_token"`, `X-Correlation-Id`

```json
{
  "type": "https://errors.bank.local/balance-dashboard/unauthorized",
  "title": "กรุณาเข้าสู่ระบบ",
  "status": 401,
  "detail": "เซสชันหมดอายุ กรุณาเข้าสู่ระบบใหม่",
  "instance": "/api/v1/balance-dashboard",
  "code": "UNAUTHORIZED"
}
```

**Frontend handling:** Silently redirect to login (reuse existing money-transfer 401 handler).

---

### 403 Forbidden

Returned when any of the following occur — the response body deliberately does not indicate which check failed:

- JWT `sub` does not match the `X-Customer-Id` header (IDOR attempt detected)
- JWT does not carry scope `accounts:read`
- Feature flag `balance-dashboard.enabled` is `false`

An `AuditEventRecord` with `result=FORBIDDEN` is emitted on the IDOR path.

```json
{
  "type": "https://errors.bank.local/balance-dashboard/forbidden",
  "title": "ไม่สามารถเข้าถึงข้อมูลได้",
  "status": 403,
  "detail": "คำขอนี้ไม่ได้รับอนุญาต",
  "instance": "/api/v1/balance-dashboard",
  "code": "FORBIDDEN"
}
```

**Frontend handling:** Surface "Access denied" toast and log to OTel. Do not auto-retry.

---

### 429 Too Many Requests

Rate limit exceeded at the api-gateway (60 requests/minute per customer). This service does not throttle internally.

**Response headers:** `Retry-After: <seconds>`, `X-Correlation-Id`

```json
{
  "type": "https://errors.bank.local/balance-dashboard/rate-limit",
  "title": "คำขอบ่อยเกินไป",
  "status": 429,
  "detail": "กรุณาลองใหม่อีกครั้งในอีกสักครู่",
  "instance": "/api/v1/balance-dashboard",
  "code": "RATE_LIMIT_EXCEEDED"
}
```

---

### 500 Internal Server Error

Unhandled internal error. Not included in the OpenAPI path definition (implicitly possible on any endpoint). Same `ProblemDetail` envelope applies.

---

### 503 Service Unavailable

The Resilience4j circuit breaker for `account-service` is OPEN and no cached snapshot is available. Returned when: timeout, circuit breaker open, bulkhead full, or `account-service` returns 5xx after all retries. An `AuditEventRecord` with `result=ERROR` is emitted.

In v1, there is no last-known-good stale fallback — that is deferred to v1.1.

**Response headers:** `Retry-After: 5`, `X-Correlation-Id`

```json
{
  "type": "https://errors.bank.local/balance-dashboard/unavailable",
  "title": "ไม่สามารถโหลดข้อมูลได้ในขณะนี้",
  "status": 503,
  "detail": "กรุณาลองใหม่อีกครั้งในอีกสักครู่",
  "instance": "/api/v1/balance-dashboard",
  "code": "SERVICE_UNAVAILABLE"
}
```

**Frontend handling:** Show retry banner "Unable to load accounts. Tap to retry." with exponential backoff (1s / 2s / 4s, capped at 3 attempts).

---

## Idempotency

`GET /api/v1/balance-dashboard` is an HTTP-safe, idempotent method. Clients may safely retry on `503` using the `Retry-After` guidance. No `Idempotency-Key` header is sent or required for read operations.

---

## Cache Behavior

| Scenario | `X-Cache` header | `meta.freshness` | `Cache-Control` |
|---|---|---|---|
| Cold fetch from `account-service` | `MISS` | `live` | `private, no-store` |
| Served from Redis within TTL | `HIT` | `snapshot` | `private, no-store` |

The Redis TTL is 30 seconds (configurable via `balance-dashboard.cache.ttl-seconds`). Per-row staleness (`isStale`) is computed independently: `now() - balanceAsOf > 60 seconds`.

`Cache-Control: private, no-store` is always present. The response body contains financial data and must not be cached by any proxy or browser cache. Redis is the only intentional cache layer.

---

## Audit Behavior

Every response — including cache hits, `403`, and `503` — emits exactly one `AuditEventRecorded v2` Kafka event to topic `audit.event-recorded` (BR-014).

The audit payload is metadata-only (Security C-2 / PDPA §22 data minimization):

- Permitted fields: `eventType`, `actorId` (JWT sub), `channel`, `correlationId`, `timestamp`, `result`, `purpose`, `cacheHit`, `accountCount`
- Forbidden fields: `balance`, `accountId`, `accountNumber`, `accounts[]`, `balanceAsOf`, `currency`

Balance values and account numbers are never written to audit logs or Kafka events. The 7-year retained audit log is not a balance history store.

---

## Security Notes

- No PII in the URL path or query string. The endpoint path is static.
- No balance values in error responses.
- No upstream service names in error responses.
- `customerId` is never accepted as a client-supplied parameter. It is derived solely from the JWT `sub` claim via `CustomerIdResolver` (ADR-006).
- Log output masks `customerId`, `accountId`, and Redis key prefixes via `LogMasking.maskId` / `LogMasking.maskKey` (Security F-1 / CWE-532 resolved). A `logback-spring.xml` regex safety net applies secondary masking.

---

*API Reference for `balance-dashboard-service` v1.0.0 — generated by `banking-docs` · 2026-05-22*
