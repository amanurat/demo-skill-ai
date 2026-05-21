# Implementation Task Plan — balance-comparison

> Sprint: SPRINT-2026-Q2-BC-01 · TL Artifact: TL-001 · Planner: banking-implementation-planner

---

## Architecture Constraints (read before touching code)

| Constraint | Rule | Source |
|---|---|---|
| NO-RDBMS | No JPA, no Flyway, no DataSource bean. Exclude `DataSourceAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`, `HibernateJpaAutoConfiguration` at `@SpringBootApplication`. | db-schema §2.1 |
| Hexagonal purity | Domain: ZERO Spring/Kafka/Redis imports. Application: `@Service` only. Infrastructure: all adapters. | impl-notes §1 |
| Resilience4j operator order | `TimeLimiter (outer) → CB → Retry → Bulkhead (inner)` | impl-notes §3 |
| Redis SETEX | Atomic `SET + TTL` via `redisTemplate.opsForValue().set(key, val, Duration)`. NEVER `SET` then `EXPIRE` separately. | impl-notes §4 |
| Redis fail-open, no CB | Catch `RedisException` → log + metric → fallthrough to AccountClient. No circuit breaker on Redis. | impl-notes §4 |
| Feature flag + 501 | `@ConditionalOnProperty("balance-dashboard.enabled")`. Disabled state = `BalanceDashboardDisabledController` returns **501 Not Implemented**, not 404. | impl-notes §5 |
| Security C-2 | `AuditEventPublisher` emits metadata-only. Forbidden fields: `balance`, `accountId`, `accountNumber`, `accounts[]`, `balanceAsOf`, `currency`. Enforced by `KafkaAuditEventPublisherContractTest`. | ADR-007 |
| Security C-3 | `CustomerIdResolver` is the ONLY path to derive `customerId` from `JwtAuthenticationToken.getToken().getSubject()`. `IborCheckFilter` is the ONLY class allowed to read `X-Customer-Id` header. ArchUnit `CustomerIdSourceRule` fails build on violation. | ADR-006 |
| Security C-4 | `spring.data.redis.ssl.enabled=true`. At-rest AES-256-GCM verified by DevOps P1 (ASSUMPTION-TL-004). | security/early-review §7 |
| balance field type | `BigDecimal` in Java, serialized as `String` (2dp `^-?\d+\.\d{2}$`). Never `float`/`double`. | OpenAPI spec |
| Server-side ranking | FE MUST NOT re-sort. Server returns pre-ranked array; FE renders in received order. | impl-notes §8.2 |

---

## BE Task List (banking-backend-dev)

### Layer 1: Domain Model

> Package root: `com.bank.balancedashboard.domain`
> Rule: **ZERO** Spring, Kafka, or Redis imports in any class under this package.

| Class | Package | AC Covered | Test Cases |
|---|---|---|---|
| `AccountView` | `domain.model` | AC-001-H1, AC-002-H1, AC-002-H3, AC-002-H4 | Unit: `AccountViewTest` — (1) all required fields present; (2) `balance` is `BigDecimal`, not `double`; (3) `accountNumberMasked` matches `^\*+\d{4}$`; (4) record is immutable (no setters) |
| `BalanceSnapshot` | `domain.model` | AC-001-H1, AC-003-H2 | Unit: `BalanceSnapshotTest` — (1) holds unranked list from AccountClient; (2) `null` accounts list throws NPE on construction |
| `RankedDashboard` | `domain.model` | AC-001-H1, AC-001-H2, AC-003-H1 | Unit: `RankedDashboardTest` — (1) holds ranked list + freshness metadata; (2) ranked list order preserved (not re-sorted) |
| `AuditEventRecord` | `domain.audit` | AC-001-H3, AC-001-E1, AC-001-E2, AC-003-H3 | Unit via `KafkaAuditEventPublisherContractTest` — (1) `success()` factory populates all 9 fields; (2) `forbidden()` factory populates correct subset; (3) `error()` factory populates correct subset; (4) reflection: no component named `balance`, `accountId`, `accountNumber`, `accounts`, `balanceAsOf`, `currency` |
| `Channel` | `domain.audit` | AC-001-H3 | Unit: covered by `AuditEventRecord` factory tests — all 3 enum values compile |
| `Result` | `domain.audit` | AC-001-H3, AC-001-E2 | Unit: covered by `AuditEventRecord` factory tests — all 4 enum values (`SUCCESS`, `FAILURE`, `FORBIDDEN`, `ERROR`) compile |
| `LoadDashboardUseCase` (interface) | `domain.port.in` | AC-001-H1 | N/A (interface only) — verified by `BalanceDashboardService` implementing it |
| `EligibilityPolicy` | `domain.policy` | AC-001-H1, AC-001-E1, US-BC-001 BR-003, BR-004 | Unit: `EligibilityPolicyTest` — (1) ACTIVE SAVINGS passes; (2) ACTIVE CURRENT passes; (3) ACTIVE FIXED_DEPOSIT passes; (4) DORMANT SAVINGS excluded; (5) CLOSED SAVINGS excluded; (6) FROZEN SAVINGS excluded; (7) INACTIVE SAVINGS excluded; (8) ACTIVE LOAN excluded; (9) ACTIVE CREDIT_CARD excluded; (10) all-ineligible list returns empty; (11) config flip excludes FIXED_DEPOSIT (ASSUMPTION-TL-001 fallback) |
| `Ranker` | `domain.policy` | AC-001-H1, AC-001-H2 | Unit: `RankerTest` — (1) 3 distinct balances → DESC order; (2) equal balances → accountId ASC tie-break; (3) all equal → stable by accountId; (4) single account → rank 1; (5) empty list → empty result; (6) `rank` field is 1-based integer |

**Layer 1 self-check:**
```bash
# MUST return zero matches:
grep -rn "import org.springframework" backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/domain/
grep -rn "import org.apache.kafka" backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/domain/
grep -rn "import io.lettuce\|import org.springframework.data.redis" backend/balance-dashboard-service/src/main/java/com/bank/balancedashboard/domain/
```

---

### Layer 2: Repository / Port Interfaces

