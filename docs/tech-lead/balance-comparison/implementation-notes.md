# Implementation Notes — Balance Comparison Dashboard

> **Feature slug:** `balance-comparison`
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Artifact:** TL-001 (companion to [openapi/](./openapi/), [db-schema.md](./db-schema.md), [adrs/](./adrs/))
> **Author:** `banking-tech-lead`
> **Audience:** `banking-backend-dev`, `banking-frontend-dev`, `banking-devops`, `banking-qa`

This document collects the **execution-level guidance** that backs the OpenAPI spec, DB-schema decision, and ADRs. It is the single page a developer reads before opening their IDE.

---

## §1. Maven module layout (BDS)

```
backend/balance-dashboard-service/
├── pom.xml                                       # depends on common-libs/account-client-lib, audit-lib, observability-lib
├── src/main/java/com/bank/balancedashboard/
│   ├── BalanceDashboardServiceApplication.java   # excludes DataSource* autoconfig (see §2)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── AccountView.java                  # immutable record — ranked, masked, view-projected
│   │   │   ├── BalanceSnapshot.java              # the unranked aggregate
│   │   │   └── RankedDashboard.java              # snapshot + freshness metadata
│   │   ├── policy/
│   │   │   ├── EligibilityPolicy.java            # filter to ACTIVE + SAVINGS/CURRENT/FIXED_DEPOSIT
│   │   │   └── Ranker.java                       # balance DESC, accountId ASC tie-break (ADR-004 SA)
│   │   ├── audit/
│   │   │   ├── AuditEventRecord.java             # the metadata-only value object (ADR-007)
│   │   │   ├── Channel.java                      # enum MOBILE_BANKING / WEB / API
│   │   │   └── Result.java                       # enum SUCCESS / FAILURE / FORBIDDEN / ERROR
│   │   └── port/
│   │       └── in/
│   │           └── LoadDashboardUseCase.java     # primary port (driven by controller)
│   ├── application/
│   │   ├── service/
│   │   │   └── BalanceDashboardService.java      # use-case impl; orchestrates cache → client → rank → audit
│   │   └── port/out/
│   │       ├── AccountPort.java                  # secondary port — implemented by AccountClientAdapter
│   │       ├── CachePort.java                    # secondary port — implemented by RedisCacheRepository
│   │       └── AuditEventPublisher.java          # secondary port (ADR-007) — impl: KafkaAuditEventPublisher
│   └── infrastructure/
│       ├── rest/
│       │   ├── BalanceDashboardController.java   # GET /api/v1/balance-dashboard
│       │   ├── CustomerIdResolver.java           # ADR-006 — JWT sub → UUID, the only resolver
│       │   ├── IborCheckFilter.java              # ADR-006 — header tamper detection, the only header reader
│       │   └── ProblemDetailAdvice.java          # RFC 7807 errors per OpenAPI
│       ├── client/
│       │   └── AccountClientAdapter.java         # wraps common-libs/account-client-lib; Resilience4j here
│       ├── cache/
│       │   ├── RedisCacheRepository.java         # Lettuce-based; key prefix balance-dashboard:customer:{customerId}
│       │   └── CachedBalanceDashboard.java       # Jackson-deserializable record (db-schema §2.4)
│       └── audit/
│           ├── KafkaAuditEventPublisher.java     # ADR-007 adapter — async fire-and-forget
│           └── AvroMapper.java                   # AuditEventRecord → com.bank.compliance.audit.v2.AuditEventRecorded
├── src/main/resources/
│   ├── application.yml                           # base config (see §3, §4)
│   ├── application-staging.yml                   # feature flag = true
│   ├── application-prod.yml                      # feature flag = false until BoT sign-off
│   └── logback-spring.xml                        # reuses observability-lib JSON encoder + masking filter
│   # NB: NO db/migration/ directory. NO Flyway. (db-schema §1 + §2.1)
└── src/test/java/com/bank/balancedashboard/
    ├── unit/                                     # Ranker, EligibilityPolicy, CustomerIdResolver — pure Java
    ├── integration/                              # @SpringBootTest with Testcontainers (Redis, Kafka)
    │   ├── IborGuardIntegrationTest.java         # ADR-006 acceptance
    │   ├── CacheHitAuditEmittedTest.java         # BR-014 acceptance
    │   └── UpstreamFailureReturns503Test.java    # SA event-flows §6
    ├── contract/
    │   └── KafkaAuditEventPublisherContractTest.java  # ADR-007 — forbidden-field byte-grep (see §12)
    └── arch/
        └── CustomerIdSourceRule.java             # ArchUnit — ADR-006 §2.4
```

