# Developer Guide — Balance Comparison Dashboard

**Feature:** `balance-comparison`
**Branch:** `stage/02-balance-comparison`
**Service:** `balance-dashboard-service` (BDS)

This guide is written for a developer new to this feature. Read it front-to-back once, then jump to the IDE.

---

## 1. Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| Java | 21 (LTS) | `JAVA_HOME` must point to JDK 21; project uses `java.util.concurrent.StructuredTaskScope` not available in 17 |
| Maven | Wrapper (`./mvnw`) | Do not use system Maven; use the wrapper to ensure consistent plugin versions |
| Node.js | 20 (LTS) | Angular CLI + Style Dictionary build |
| npm | 10+ | Bundled with Node 20 |
| Docker | 24+ | Testcontainers requires a running Docker daemon |
| Redis | 7.x | Local infra via Docker |
| Kafka | 3.x (Confluent or Apache) | Local infra via Docker |
| `helm` | 3.x | Only needed if you are working on DevOps/Helm chart changes |

---

## 2. Running Locally

### 2.1 Start infrastructure dependencies

The repository does not ship a `docker-compose.yml`. You must start Redis and Kafka manually before running the service or tests. A minimal example:

```bash
# Redis (with TLS disabled for local dev — see note below)
docker run -d --name bds-redis -p 6379:6379 redis:7-alpine

# Kafka (with Zookeeper for simplicity)
docker run -d --name bds-zookeeper -e ALLOW_ANONYMOUS_LOGIN=yes -p 2181:2181 bitnami/zookeeper:latest
docker run -d --name bds-kafka \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=host.docker.internal:2181 \
  -e ALLOW_PLAINTEXT_LISTENER=yes \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -p 9092:9092 bitnami/kafka:latest
```

**Redis TLS note:** In staging and production, `REDIS_SSL_ENABLED=true` is required (Security C-4). For local development with the `dev` profile, you can run Redis without TLS by setting `REDIS_SSL_ENABLED=false` in your environment or `application-dev.yml`. Never disable TLS in staging or production.

### 2.2 Enable the feature flag

The feature is disabled by default in all profiles (`balance-dashboard.enabled=false`). You must set the flag to `true` in your local environment, otherwise the endpoint returns `501 Not Implemented`.

```bash
export BALANCE_DASHBOARD_ENABLED=true
```

Or set it in your local `application-dev.yml`:

```yaml
balance-dashboard:
  enabled: true
```

### 2.3 Run the backend service

```bash
./mvnw spring-boot:run \
  -pl backend/balance-dashboard-service \
  -Pdev \
  -Dspring-boot.run.jvmArguments="-DBALANCE_DASHBOARD_ENABLED=true \
    -DREDIS_HOST=localhost \
    -DREDIS_PORT=6379 \
    -DKAFKA_BOOTSTRAP_SERVERS=localhost:9092"
```

The service starts on port `8080` (HTTP) and `8081` (actuator). Health check: `http://localhost:8081/actuator/health`.

**Note on DataSource autoconfiguration:** BDS deliberately excludes `DataSourceAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`, and `HibernateJpaAutoConfiguration` — it has no RDBMS. Do not add a `spring.datasource.*` property; it will cause context startup to fail.

### 2.4 Run the Angular frontend

```bash
cd frontend
npm install
ng serve
```

The dev server starts on `http://localhost:4200` and proxies API calls to `http://localhost:8080` (configured in `proxy.conf.json`).

---

## 3. Key Environment Variables

These variables are consumed by the service at startup. In Kubernetes, they are injected from Helm `values.yaml` and Vault secrets.