> Package root: `com.bank.balancedashboard.application.port.out`
> Rule: interfaces only — no implementation. `@Service` annotation NOT permitted here.

| Interface | Package | Method Signatures |
|---|---|---|
| `AccountPort` | `application.port.out` | `List<AccountView> fetchAccounts(UUID customerId);` — may throw `UpstreamUnavailableException` (unchecked) |
| `CachePort` | `application.port.out` | `Optional<RankedDashboard> get(UUID customerId);` and `void put(UUID customerId, RankedDashboard dashboard);` — implementations MUST NOT leak `RedisException` to callers |
| `AuditEventPublisher` | `application.port.out` | `void publish(AuditEventRecord record);` — implementations MUST NOT throw to callers (swallow + log + metric) |

**Note:** `AccountPort.fetchAccounts()` returns already-mapped `AccountView` list (eligibility-filtered + mapped). The mapping and filtering responsibility belongs to the infrastructure adapter (`AccountClientAdapter`), not to the application service. The application service only calls `fetchAccounts()` and then calls `Ranker`.

---

### Layer 3: Application Service

> Package: `com.bank.balancedashboard.application.service`
> Rule: `@Service` only. No `@RestController`, no Redis/Kafka/Jackson imports.

| Class | Use Case | AC Covered | Test Cases (with mock ports) |
|---|---|---|---|
| `BalanceDashboardService` implements `LoadDashboardUseCase` | Orchestrates: cache→client→rank→audit | AC-001-H1, AC-001-H2, AC-001-H3, AC-001-E1, AC-003-H1, AC-003-H2, AC-003-H3, AC-003-E1 | See detailed test cases below |

**`BalanceDashboardServiceTest` — detailed test cases (all with Mockito mocked ports):**

| # | Test Method | Mocked Scenario | Assert |
|---|---|---|---|
| 1 | `cacheHit_returnsSnapshot_emitsAudit` | `CachePort.get()` → populated `RankedDashboard` | Returns `RankedDashboard` from cache; `AuditEventPublisher.publish()` called once with `result=SUCCESS, cacheHit=true`; `AccountPort.fetchAccounts()` never called |
| 2 | `cacheMiss_fetchesAndRanks_writesCache_emitsAudit` | `CachePort.get()` → empty; `AccountPort.fetchAccounts()` → 3 `AccountView`s | Returns `Ranker`-ordered result; `CachePort.put()` called once; `AuditEventPublisher.publish()` called once with `result=SUCCESS, cacheHit=false` |
| 3 | `emptyAccounts_returns200WithEmptyArray_emitsAuditAccountCount0` | `CachePort.get()` → empty; `AccountPort.fetchAccounts()` → empty list | Returns `accounts=[]`; audit emitted with `accountCount=0, result=SUCCESS` |
| 4 | `upstreamFailure_returns503_emitsAuditError` | `CachePort.get()` → empty; `AccountPort.fetchAccounts()` → throws `UpstreamUnavailableException` | Throws `UpstreamUnavailableException` to controller; audit emitted with `result=ERROR`; `CachePort.put()` never called |
| 5 | `redisFailure_failOpen_fetchesAccountClient_emitsAudit` | `CachePort.get()` → throws `RuntimeException`; `AccountPort.fetchAccounts()` → 2 `AccountView`s | Returns ranked result from AccountClient (fail-open); audit emitted `result=SUCCESS, cacheHit=false` |
| 6 | `auditAlwaysEmitted_evenOnCacheHit_BR014` | `CachePort.get()` → populated | Audit emitted regardless; confirms BR-014 compliance |
| 7 | `accountCountInAudit_matchesResponseLength` | `AccountPort.fetchAccounts()` → 5 accounts | `AuditEventRecord.accountCount == 5` |
| 8 | `correlationIdPassedToAudit` | Any happy-path | `AuditEventRecord.correlationId` equals the request correlation ID injected into service |

**Orchestration sequence (mandatory):**
```
1. CachePort.get(customerId)
   ├── HIT  → recompute meta.cacheHit=true + refresh correlationId → skip to step 4
   └── MISS → AccountPort.fetchAccounts(customerId) [Resilience4j in adapter]
              → EligibilityPolicy.filter() [already done in adapter — see Layer 4]
              → Ranker.rank()
              → CachePort.put(customerId, rankedDashboard)
4. AuditEventPublisher.publish(AuditEventRecord.success(...))
   [Cache NEVER short-circuits this step — BR-014]
5. Return RankedDashboard
```

**Error path (upstream failure):**
```
AccountPort throws UpstreamUnavailableException
  → AuditEventPublisher.publish(AuditEventRecord.error(...))
  → re-throw UpstreamUnavailableException to controller
```

---

### Layer 4: Infrastructure Adapters

> Package root: `com.bank.balancedashboard.infrastructure`
> Rule: Spring, Kafka, Redis, Jackson wiring lives here.

| Adapter | Port Implemented | Technology | Integration Test |
|---|---|---|---|
| `AccountClientAdapter` | `AccountPort` | `common-libs/account-client-lib` + **Resilience4j** (order: `TimeLimiter → CB → Retry → Bulkhead`) | `UpstreamFailureReturns503Test` — WireMock stubbing `account-service` |
| `RedisCacheRepository` | `CachePort` | Lettuce (`RedisTemplate<String, String>` + Jackson `ObjectMapper`) | `CacheHitAuditEmittedTest` — Testcontainers Redis |
| `KafkaAuditEventPublisher` | `AuditEventPublisher` | `KafkaProducer<String, AuditEventRecorded>` + Apicurio Avro serializer (async, no `.get()`) | `KafkaAuditEventPublisherContractTest` (contract, not Testcontainers) |
| `AvroMapper` | (helper for `KafkaAuditEventPublisher`) | Avro generated class `com.bank.compliance.audit.v2.AuditEventRecorded` | Covered by `KafkaAuditEventPublisherContractTest` |
| `CachedBalanceDashboard` | (Redis payload record) | Jackson-deserializable record | Covered by `RedisCacheRepository` integration tests |