**Reasoning:** strict hexagonal layout per SA `tech_choices.hexagonal_layers`. Domain has **zero** Spring or Kafka or Redis imports. Application has Spring `@Service` only. Infrastructure owns all wiring.

---

## §2. `@SpringBootApplication` exclusions (no-RDBMS service)

```java
package com.bank.balancedashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class BalanceDashboardServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BalanceDashboardServiceApplication.class, args);
    }
}
```

**Why all three exclusions:** any one of these, if present and unable to find a DataSource bean, will fail the context with `Failed to determine a suitable driver class`. Per [db-schema §2.1](./db-schema.md#21-what-no-rdbms-means-concretely), BDS has no `spring-boot-starter-data-jpa` or `spring-boot-starter-jdbc` on the classpath — but these autoconfigs may be transitively pulled by shared starters, so exclude defensively.

---

## §3. Resilience4j configuration (AccountClient)

`application.yml` snippet (matches SA `tech_choices.resilience` + `resilience_*` NFRs):

```yaml
resilience4j:
  timelimiter:
    instances:
      account-client:
        timeoutDuration: 300ms
        cancelRunningFuture: true
  retry:
    instances:
      account-client:
        maxAttempts: 3   # 1 initial + 2 retries
        waitDuration: 100ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2     # 100ms, 200ms
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException   # 5xx only
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException   # 4xx — never retry
  circuitbreaker:
    instances:
      account-client:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 100
        failureRateThreshold: 50          # %
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 10
        minimumNumberOfCalls: 20
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException
  bulkhead:
    instances:
      account-client:
        maxConcurrentCalls: 20
        maxWaitDuration: 0ms             # fail-fast — do not queue
```

### Operator ordering (critical)

Resilience4j operator order in the adapter MUST be:

```
TimeLimiter (outer)
  → CircuitBreaker
    → Retry
      → Bulkhead (innermost)
```

```java
// AccountClientAdapter.java
return Decorators.ofSupplier(() -> accountClient.listAccountsByCustomer(customerId))
    .withBulkhead(bulkhead)
    .withRetry(retry)
    .withCircuitBreaker(circuitBreaker)
    .withTimeLimiter(timeLimiter, scheduler)
    .get();
```

**Why this order:** Time-limiter is outermost so the *whole* attempt budget (with retries) is bounded — otherwise the retry could exceed the per-request deadline. CB sees only the outcome that escapes retry, so transient flakes don't trip it. Bulkhead is innermost so concurrent-call accounting is per-attempt, not per-retried-attempt.

### Failure mode contract

| Resilience4j outcome | Caller sees | HTTP returned |
|---|---|---|
| Timeout (`TimeoutException`) | `UpstreamUnavailableException` | 503 + Problem-Detail |
| CB-OPEN (`CallNotPermittedException`) | `UpstreamUnavailableException` | 503 + Problem-Detail |
| Bulkhead reject (`BulkheadFullException`) | `UpstreamUnavailableException` | 503 + Problem-Detail (+ Retry-After: 1) |
| HTTP 5xx after all retries | `UpstreamUnavailableException` | 503 + Problem-Detail |

All paths emit `AuditEventRecord.error(...)` per ADR-007 §2.5 site 3.

---

## §4. Redis (Lettuce) configuration

`application.yml` snippet:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      ssl:
        enabled: true                     # Security C-4 — TLS in transit; at-rest is cluster-level
      password: ${REDIS_PASSWORD}         # injected from Vault; rotation per platform policy
      timeout: 200ms                      # cheap fail-fast; fall through to AccountClient
      lettuce:
        pool:
          enabled: true
          max-active: 16                  # per pod (SA tech_choices.cache)
          max-idle: 16
          min-idle: 4
          max-wait: 50ms                  # do not block long if pool saturated

balance-dashboard:
  cache:
    key-prefix: "balance-dashboard:customer:"
    ttl-seconds: 30                       # SA ADR-002 — TTL-only, no active invalidation in v1
```

### Cache write — atomic with TTL

```java
// RedisCacheRepository.java — ALWAYS use SETEX (atomic SET + TTL) — NEVER SET then EXPIRE
redisTemplate.opsForValue().set(
    keyFor(customerId),
    objectMapper.writeValueAsString(cached),
    Duration.ofSeconds(ttlSeconds)        // RedisTemplate translates to SETEX semantics
);
```

Race window of `SET then EXPIRE` (TTL not applied if pod dies between calls) is forbidden — Redis would retain the entry forever.

### Cache failure mode — fail-open (no CB)

Per SA `availability_redis_fail_open` NFR: catch Lettuce `RedisException` → log + increment metric `cache_miss_reason_total{reason=REDIS_UNAVAILABLE}` → fall through to AccountClient. **No circuit breaker on Redis.** Lettuce's built-in retry + the 200ms timeout above is the only resilience layer.

---

## §5. Feature flag

```yaml
balance-dashboard:
  enabled: false                          # default; explicitly true in staging
```

Wired via Spring `@ConditionalOnProperty` on the controller bean:

```java
@RestController
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "true", matchIfMissing = false)
public class BalanceDashboardController { ... }
```

**Disabled response shape — return 501, NOT 404:**

When the flag is off, the controller bean does not exist → Spring returns 404 by default. This is undesirable: ops cannot distinguish "feature disabled" from "URL typo" in logs.

Solution: register a stub controller via `@ConditionalOnMissingBean(BalanceDashboardController.class)`:

```java
@RestController
@RequestMapping("/api/v1/balance-dashboard")
@ConditionalOnProperty(name = "balance-dashboard.enabled", havingValue = "false", matchIfMissing = true)
public class BalanceDashboardDisabledController {
    @GetMapping
    public ResponseEntity<ProblemDetail> disabled() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED);
        pd.setType(URI.create("https://docs.bank.com/errors/feature-disabled"));
        pd.setTitle("Feature disabled");
        pd.setDetail("balance-dashboard is disabled in this environment.");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(pd);
    }
}
```

---

## §6. TL assumptions register (open)

These four assumptions are PASSED FORWARD to dev/security/devops to verify or invalidate during implementation. If invalidated, escalate to PM.

| ID | Assumption | Owner to verify | Trigger | Fallback |
|---|---|---|---|---|
| **ASSUMPTION-TL-001** | `AccountInfo.balance` for `accountType=FIXED_DEPOSIT` carries **principal + accrued interest as of `balance_as_of`** (per BA SUBDEC-001 confirmation) | `banking-backend-dev` (during AccountClient integration test against staging account-service / ledger-service) | If balance is principal-only, FIXED_DEPOSIT rows misrepresent customer's value → BA AC-001 violated | Escalate to PM/BA per SUBDEC-001 fallback rule. **BDS MUST NOT call ledger-service directly** to add accrued interest — that's ledger-service scope. See [§11 subdec-001-fallback](#11-subdec-001-fallback). |
| **ASSUMPTION-TL-002** | JWT scope `accounts:read` is the exact name registered in identity-service OAuth2 client config | `banking-backend-dev` (during integration test using identity-service staging) | Spring `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` will return 403 if name differs | Adjust `@PreAuthorize` to match registered scope; document the correct name in the BDS controller javadoc. Notify SA to update NFR `security_jwt_scope_accounts_read`. |
| **ASSUMPTION-TL-003** | Resilience4j defaults (timeout 300ms / retry 2x exp 100/200 / CB 50%/100/30s / bulkhead 20) hold against real staging account-service latency | `banking-qa` (P1 shift-left perf test with realistic 10-account fixture by D3) | If staging p95 > 300ms, time-limiter trips on healthy calls → cold-cache SLA broken | Either (a) raise timeout to 500ms and re-baseline p95 cold to 1000ms with BA sign-off, OR (b) ask account-service team to optimize. NOT silently bump the timeout. |
| **ASSUMPTION-TL-004** | Shared Redis cluster (a) has at-rest AES-256-GCM encryption enabled (Security C-4), AND (b) has headroom for BDS pool: 16 conn/pod × 2 pods = 32 connections within cluster `maxclients` budget | `banking-devops` (infra inspection by D5) | If at-rest encryption disabled → Security C-4 violation, cannot ship. If `maxclients` saturated → Redis returns connection errors under load → cache fail-open kicks in but every request becomes cold cache. | (a) Encryption: enable cluster-wide OR provision dedicated namespace with encryption — DevOps owns. (b) Pool: reduce per-pod pool to 8, OR negotiate `maxclients` headroom with platform. See [§10 redis-pool-risk](#10-redis-pool-risk). |

---

## §7. `accounts(customer_id)` index — verify, do not author

Per [db-schema §3.1](./db-schema.md#31-index-requirement-verify-do-not-author): the new account-service endpoint `GET /api/v1/accounts?customerId={uuid}` does an indexed scan on `accounts(customer_id)`. **The index very likely already exists** (money-transfer's payee-verification flow uses it), but verify, don't assume.

**Backend-dev action:**
1. `cd backend/account-service/src/main/resources/db/migration/`
2. `grep -E 'CREATE\s+(?:UNIQUE\s+)?INDEX.*accounts.*customer_id' V*.sql`
3. If match → done, no action.
4. If no match → file new migration in `account-service` (NOT BDS):

```sql
-- V<next>__add_accounts_customer_id_idx.sql
-- Supports balance-dashboard-service GET /api/v1/accounts?customerId={uuid}.
-- B-tree on a UUID column; ~16 bytes per leaf entry; negligible storage cost.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_customer_id
    ON accounts (customer_id);