| Variable | Default | Description |
|---|---|---|
| `BALANCE_DASHBOARD_ENABLED` | `false` | Feature flag. Must be `true` for the endpoint to be active. Disabled → `501`. |
| `REDIS_HOST` | (required) | Redis hostname or IP. Example: `redis.infra.svc.cluster.local` |
| `REDIS_PORT` | `6379` | Redis port. |
| `REDIS_SSL_ENABLED` | `true` | **Must be `true` in staging and production** (Security C-4 / in-transit encryption). Can be `false` for local dev only. |
| `REDIS_PASSWORD` | (required) | Redis AUTH password. Injected from Vault in cluster environments. |
| `KAFKA_BOOTSTRAP_SERVERS` | (required) | Kafka bootstrap server list. Example: `kafka.infra.svc.cluster.local:9092` |
| `APICURIO_REGISTRY_URL` | (required) | Apicurio Schema Registry URL. Required for Avro serialization of audit events. Example: `http://apicurio.infra.svc.cluster.local:8080/apis/registry/v2` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | (required) | JWT issuer URI for token validation. Must match the identity-service OIDC discovery endpoint. Example: `https://identity.bank.local/realms/banking` |

**Tip:** For local development, the simplest approach is to create a `.env` file in `backend/balance-dashboard-service/` and use the IDE run configuration to load it, or set variables in `application-dev.yml` using `${VARIABLE_NAME:default_value}`.

---

## 4. Running Tests

### 4.1 All tests (unit + integration)

```bash
./mvnw test -pl backend/balance-dashboard-service
```

This runs both unit tests (`src/test/java/.../unit/`) and integration tests (`src/test/java/.../integration/`) via `@SpringBootTest` with Testcontainers (spins up Redis and Kafka containers). Ensure Docker is running before executing integration tests.

### 4.2 Full verify with JaCoCo coverage gate

```bash
./mvnw verify -pl backend/balance-dashboard-service
```

The build fails if unit + integration coverage falls below 80% (JaCoCo configured in `pom.xml`). Critical financial paths require 95% coverage.

### 4.3 ArchUnit structural enforcement test

```bash
./mvnw test -pl backend/balance-dashboard-service \
  -Dtest=CustomerIdSourceRule
```

This runs the ArchUnit rule that enforces ADR-006: only `IborCheckFilter` may call `request.getHeader("X-Customer-Id")`. A violation in the codebase fails this test at build time. Run this after any refactoring that touches the controller or filter layers.

### 4.4 Angular unit tests

```bash
cd frontend
ng test
```

Runs Karma + Jasmine tests for Angular components, services, and pipes. Includes tests for balance display, staleness rendering, and error state components.

### 4.5 Audit contract test (Security C-2 enforcement)

```bash
./mvnw test -pl backend/balance-dashboard-service \
  -Dtest=KafkaAuditEventPublisherContractTest
```

Byte-greps the Avro-serialized audit event payload to assert that no forbidden field names (`balance`, `accountId`, `accountNumber`, `accounts`, `balanceAsOf`, `currency`) appear in the output. Fails if anyone adds a forbidden field to `AuditEventRecord`.

---

## 5. Design Tokens

Design tokens are the single source of truth for all visual decisions (colors, spacing, typography, motion). The workflow is:

1. **Source of truth:** `docs/design/_shared/tokens.json` — hand-edited by `banking-designer` only. Never hand-edit the generated files.
2. **Generated outputs:** `frontend/src/styles/tokens.scss` and `frontend/src/styles/tokens.css` — auto-generated from `tokens.json` by Style Dictionary (ADR-005).
3. **Regenerate after a `tokens.json` change:**

```bash
node tools/design-tokens/build.js
# or via npm script:
cd frontend && npm run tokens:build
```

4. **CI enforcement:** The CI job `design-tokens-up-to-date` runs `git diff --exit-code` on the generated files. If you change `tokens.json` without regenerating, the PR fails.

5. **Import order in `frontend/src/styles.scss`** (locked by ADR-005 §2.4 — do not reorder):
   ```scss
   @import 'styles/tokens';        // SCSS compile-time $vars
   @import 'styles/tokens.css';    // Runtime :root --custom-properties
   @import 'styles/reset';
   @import 'styles/typography-base';
   @import 'styles/utilities';
   ```