**`AccountClientAdapter` implementation notes:**
- Calls `listAccountsByCustomer(customerId)` from `common-libs/account-client-lib` (operationId: `listAccountsByCustomer`).
- Maps `AccountInfo[]` → applies `EligibilityPolicy` → maps to `AccountView[]`.
- Resilience4j wiring (MANDATORY operator order):
  ```java
  Decorators.ofSupplier(() -> accountClient.listAccountsByCustomer(customerId))
      .withBulkhead(bulkhead)
      .withRetry(retry)
      .withCircuitBreaker(circuitBreaker)
      .withTimeLimiter(timeLimiter, scheduler)
      .get();
  ```
- All failure outcomes (`TimeoutException`, `CallNotPermittedException`, `BulkheadFullException`, HTTP 5xx after retries) throw `UpstreamUnavailableException`.
- HTTP 4xx responses: `ignoreExceptions` in Resilience4j config — do NOT retry.

**`RedisCacheRepository` implementation notes:**
- Key pattern: `balance-dashboard:customer:{customerId}` (prefix from `application.yml`).
- Write (ATOMIC): `redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds))` — `RedisTemplate` translates to `SETEX`. NEVER call `.set()` then `.expire()`.
- On read `RedisException`: log WARN + increment metric `cache_miss_reason_total{reason=REDIS_UNAVAILABLE}` → return `Optional.empty()`.
- On write `RedisException`: log WARN + increment metric — swallow, do NOT throw to application layer.
- `ObjectMapper` config: `WRITE_BIGDECIMAL_AS_PLAIN=true` + no `float` coercion.
- When serving from cache: recompute `meta.cacheHit=true` and refresh `meta.correlationId` to current request trace ID. Only `accounts[]` and `meta.freshness=snapshot` come verbatim from stored JSON.

**`KafkaAuditEventPublisher` implementation notes:**
- Async fire-and-forget: `producer.send(record, callback)`. NO `.get()` anywhere.
- Producer config (`application.yml`): `acks=1`, `enable.idempotence=true`, `max.in.flight.requests.per.connection=5`.
- Key: `record.actorId().toString()` (partition by customer for ordering within customer stream).
- Callback: on exception → log WARN + `meterRegistry.counter("audit_events_total", "result", "FAILED").increment()`. On success → `"result", "PUBLISHED"`.
- Kafka namespace: `com.bank.compliance.audit.v2` MUST match Apicurio-registered schema namespace exactly.
- `publish()` swallows ALL exceptions (Kafka + serialization). Never throws to caller.

---

### Layer 5: REST Controllers + Filters

> Package: `com.bank.balancedashboard.infrastructure.rest`

| Class | Endpoint / Role | Test Cases (`@WebMvcTest`) |
|---|---|---|
| `BalanceDashboardController` | `GET /api/v1/balance-dashboard` — feature-flagged, requires `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` | See detailed test table below |
| `BalanceDashboardDisabledController` | `GET /api/v1/balance-dashboard` — returns `501 Not Implemented` when `balance-dashboard.enabled=false` | `ControllerFeatureFlagTest` — (1) flag OFF → 501 + `application/problem+json`; (2) flag ON → delegates to `LoadDashboardUseCase` |
| `IborCheckFilter` | `OncePerRequestFilter` — IDOR detection only; reads `X-Customer-Id` header (ONLY class permitted to do so) | `IborGuardIntegrationTest` (Layer 6 integration) + `IborCheckFilterUnitTest` — (1) mismatch → 403 + audit FORBIDDEN emitted; (2) match → proceeds; (3) absent header → proceeds |
| `CustomerIdResolver` | Helper bean — extracts UUID from `JwtAuthenticationToken.getToken().getSubject()` | Unit: `CustomerIdResolverTest` — (1) valid UUID sub → returns UUID; (2) null sub → throws `InvalidJwtSubException`; (3) non-UUID sub → throws `InvalidJwtSubException`; (4) no Spring context needed (pure unit test) |
| `ProblemDetailAdvice` | `@RestControllerAdvice` — maps exceptions to RFC 7807 `application/problem+json` | `ProblemDetailAdviceTest` — (1) `UpstreamUnavailableException` → 503 + `code=SERVICE_UNAVAILABLE`; (2) `InvalidJwtSubException` → 403 + `code=FORBIDDEN`; (3) generic `RuntimeException` → 503 (safe fallback, no stack trace leaked) |

**`BalanceDashboardControllerTest` — detailed `@WebMvcTest` test cases:**

| # | Test Method | Setup | Assert |
|---|---|---|---|
| 1 | `getBalanceDashboard_withValidJwt_returns200` | Mock `LoadDashboardUseCase` returns 3-account ranked dashboard; valid JWT with `SCOPE_accounts:read` | HTTP 200; `accounts` array has 3 entries in rank order; `X-Cache: MISS` header; `Cache-Control: private, no-store` |
| 2 | `getBalanceDashboard_cacheHit_returnsXCacheHit` | Mock returns snapshot dashboard; `meta.cacheHit=true` | HTTP 200; `X-Cache: HIT` header; `meta.freshness=snapshot` |
| 3 | `getBalanceDashboard_emptyAccounts_returns200EmptyArray` | Mock returns `accounts=[]`, `accountCount=0` | HTTP 200; `accounts: []`; response is valid `BalanceDashboardResponse` |
| 4 | `getBalanceDashboard_noJwt_returns401` | No `Authorization` header | HTTP 401 + `application/problem+json`; `code=UNAUTHORIZED` |
| 5 | `getBalanceDashboard_wrongScope_returns403` | JWT with scope `transfers:write` only (no `accounts:read`) | HTTP 403 + `application/problem+json`; `code=FORBIDDEN` |
| 6 | `getBalanceDashboard_upstreamDown_returns503` | Mock `LoadDashboardUseCase` throws `UpstreamUnavailableException` | HTTP 503 + `application/problem+json`; `code=SERVICE_UNAVAILABLE`; `Retry-After` header present |
| 7 | `getBalanceDashboard_responseNeverCachedByProxy` | Any 200 response | `Cache-Control: private, no-store` header MUST be present |
| 8 | `getBalanceDashboard_correlationIdInHeader` | Any 200 response | `X-Correlation-Id` response header present and is valid UUID |
| 9 | `getBalanceDashboard_balanceIsString_notNumber` | Mock returns account with `balance="128540.25"` | Serialized JSON has `"balance":"128540.25"` (quoted string), NOT `"balance":128540.25` |
| 10 | `getBalanceDashboard_flagDisabled_returns501` | `balance-dashboard.enabled=false` profile | HTTP 501 + `application/problem+json` |
| 11 | `getBalanceDashboard_noCustomerIdParam_accepted` | Valid JWT, no `customerId` query/path param | HTTP 200 — endpoint takes no `customerId` by construction (IDOR prevention) |