```

`CONCURRENTLY` to avoid locking the live `accounts` table during deploy. Reversibility: `DROP INDEX CONCURRENTLY IF EXISTS idx_accounts_customer_id;` — file as the `down_sql` in the migration's accompanying ADR-note.

---

## §8. Frontend-dev notes (cross-handoff context)

> Read this section, then jump to the [Designer-002 HI-FI handoff](../../design/balance-comparison/handoff-designer-002.json) for visual specs.

### 8.1 Design tokens consumption (ADR-005)

- Tool: **Style Dictionary** (locked).
- Config skeleton (FE-dev or TL drops this in):

```js
// tools/design-tokens/style-dictionary.config.cjs
module.exports = {
  source: ['docs/design/_shared/tokens.json'],
  platforms: {
    scss: {
      transformGroup: 'scss',
      buildPath: 'frontend/src/styles/',
      files: [{
        destination: 'tokens.scss',
        format: 'scss/variables',
        options: { outputReferences: false }   // alias-flatten (ADR-005 §2.2)
      }]
    },
    css: {
      transformGroup: 'css',
      buildPath: 'frontend/src/styles/',
      files: [{
        destination: 'tokens.css',
        format: 'css/variables',
        options: { outputReferences: false, selector: ':root' }
      }, {
        // mandatory reduced-motion override block (ADR-005 §2.2, Designer §2.3)
        destination: 'tokens.css',
        format: 'css/reduced-motion-overrides',  // custom format — see scripts/tokens/reduced-motion-format.cjs
        filter: token => token.attributes?.category === 'motion' && token.attributes?.type === 'duration',
        options: { selector: '@media (prefers-reduced-motion: reduce) { :root', closing: '} }' }
      }]
    }
  }
};
```

- Import order in `frontend/src/styles.scss`: **tokens.scss → tokens.css → reset → typography-base → utilities** (locked by ADR-005 §2.4).
- Component styles MUST use `var(--*)` not `$*` (ADR-005 §2.5).
- CI: `npm run tokens:check` fails the PR if generated outputs lag the source.

### 8.2 No re-sort on the client

Server returns the dashboard pre-ranked (SA ADR-004). FE renders in array order. **Do NOT re-sort on the client** — it breaks deterministic ordering and the `rank` field becomes meaningless. The `aria-label="Account {rank} of {total}"` is server-authoritative (SA NFR `accessibility_wcag_aa_lighthouse_90`).

### 8.3 Stale banner is driven by `meta.freshness`

The response carries `meta.freshness` of value `"live" | "snapshot"` plus per-row `isStale` (server-derived: `now() - balanceAsOf > 60s`). FE rule:

| `meta.freshness` | Per-row `isStale` | UI |
|---|---|---|
| `"live"` (cache miss / refresh) | false | Normal row |
| `"live"` | true | Row gets "Updated 5m ago" caption + reduced-emphasis text-secondary color (one stale row in an otherwise-fresh response) |
| `"snapshot"` (cache hit) | irrelevant for banner | Top banner "Showing cached data from N seconds ago" |

### 8.4 Heroicons mapping (icon-by-type)

| `accountType` | Heroicons v2 outline name | Rationale |
|---|---|---|
| `SAVINGS` | `banknotes` | Money-stacked symbology |
| `CURRENT` | `credit-card` | Daily-transactional symbology |
| `FIXED_DEPOSIT` | `lock-closed` | "Locked for term" symbology — instantly readable as time-bound |

Apply `--color-text-secondary` to icon stroke; size `1.5rem` for row alignment.

### 8.5 Idempotency-Key

Not applicable. This is a **read-only GET** endpoint. No `Idempotency-Key` header is sent. (See Security hard-rule #7 — N/A for reads.)

### 8.6 Error UX

- 401 — silently redirect to login (existing handler from money-transfer).
- 403 — surface "Access denied" toast + log to OTel; do NOT auto-retry.
- 503 — show retry banner "Unable to load accounts. Tap to retry." with exponential backoff (1s / 2s / 4s, cap 3 attempts).
- Empty 200 (`accounts: []`) — show empty-state illustration + "You don't have any deposit accounts yet" copy (BA AC-001-E1).

---

## §9. DevOps P1 notes (CI/CD skeleton, shift-left)

DevOps P1 runs **in parallel** with backend/frontend dev. Scope:

### 9.1 Helm chart skeleton

```
infra/helm/balance-dashboard-service/
├── Chart.yaml
├── values.yaml                    # min replicas 2, env=staging feature flag true
├── values-prod.yaml               # env=prod feature flag false
├── templates/
│   ├── deployment.yaml            # 2 replicas across 2 AZs (topologySpreadConstraints)
│   ├── service.yaml               # ClusterIP — gateway-only ingress
│   ├── hpa.yaml                   # CPU 70% + custom metric balance_dashboard_requests_total rate > 30/s/pod
│   ├── networkpolicy.yaml         # ingress: api-gateway only · egress: account-service + Redis + Kafka + Apicurio
│   ├── configmap.yaml
│   ├── secret.yaml                # placeholders for VAULT-injected REDIS_PASSWORD, KAFKA_SASL_*
│   └── servicemonitor.yaml        # Prometheus scrape /actuator/prometheus
```

Cross-reference: SA `tech_choices.deployment`, NFR `availability_99_9_pct_bds`, `scalability_50_peak_concurrent`.

### 9.2 Apicurio v2 schema registration (BLOCKING for D8 deploy)

Per SA `events[0].schema_ref` + ADR-003 sequencing + db-schema §5: register **`com.bank.compliance.audit.v2.AuditEventRecorded`** in Apicurio **by D6**. Smoke test on D7 (produce/consume with both v1 + v2 producers; verify backward-compatibility).

If registration slips, BDS deploy on D8 will fail — KafkaAuditEventPublisher's Avro serializer rejects at startup. Track as DevOps blocker, escalate to PM if D6 missed.

### 9.3 Feature flag bootstrap

- Staging: `balance-dashboard.enabled=true` (BDS reachable on staging cluster).
- Prod: `balance-dashboard.enabled=false` (BDS deployed but returns 501; flipped only after BoT sign-off).

### 9.4 Observability wiring

- Prometheus scrape from `/actuator/prometheus` on port 8081 (per money-transfer convention).
- Grafana dashboard `balance-dashboard-overview.json` — panels: request rate, error rate, p95 latency, cache hit ratio, audit-event rate, excluded-account counter (DevOps P2).
- AlertManager rules (DevOps P2): error rate > 1%/5min · p95 warm > 500ms / 5min · `audit_events_total` rate == 0 / 2min (silent compliance failure) · `audit_events_total{result=FAILED}` rate > 0/5min.

---

## §10. redis-pool-risk

> Anchor target of [ASSUMPTION-TL-004](#6-tl-assumptions-register-open) and [SA-RISK-004](../../sa/balance-comparison/handoff-sa-001.json).

**Math:**
- Pool size per pod: **16** connections (Lettuce, `max-active=16`).
- Replica count per env: **2** (min, baseline; HPA can scale up).
- Steady-state connections: **16 × 2 = 32**.
- Under HPA peak (e.g., 4 pods): **16 × 4 = 64**.
- Combined with money-transfer existing pool (also on shared cluster — assume ~64 connections existing): total **~96–128** connections from this customer base alone.

**Verification action (DevOps P1, by D5):**
1. Inspect Redis cluster config: `CONFIG GET maxclients` (default 10000 — almost always plenty, but verify).
2. Inspect current cluster connection count: `CLIENT LIST | wc -l` on each shard.
3. Compute headroom: `maxclients - current_connections - 128`. Must be > 30% of `maxclients`.
4. If insufficient: either (a) reduce BDS pool to 8/pod (acceptable for 50 peak concurrent users — sliding from `16 × 4 = 64` to `8 × 4 = 32` doesn't impact the workload), or (b) request platform to raise `maxclients`.

**Why this matters:** Lettuce returns errors immediately when pool is exhausted (with `max-wait: 50ms`, see §4) — cache fail-open kicks in, but every request becomes cold cache (≥ 300ms vs ≤ 50ms warm). SLA NFR `performance_p95_lt_500ms_warm` would degrade silently.

---

## §11. subdec-001-fallback

> Anchor target of [ASSUMPTION-TL-001](#6-tl-assumptions-register-open) and SA SUBDEC-001 resolution.

**Trigger:** During integration test against staging account-service / ledger-service, the BE-dev observes that `AccountInfo.balance` for `accountType=FIXED_DEPOSIT` returns **principal only** (no accrued interest).

**Fallback procedure (do NOT silently fix in BDS):**

1. **Do not** attempt to compute accrued interest in BDS. BDS is a thin aggregator and has no rate / day-count-fraction / accrual-table knowledge. **BDS MUST NOT call ledger-service directly.**
2. **Do not** introduce a `LedgerClient` to BDS. That would (a) violate ADR-001 (BDS bounded context), (b) expand BDS blast radius, (c) duplicate accrual logic outside ledger-service.
3. **Escalate** to PM (`banking-pm`) with:
   - Test evidence: integration test name + snapshot of returned `AccountInfo` for the FIXED_DEPOSIT fixture.
   - Impact: BA AC-001 ("balance reflects customer's value") is violated.
   - Options for PM/BA to choose:
     - **Option A:** `ledger-service` extends `AccountInfo.balance` to include accrued interest at the source. Ticket created on ledger-service team's backlog. **BDS scaffold continues with current contract; FIXED_DEPOSIT rows ship as principal-only in v1 with a known-issue note in the demo script.**
     - **Option B:** Defer FIXED_DEPOSIT support to v1.1. BDS filters out FIXED_DEPOSIT from `EligibilityPolicy` until ledger fix lands. Updates BA story scope.
4. **Default action** (if PM unavailable during the sprint window): proceed with Option A; flag in standup; ship v1 with principal-only and the known-issue note.

Implementation hook: `EligibilityPolicy` carries a configurable allow-list:

```java
@Component
public class EligibilityPolicy {
    private final Set<AccountType> eligibleTypes;
    public EligibilityPolicy(@Value("${balance-dashboard.eligible-types:SAVINGS,CURRENT,FIXED_DEPOSIT}")
                             Set<AccountType> eligibleTypes) {
        this.eligibleTypes = eligibleTypes;
    }
    // ...
}
```

So Option B is a config flip (no code change), not a redeploy.

---

## §12. audit-publisher-contract-test

> Anchor target of ADR-007 §2.6 acceptance criteria. The Security C-2 enforcement test.

**Class:** `backend/balance-dashboard-service/src/test/java/com/bank/balancedashboard/infrastructure/audit/KafkaAuditEventPublisherContractTest.java`

**Test surface (mandatory):**

```java
class KafkaAuditEventPublisherContractTest {

    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
        // matches JSON keys exactly — won't false-positive on the substring 'balance' inside 'balance-inquiry'
        "\"(balance|accountId|accountNumber|accounts|balanceAsOf|currency)\"\\s*:"
    );

    @Test
    void serialized_success_record_neverContainsForbiddenFieldKeys() throws Exception {
        AuditEventRecord rec = AuditEventRecord.success(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60",
            Channel.MOBILE_BANKING,
            /* cacheHit */ true,
            /* accountCount */ 5
        );
        byte[] avroBytes = AvroJsonEncoder.encode(AvroMapper.toAvro(rec));
        String json = new String(avroBytes, StandardCharsets.UTF_8);

        assertThat(FORBIDDEN_KEY.matcher(json).find())
            .as("Audit event must not carry forbidden field keys per Security C-2 / PDPA §22. Got: %s", json)
            .isFalse();

        // and: positive presence checks
        assertThat(json).contains("\"eventType\":\"BALANCE_INQUIRY\"");
        assertThat(json).contains("\"purpose\":\"balance-inquiry\"");
        assertThat(json).contains("\"cacheHit\":true");
        assertThat(json).contains("\"accountCount\":5");
    }

    @Test
    void serialized_forbidden_record_neverContainsForbiddenFieldKeys() throws Exception {
        AuditEventRecord rec = AuditEventRecord.forbidden(
            UUID.randomUUID(), "trace-1", Channel.MOBILE_BANKING);
        byte[] avroBytes = AvroJsonEncoder.encode(AvroMapper.toAvro(rec));
        String json = new String(avroBytes, StandardCharsets.UTF_8);
        assertThat(FORBIDDEN_KEY.matcher(json).find()).isFalse();
    }

    @Test
    void serialized_error_record_neverContainsForbiddenFieldKeys() throws Exception {
        AuditEventRecord rec = AuditEventRecord.error(
            UUID.randomUUID(), "trace-2", Channel.API);
        byte[] avroBytes = AvroJsonEncoder.encode(AvroMapper.toAvro(rec));
        String json = new String(avroBytes, StandardCharsets.UTF_8);
        assertThat(FORBIDDEN_KEY.matcher(json).find()).isFalse();
    }

    @Test
    void recordType_signature_doesNotExposeForbiddenComponentNames() {
        Set<String> componentNames = Arrays.stream(AuditEventRecord.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
        assertThat(componentNames)
            .doesNotContain("balance", "accountId", "accountNumber", "accounts", "balanceAsOf", "currency");
    }
}
```

**Why three serialization tests (SUCCESS/FORBIDDEN/ERROR):** each `AuditEventRecord` factory method has a slightly different field-population profile; a future PR that adds a forbidden field via the SUCCESS factory only would otherwise slip past a single-test contract.

**Why the regex `"balance"\s*:` and not `contains("balance")`:** the legitimate `purpose` value `"balance-inquiry"` contains the substring `balance`. Matching only `"balance":` as a JSON key avoids the false positive.

**Why also the reflection-based component-name test:** the byte-grep covers serialized output; the reflection test fails at compile time on a refactor that adds a forbidden component to the record — earlier feedback in the inner dev loop.

---

## §arch-unit — referenced from ADR-006 §2.4

ArchUnit setup in `pom.xml` (testScope):

```xml
<dependency>
  <groupId>com.tngtech.archunit</groupId>
  <artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version>
  <scope>test</scope>
</dependency>
```

Already on the monorepo parent — no new dependency burden.

---

*implementation-notes · banking-tech-lead · 2026-05-21 · TL-001*