6. **Component styling rule:** Use `var(--*)` CSS custom properties in component styles (not `$*` SCSS variables), except for `@media` breakpoints where CSS custom properties cannot be used.

**Config file:** `tools/design-tokens/style-dictionary.config.cjs`

---

## 6. Security Constraints

The following security constraints from the security review (G8) are non-negotiable. Violating them during development will fail the security gate.

### C-2 — Audit event is metadata-only (PDPA §22 data minimization)

`AuditEventRecord` must never carry balance values, account IDs, account numbers, or per-account timestamps. The 7-year retained audit log must not become a balance history store. The contract test `KafkaAuditEventPublisherContractTest` enforces this automatically, but do not attempt to add financial data fields to the record even if a test passes.

### C-3 — `customerId` derived from JWT only (IDOR defense / ADR-006)

The controller must derive `customerId` via `customerIdResolver.resolve(jwt)`. Never use `@RequestHeader("X-Customer-Id")`, `@RequestParam("customerId")`, or `@PathVariable("customerId")` as the source of `customerId`. The ArchUnit rule `CustomerIdSourceRule` enforces this at build time.

### C-4 — Redis TLS in transit required

`spring.data.redis.ssl.enabled` must be `true` in staging and production environments. Local dev may disable this for convenience, but never commit a configuration that disables TLS in non-dev profiles.

### Logging — No PII in log output

All log statements that bind `customerId`, `accountId`, or Redis key prefixes must wrap the value in `LogMasking.maskId(...)` or `LogMasking.maskKey(...)`. A `logback-spring.xml` regex masking filter provides a secondary safety net, but the primary defense is at the call site. Raw bindings like `log.warn("...", customerId)` are a CWE-532 violation.

---

## 7. Troubleshooting

### Endpoint returns 501 Not Implemented

The feature flag is disabled. Set `BALANCE_DASHBOARD_ENABLED=true` (environment variable or `application-dev.yml`). The `501` response is intentional — it distinguishes "feature disabled" from "path not found".

### Context startup fails: `Failed to determine a suitable driver class`

A Spring datasource autoconfiguration is being included transitively. Verify that `BalanceDashboardServiceApplication` has all three autoconfiguration exclusions:
- `DataSourceAutoConfiguration`
- `DataSourceTransactionManagerAutoConfiguration`
- `HibernateJpaAutoConfiguration`

BDS has no RDBMS. Do not add any JDBC or JPA dependency.

### Redis TLS handshake error in local dev

Set `REDIS_SSL_ENABLED=false` in your local environment or `application-dev.yml`. Only disable TLS in local dev, never in staging or production.

### Audit events not appearing in Kafka

1. Verify `KAFKA_BOOTSTRAP_SERVERS` points to a reachable broker.
2. Verify `APICURIO_REGISTRY_URL` is reachable and the Avro v2 schema `com.bank.compliance.audit.v2.AuditEventRecorded` is registered. If the schema is not registered, the Avro serializer throws at startup.
3. Check `audit_events_total{result=FAILED}` metric — if incrementing, a Kafka send is failing silently (fire-and-forget contract means the service still returns 200 to the user).

### JWT scope mismatch — always 403

Verify the scope name registered in `identity-service` matches `accounts:read` exactly (ASSUMPTION-TL-002). Spring evaluates `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` — the `SCOPE_` prefix is added by Spring Security automatically when parsing JWT scopes. If the registered scope is different (e.g., `account:read`), adjust the `@PreAuthorize` expression and notify SA.

### Integration tests fail: cannot connect to Testcontainers

Ensure Docker is running (`docker info`). Testcontainers requires a running Docker daemon to spin up Redis and Kafka containers. On macOS with Colima, ensure Colima is started (`colima start`).

### `design-tokens-up-to-date` CI job fails

Run `node tools/design-tokens/build.js` (or `npm run tokens:build` from the `frontend/` directory) and commit the updated `tokens.scss` and `tokens.css` files. Never hand-edit the generated files — the CI will catch it.

---

*Developer Guide · balance-comparison · banking-docs · 2026-05-22*