**Controller wiring (mandatory pattern):**
```java
@RestController
@RequestMapping("/api/v1/balance-dashboard")
@PreAuthorize("hasAuthority('SCOPE_accounts:read')")
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "true", matchIfMissing = false)
public class BalanceDashboardController {
    // Only accept @AuthenticationPrincipal Jwt jwt — no @RequestHeader, no @RequestParam for customerId
    @GetMapping
    public ResponseEntity<BalanceDashboardResponse> getDashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID customerId = customerIdResolver.resolve(jwt);
        return ResponseEntity.ok(useCase.loadDashboard(customerId));
    }
}
```

---

### Layer 6: Full Integration + Self-Checks

> Testcontainers: Redis + Kafka (embedded Kafka acceptable for contract test)
> Scope: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@ActiveProfiles("integration")`

| Test Class | Scenario Covered | AC / Condition Verified |
|---|---|---|
| `IborGuardIntegrationTest` | (1) Tampered `X-Customer-Id` → 403 + audit FORBIDDEN emitted with `actorId=JWT.sub(A)` + zero calls to `AccountClient`; (2) Matching header → 200; (3) Absent header → 200 (JWT sub is source of truth) | AC-001-E2, Security C-3, ADR-006 |
| `CacheHitAuditEmittedTest` | Warm-cache request → response from Redis → audit event STILL emitted with `cacheHit=true` (BR-014); second request → same result; `AccountClient` called only once across two requests | AC-001-H3, AC-003-H1, AC-003-H3, BR-014 |
| `UpstreamFailureReturns503Test` | CB-open (WireMock stubbed account-service returns 500 until CB trips) → 503 Problem-Detail; audit emitted `result=ERROR` | AC-003-E1 (partially), impl-notes §3 |

**`KafkaAuditEventPublisherContractTest` (contract, not integration):**

```java
private static final Pattern FORBIDDEN_KEY = Pattern.compile(
    "\"(balance|accountId|accountNumber|accounts|balanceAsOf|currency)\"\\s*:"
);
```

Tests: (1) SUCCESS record byte-grep passes + positive presence of `eventType`, `purpose`, `cacheHit`, `accountCount`; (2) FORBIDDEN record byte-grep passes; (3) ERROR record byte-grep passes; (4) reflection: `AuditEventRecord` component names do not contain any forbidden name.

**`CustomerIdSourceRule` (ArchUnit — mandatory build gate):**
```java
@AnalyzeClasses(packages = "com.bank.balancedashboard")
class CustomerIdSourceRule {
    @ArchTest
    static final ArchRule only_filter_reads_x_customer_id_header = ...
    @ArchTest
    static final ArchRule no_request_header_customer_id_annotation = ...
}
```
This rule MUST pass before any PR is merged. Deliberate-violation test fixture must also be present to confirm the rule fires.

**Build gate:**
```bash
./mvnw clean verify \
  -Dspring.profiles.active=integration \
  -Pcoverage
```

**Coverage thresholds:**
- Overall unit test coverage: **≥ 80%** (line + branch).
- Critical paths (domain policy, application service, security filter chain): **≥ 95%**.
- Specifically: `EligibilityPolicy`, `Ranker`, `BalanceDashboardService`, `IborCheckFilter`, `CustomerIdResolver` — each must be ≥ 95%.

**Additional self-checks:**
```bash
# 1. Confirm no Flyway / JPA in BDS
grep -r 'src/main/resources/db/migration' backend/balance-dashboard-service/  # MUST be empty
grep -rn 'DataSourceAutoConfiguration' backend/balance-dashboard-service/src/main/java/ | grep -v "exclude" # MUST be empty (exclusions only)

# 2. Confirm no forbidden audit fields in serialized output
# Covered by KafkaAuditEventPublisherContractTest FORBIDDEN_KEY pattern

# 3. Confirm customer_id index exists in account-service
cd backend/account-service/src/main/resources/db/migration/
grep -E 'CREATE\s+(UNIQUE\s+)?INDEX.*accounts.*customer_id' V*.sql
# If empty → file V<next>__add_accounts_customer_id_idx.sql in account-service (NOT BDS)

# 4. Emit handoff JSON
# See handoff-impl-planner-001.json
```

---

## FE Task List (banking-frontend-dev)

### Step 1: API Client

> Source: `docs/tech-lead/balance-comparison/openapi/balance-dashboard-service.openapi.yaml`
> Generate via: `openapi-generator-cli` or `ng-openapi-gen` targeting Angular HttpClient.
> Place generated types at: `frontend/src/app/features/balance-dashboard/api/`

| Generated Type / Service | OpenAPI Operation | Used By |
|---|---|---|
| `BalanceDashboardResponse` (interface) | `getBalanceDashboard` → `200` schema | `BalanceDashboardFacade`, `AccountRowComponent` inputs |
| `AccountView` (interface) | `BalanceDashboardResponse.accounts[]` item schema | `AccountRowComponent` input, `@for` track key |
| `ResponseMeta` (interface) | `BalanceDashboardResponse.meta` schema | `BalanceStalenessService`, `BalanceDashboardPageComponent` |
| `ProblemDetail` (interface) | 4xx/5xx error schema | `ErrorStateComponent`, `BalanceDashboardFacade` error handler |
| `AccountType` (enum / union type) | `AccountView.accountType` | `AccountTypeIconPipe`, `AccountTypeLabelPipe` |
| `BalanceDashboardApiService` | `getBalanceDashboard` — `GET /api/v1/balance-dashboard` | `BalanceDashboardFacade` |

**Critical type rules:**
- `AccountView.balance` MUST be typed as `string` in TypeScript — NOT `number`. Format: `^-?\d+\.\d{2}$`.
- `AccountView.accountId` is used ONLY as `@for track` key — MUST NOT be displayed in UI (BA NFR §8).
- `AccountView.rank` is 1-based server-authoritative — FE renders in received array order (no re-sort).
- `Idempotency-Key` header: N/A for this GET endpoint.
- OTel browser instrumentation: add `traceparent` header to outgoing request per common-libs convention (see Step 4).

**`BalanceDashboardApiServiceTest`:**
| # | Test | Assert |
|---|---|---|
| 1 | `getDashboard_happyPath_returns200` | Calls `GET /api/v1/balance-dashboard`; maps response to `BalanceDashboardResponse` |
| 2 | `getDashboard_401_throwsHttpErrorResponse` | HTTP 401 → `HttpErrorResponse` propagated |
| 3 | `getDashboard_503_throwsHttpErrorResponse` | HTTP 503 → `HttpErrorResponse` propagated |
| 4 | `balance_fieldIsString_notParsedAsNumber` | `typeof response.accounts[0].balance === 'string'` |

---

### Step 2: State / Service Layer

> Package: `frontend/src/app/features/balance-dashboard/services/`

| Service | Methods | Test Cases |
|---|---|---|
| `BalanceDashboardFacade` | `loadDashboard(): Observable<BalanceDashboardResponse>` — calls `BalanceDashboardApiService`, applies retry (1s/2s/4s × 3 on 503 only), emits `LoadingState` transitions | `BalanceDashboardFacadeTest` — (1) success path → emits `{state:'loaded', data}`; (2) 503 × 3 → emits `{state:'error', code:'SERVICE_UNAVAILABLE'}`; (3) 401 → emits `{state:'unauthorized'}` → triggers redirect; (4) 403 → emits `{state:'forbidden'}`; (5) empty `accounts[]` → emits `{state:'empty'}` |
| `BalanceStalenessService` | `isFreshnessSnapshot(meta: ResponseMeta): boolean`, `isRowStale(account: AccountView): boolean` | `BalanceStalenessServiceTest` — (1) `freshness='snapshot'` → returns true for banner; (2) `freshness='live'` → returns false; (3) `isStale=true` on row → badge shown; (4) `isStale=false` → no badge |
| `AccountTypeLabelPipe` (or service) | `transform(type: AccountType, locale: 'th'\|'en'): string` — maps SAVINGS→"บัญชีออมทรัพย์"/CURRENT→"บัญชีกระแสรายวัน"/FIXED_DEPOSIT→"บัญชีเงินฝากประจำ" | `AccountTypeLabelPipeTest` — (1) SAVINGS TH; (2) CURRENT TH; (3) FIXED_DEPOSIT TH; (4) SAVINGS EN; (5) unknown type → returns raw enum (safe fallback) |
| `AccountTypeIconPipe` | `transform(type: AccountType): string` — SAVINGS→`banknotes`, CURRENT→`credit-card`, FIXED_DEPOSIT→`lock-closed` | `AccountTypeIconPipeTest` — one test per AccountType value |

**Retry strategy for 503 (FE):**
```typescript
retryWhen(errors => errors.pipe(
  mergeMap((err, attempt) => {
    if (err.status !== 503 || attempt >= 3) return throwError(() => err);
    return timer([1000, 2000, 4000][attempt]);
  })
))
```

---

### Step 3: Presentational Components

> Package: `frontend/src/app/features/balance-dashboard/components/`
> Rule: All styles use `var(--*)` CSS custom properties (ADR-005 §2.5). No `$scss-vars` in component styles.

| Component | Inputs | Outputs | A11y Requirements | Test Cases |
|---|---|---|---|---|
| `AccountRowComponent` | `@Input() account: AccountView`, `@Input() totalCount: number` | (none — display only) | `role="listitem"`; `aria-label="Account {rank} of {totalCount}, {typeLabel}, {maskedNumber}, {balance} baht, last updated {relativeTime}"` per BR-021; balance `aria-label="X baht"` per BR-022 | `AccountRowComponentTest`: (1) renders masked number `****XXXX`; (2) renders balance as formatted string (NOT as number); (3) renders correct icon per `accountType`; (4) `isStale=true` shows staleness badge; (5) `isStale=false` no badge; (6) aria-label includes rank and total; (7) icon stroke uses `var(--color-text-secondary)` |
| `BalanceStalenessBadgeComponent` | `@Input() balanceAsOf: string` (ISO-8601) | (none) | `aria-label="Balance may be outdated"` | `BalanceStalenessComponentTest`: (1) renders "Updated X ago" text; (2) hover tooltip shows absolute timestamp "DD MMM YYYY HH:mm ICT" |
| `BalanceStalenessGlobalBannerComponent` | `@Input() freshness: 'live'\|'snapshot'` | (none) | `role="status"`, `aria-live="polite"` | `BannerTest`: (1) `freshness='snapshot'` → banner visible; (2) `freshness='live'` → banner hidden |
| `BalanceEmptyStateComponent` | (none) | (none) | `role="status"` | `EmptyStateTest`: (1) renders illustration + "You don't have any deposit accounts yet" copy (BA AC-001-E1) |
| `BalanceErrorStateComponent` | `@Input() errorCode: string`, `@Input() correlationId: string` | `@Output() retry = new EventEmitter<void>()` | `role="alert"`, `aria-live="assertive"` | `ErrorStateTest`: (1) `SERVICE_UNAVAILABLE` → retry button visible; (2) `FORBIDDEN` → "Access denied" message, no retry button; (3) `correlationId` rendered for support contact |
| `BalanceLoadingSkeletonComponent` | (none) | (none) | `aria-busy="true"`, `aria-label="Loading accounts"` | `LoadingSkeletonTest`: (1) renders skeleton rows; (2) `aria-busy` attribute present |

**Icon mapping (Heroicons v2 outline, size `1.5rem`, stroke `var(--color-text-secondary)`):**

| `accountType` | Heroicons v2 outline name |
|---|---|
| `SAVINGS` | `banknotes` |
| `CURRENT` | `credit-card` |
| `FIXED_DEPOSIT` | `lock-closed` |

---

### Step 4: Smart Components

> Package: `frontend/src/app/features/balance-dashboard/containers/`

| Component | Services Injected | Handles | Test Cases |
|---|---|---|---|
| `BalanceDashboardPageComponent` | `BalanceDashboardFacade`, `BalanceStalenessService`, `Router` | Subscribes to `facade.loadDashboard()`; manages `LoadingState` transitions; passes `account[]` to `AccountRowComponent`; handles 401→redirect, 403→toast, 503→retry banner; OTel browser tracing integration | `BalanceDashboardPageComponentTest`: (1) on init → calls `loadDashboard()`; (2) loaded state → renders `AccountRowComponent` for each account in RECEIVED ORDER (no re-sort); (3) empty state → renders `BalanceEmptyStateComponent`; (4) 503 → renders `BalanceErrorStateComponent` with retry; (5) 403 → shows "Access denied" toast via toast service; (6) 401 → calls `router.navigate(['/login'])`; (7) `meta.freshness=snapshot` → renders `BalanceStalenessGlobalBannerComponent`; (8) accounts rendered in received order, not sorted by FE |

**OTel browser instrumentation:**
```typescript
// In BalanceDashboardApiService (or HTTP interceptor)
// Add W3C traceparent header from OpenTelemetry SDK per common-libs convention
// X-Correlation-Id from response echoed to OTel span attributes
```

**No re-sort rule (mandatory test):**
```typescript
// Test case 8 in BalanceDashboardPageComponentTest:
const mockAccounts = [
  { rank: 1, accountId: 'z-id', balance: '100.00' },
  { rank: 2, accountId: 'a-id', balance: '50.00' }
];
// After render, verify DOM order matches input array order:
expect(rows[0].querySelector('[data-testid="rank"]').textContent).toBe('1');
expect(rows[1].querySelector('[data-testid="rank"]').textContent).toBe('2');
```

---

### Step 5: Routing + Guards

> Package: `frontend/src/app/features/balance-dashboard/`

| Route | Guard | Lazy Chunk |
|---|---|---|
| `/balance-dashboard` | `AuthGuard` (reuse from money-transfer — redirects to `/login` on 401) | `balance-dashboard.chunk.js` via `loadComponent(() => import('./containers/balance-dashboard-page.component'))` |

**Routing test cases:**
| # | Test | Assert |
|---|---|---|
| 1 | `unauthenticated_redirectsToLogin` | `AuthGuard` blocks; router navigates to `/login` |
| 2 | `authenticated_withScope_activatesRoute` | Guard allows; `BalanceDashboardPageComponent` rendered |
| 3 | `lazyChunk_loadedOnDemand` | Initial bundle does NOT include balance-dashboard code |

---

### Style Dictionary Setup

> ADR-005 — locked pipeline. Must complete before any component styling starts.

**Files to create:**

| File | Description |
|---|---|
| `tools/design-tokens/style-dictionary.config.cjs` | Style Dictionary v4 config — source: `docs/design/_shared/tokens.json` → dual-emit to `frontend/src/styles/tokens.scss` + `frontend/src/styles/tokens.css` |
| `frontend/src/styles/tokens.scss` | AUTO-GENERATED (header comment: `/* AUTO-GENERATED FROM tokens.json — DO NOT HAND-EDIT — see ADR-005 */`) |
| `frontend/src/styles/tokens.css` | AUTO-GENERATED, includes `@media (prefers-reduced-motion: reduce) { :root { --motion-duration-*: 0ms; } }` block |

**npm scripts to add to `package.json`:**
```json
{
  "tokens:build": "style-dictionary build --config tools/design-tokens/style-dictionary.config.cjs",
  "tokens:check": "npm run tokens:build && git diff --exit-code frontend/src/styles/tokens.scss frontend/src/styles/tokens.css"
}
```

**Import order in `frontend/src/styles.scss` (locked by ADR-005 §2.4):**
```scss
@import 'styles/tokens';        // .scss compile-time $vars (breakpoints in @media queries)
@import 'styles/tokens.css';    // runtime :root --vars (component custom props, dark-mode)
@import 'styles/reset';
@import 'styles/typography-base';
@import 'styles/utilities';
```

**CI gate:** `npm run tokens:check` — fails PR if generated outputs lag source (`git diff --exit-code`).

**Alias-flatness check:** `grep -E 'var\(--' frontend/src/styles/tokens.css` MUST return zero matches.

---

## Test Coverage Map (AC → Test)

| AC | Description (summary) | BE Test | FE Test |
|---|---|---|---|
| AC-001-H1 | Ranked list returned with all required fields | `BalanceDashboardServiceTest#cacheMiss_fetchesAndRanks` + `BalanceDashboardControllerTest#getBalanceDashboard_withValidJwt_returns200` + `RankerTest` | `BalanceDashboardPageComponentTest#loaded_rendersAccountRows` |
| AC-001-H2 | Deterministic tie-break equal balances | `RankerTest#equalBalances_tieBreakAccountIdAsc` + `BalanceDashboardServiceTest` | `BalanceDashboardPageComponentTest#rendersInReceivedOrder` (no re-sort) |
| AC-001-H3 | Audit emitted on every retrieval | `BalanceDashboardServiceTest#auditAlwaysEmitted_evenOnCacheHit_BR014` + `CacheHitAuditEmittedTest` | `BalanceDashboardApiServiceTest` (audit is BE concern; FE sends request only) |
| AC-001-H4 | Native currency displayed | `AccountViewTest` + `BalanceDashboardControllerTest#balance_fieldIsString` | `AccountRowComponentTest#rendersCurrencyCode` |
| AC-001-E1 | Empty state — zero eligible accounts | `BalanceDashboardServiceTest#emptyAccounts_returns200WithEmptyArray_emitsAuditAccountCount0` + `BalanceDashboardControllerTest#emptyAccounts_returns200EmptyArray` | `BalanceDashboardPageComponentTest#emptyState_rendersEmptyStateComponent` |
| AC-001-E2 | IDOR attempt → 403 + audit FORBIDDEN | `IborGuardIntegrationTest#tamperedHeader_returnsForbidden_andEmitsAuditForbidden` | `BalanceDashboardFacadeTest#403_emitsForbiddenState` |
| AC-001-E3 | Unauthenticated → 401 | `BalanceDashboardControllerTest#getBalanceDashboard_noJwt_returns401` | `BalanceDashboardFacadeTest#401_triggersRedirect` + routing guard test |
| AC-002-H1 | All display fields correct | `AccountViewTest#allRequiredFieldsPresent` | `AccountRowComponentTest#rendersAllDisplayFields` |
| AC-002-H2 | FIXED_DEPOSIT shows principal+interest | `EligibilityPolicyTest#fixedDepositPasses` + ASSUMPTION-TL-001 integration test vs staging | `AccountRowComponentTest#fixedDepositLabelCorrect` |
| AC-002-H3 | Masked account number never exposes full number | `AccountViewTest#maskedNumberPattern` (BE never receives full number — already masked by account-service) | `AccountRowComponentTest#maskedNumberNoFullDigits` |
| AC-002-H4 | All 3 account type labels correct | `EligibilityPolicyTest#allThreeTypePass` | `AccountTypeLabelPipeTest#allThreeTypes` + `AccountRowComponentTest#typeLabelRendered` |
| AC-002-E1 | Stale `balanceAsOf` displayed with badge | `AccountView.isStale` computation (server-computed: `now() - balanceAsOf > 60s`) in `AccountClientAdapter` or `BalanceDashboardService` | `AccountRowComponentTest#isStale_showsBadge` + `BalanceStalenessComponentTest` |
| AC-002-E2 | Null `balanceAsOf` → "–" display, account still included | `AccountClientAdapter` null-safety mapping | `AccountRowComponentTest#nullBalanceAsOf_showsDash` |
| AC-003-H1 | Warm-cache p95 < 500ms | `CacheHitAuditEmittedTest` confirms Redis served + `UpstreamFailureReturns503Test` baseline | k6/Gatling staging test: 10-account fixture, warm cache, 95th pct < 500ms |
| AC-003-H2 | Cold-cache p95 < 800ms | `BalanceDashboardServiceTest#cacheMiss_fetchesAndRanks` | k6/Gatling staging test: cold-cache, 95th pct < 800ms |
| AC-003-H3 | Audit emitted regardless of cache state | `CacheHitAuditEmittedTest` (Testcontainers) | N/A (BE concern) |
| AC-003-H4 | Cache includes `balanceAsOf` — not substituted | `RedisCacheRepository` integration: cached `balanceAsOf` round-trips correctly | `AccountRowComponentTest#balanceAsOf_fromCache_notCurrentTime` |
| AC-003-E1 | Redis unavailable → fail-open | `BalanceDashboardServiceTest#redisFailure_failOpen_fetchesAccountClient` + integration (WireMock Redis failure) | `BalanceDashboardFacadeTest#503_retryBannerShown` |
| AC-003-E2 | Cache hit ratio observable in Grafana | DevOps P2 Grafana panel (not a unit test); metrics `cache_miss_reason_total` instrumented in `RedisCacheRepository` | N/A (ops observable) |
| AC-005-H1 | No horizontal scroll at 375px | N/A | `AccountRowComponentTest#noHorizontalScroll_375px` (Angular CDK testing harness + viewport) |
| AC-005-H2 | Screen reader announces rank, type, balance | N/A | `AccountRowComponentTest#ariaLabel_containsRankTypeBalanceLastUpdated` |
| AC-005-H3 | Keyboard Tab navigation through rows | N/A | `BalanceDashboardPageComponentTest#keyboardNavigation_tabOrder` |
| AC-005-H4 | Lighthouse a11y >= 90 | N/A | QA P2 — Lighthouse CI report on staging URL |
| AC-005-E1 | 200% font size — no clipping | N/A | `AccountRowComponentTest#fontScale200_noClipping` (CSS snapshot or Playwright test) |
| AC-005-E2 | Balance `aria-label` prevents digit-by-digit reading | N/A | `AccountRowComponentTest#balance_ariaLabel_fullAmount` |

**AC Coverage: 25/25 — 100%** (all 25 in-scope AC from BA user-stories.md covered by ≥ 1 test).

---

## Security Conditions Traceability

| Condition | Description | BE Class Responsible | Test |
|---|---|---|---|
| **C-2** | Audit event schema — metadata only. Forbidden fields: `balance`, `accountId`, `accountNumber`, `accounts[]`, `balanceAsOf`, `currency` | `AuditEventRecord` (record type signature as contract), `KafkaAuditEventPublisher` (adapter), `AvroMapper` | `KafkaAuditEventPublisherContractTest` — (1) byte-grep regex `"(balance\|accountId\|accountNumber\|accounts\|balanceAsOf\|currency)"\s*:` must find ZERO matches in serialized SUCCESS, FORBIDDEN, ERROR records; (2) reflection test: `AuditEventRecord.getRecordComponents()` names must not contain forbidden names |
| **C-3** | `customerId` source of truth = JWT `sub` only. Header `X-Customer-Id` used for IDOR detection only, never as `customerId` value for business logic | `CustomerIdResolver` (only resolver), `IborCheckFilter` (only header reader), `BalanceDashboardController` (calls `customerIdResolver.resolve(jwt)` only) | `CustomerIdResolverTest` (unit); `IborGuardIntegrationTest` (integration: tampered header → 403); `CustomerIdSourceRule` (ArchUnit — fails build if any class outside `IborCheckFilter` calls `request.getHeader("X-Customer-Id")`, or any `@RestController` uses `@RequestHeader` bound to `X-Customer-Id`) |
| **C-4** | Redis TLS in-transit + AES-256-GCM at-rest | `application.yml`: `spring.data.redis.ssl.enabled=true`. At-rest is cluster-level (DevOps P1 to verify via `CONFIG GET maxclients` + cluster encryption capability by D5) | DevOps P1 infra-verification (not a unit test). Documented in ASSUMPTION-TL-004. If cluster cannot meet C-4 → escalate to PM; BDS cannot ship to staging until resolved. |
| **C-1** | Privacy notice covers "balance inquiry via digital channels" | N/A (SA/compliance coordination — out of sprint code scope) | Pre-GA checklist item. Demo on synthetic users is unaffected. |

---

## Interface Contracts (shared — BE and FE MUST honor exactly)

> These shapes are locked. BE serializes them; FE deserializes them. Neither side may rename fields or change types without amending this section and the OpenAPI spec.

### `BalanceDashboardResponse` (canonical)

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
    }
  ],
  "meta": {
    "accountCount": 1,
    "freshness": "live",
    "cacheHit": false,
    "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60"
  }
}
```

**Field-level contract (BE ↔ FE must agree exactly):**

| Field | Java type | TypeScript type | Serialization rule |
|---|---|---|---|
| `rank` | `int` | `number` (integer) | JSON number |
| `accountId` | `UUID` | `string` (uuid format) | Lowercase UUID string — FE uses as `@for track` key ONLY |
| `accountNumberMasked` | `String` | `string` | Pattern `^\*+\d{4}$`; NEVER full account number |
| `accountType` | `AccountType` enum | `'SAVINGS' \| 'CURRENT' \| 'FIXED_DEPOSIT'` | Exact enum string |
| `balance` | `BigDecimal` | `string` | **String (NOT number)**. Format `^-?\d+\.\d{2}$`. Never `float`/`double` |
| `currency` | `String` | `string` | ISO 4217, `^[A-Z]{3}$` |
| `balanceAsOf` | `Instant` | `string` | ISO 8601 UTC (`format: date-time`) |
| `isStale` | `boolean` | `boolean` | Server-computed (`now() - balanceAsOf > 60s`) |
| `displayLabel` | `String` (enum key) | `'account.type.savings' \| 'account.type.current' \| 'account.type.fixedDeposit'` | i18n key; FE resolves via `account.*` namespace |
| `meta.accountCount` | `int` | `number` (integer) | Must match `accounts.length` exactly |
| `meta.freshness` | `String` | `'live' \| 'snapshot'` | v1: no `'stale'` value — v1.1 only |
| `meta.cacheHit` | `boolean` | `boolean` | Observability only — FE MUST NOT display to customer |
| `meta.correlationId` | `UUID` | `string` | OTel trace ID; echoed in `X-Correlation-Id` response header |

### `ProblemDetail` error shape (canonical, RFC 7807)

```json
{
  "type": "https://errors.bank.local/balance-dashboard/unavailable",
  "title": "ไม่สามารถโหลดข้อมูลได้ในขณะนี้",
  "status": 503,
  "detail": "กรุณาลองใหม่อีกครั้งในอีกสักครู่",
  "instance": "/api/v1/balance-dashboard",
  "correlationId": "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60",
  "code": "SERVICE_UNAVAILABLE"
}
```

**`Content-Type: application/problem+json`** on ALL 4xx/5xx responses.
**`code` enum:** `UNAUTHORIZED` | `FORBIDDEN` | `RATE_LIMIT_EXCEEDED` | `SERVICE_UNAVAILABLE`.
FE branches behavior on `code` field (not `status` alone).

### Response headers (mandatory for FE handling)

| Header | On | Rule |
|---|---|---|
| `X-Cache` | `200` | `HIT` or `MISS` — FE MAY log to OTel span |
| `X-Correlation-Id` | All responses | UUID — echoed from `meta.correlationId`; display in `BalanceErrorStateComponent` |
| `Cache-Control` | `200` | Always `private, no-store` — FE MUST NOT attempt to cache |
| `Retry-After` | `503`, `429` | Seconds to wait — FE retry banner respects this |

---

## Assumptions to Carry Forward

| ID | Description | Owner | Verification Trigger | Fallback |
|---|---|---|---|---|
| ASSUMPTION-TL-001 | `AccountInfo.balance` for `accountType=FIXED_DEPOSIT` = principal + accrued interest (not principal-only) per SUBDEC-001 resolution | `banking-backend-dev` | Integration test vs staging `account-service` / `ledger-service` during Layer 4 implementation | Escalate to PM per impl-notes §11. `EligibilityPolicy` can exclude `FIXED_DEPOSIT` via `balance-dashboard.eligible-types` config flip (no code change). Ship v1 with principal-only + known-issue demo note. |
| ASSUMPTION-TL-002 | JWT scope registered in identity-service is `accounts:read` (exact match for `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")`) | `banking-backend-dev` | Integration test against identity-service staging; `IborGuardIntegrationTest#validScope_returns200` | Adjust `@PreAuthorize` literal to match registered scope name. Notify SA to update NFR `security_jwt_scope_accounts_read`. |
| ASSUMPTION-TL-003 | Resilience4j defaults hold against real staging `account-service` latency: timeout 300ms / retry 2× (100ms/200ms) / CB 50%/100/30s / bulkhead 20 concurrent | `banking-qa` | QA P1 shift-left cold-cache p95 measurement with 10-account fixture at staging (by D3) | (a) Raise timeout to 500ms + re-baseline cold-cache SLA to 1000ms with BA sign-off; OR (b) request account-service optimization. Do NOT silently bump. |
| ASSUMPTION-TL-004 | Shared Redis cluster has: (a) AES-256-GCM at-rest encryption enabled (Security C-4), AND (b) headroom for BDS pool: 16 conn/pod × 2 pods = 32 steady-state connections within cluster `maxclients` budget | `banking-devops` | Infra inspection by D5: `CONFIG GET maxclients` + `CLIENT LIST \| wc -l` per shard; headroom must be > 30% of `maxclients` | (a) Encryption gap: enable cluster-wide OR provision dedicated BDS Redis namespace. (b) Pool saturation: reduce per-pod pool to 8 (`max-active: 8`), OR negotiate `maxclients` headroom with platform team. |

---

*Implementation Task Plan generated by `banking-implementation-planner` · 2026-05-21 · G4 quality gate*
