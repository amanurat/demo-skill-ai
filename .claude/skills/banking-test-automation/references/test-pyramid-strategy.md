# Banking Test Pyramid — Strategy & Gates

Reference loaded on demand by the `banking-test-automation` skill. Defines the shape of the test suite for a banking feature, what belongs at each layer, and the gates each layer must pass.

## The Pyramid (visual)

```
       /\
      /E2E\        ← 8 tests (happy paths + 1-2 critical errors)
     /------\
    /Contract\     ← One per consumer/provider pair
   /----------\
  /Integration \   ← Per endpoint + Saga happy/sad paths
 /--------------\
/    Unit        \ ← ≥ 80% overall, ≥ 95% money paths
------------------
```

The shape matters: many fast unit tests, fewer integration tests, very few E2E. Inverting this (the "ice-cream cone") produces slow, flaky pipelines.

---

## Layer 1 — Unit Tests (base, widest)

**Scope:** pure functions, domain entities, value objects, mappers, single-class services with collaborators mocked.

**Tools:** JUnit 5 + Mockito + AssertJ (backend); Jest (frontend).

**What belongs here:**
- `Account.debit(amount)` enforces non-negative balance
- `Money.add(Money)` rejects currency mismatch
- `TransferRequestValidator.validate()` covers every branch
- MapStruct mapper round-trips DTO ↔ entity correctly
- Frontend pure components / pipes / reducers

**Coverage gate:** ≥ 80% overall, **≥ 95%** for any class touching money. Branch coverage: every error path has at least one test.

**Rules:**
- No Spring context (`@SpringBootTest` is overkill here)
- No DB, no Kafka, no HTTP
- < 100ms per test
- Mock only collaborators — never the class under test
- One behavior per test; name it `should_X_when_Y`

**Example:**
```java
@Test
void should_reject_debit_when_balance_insufficient() {
  var account = AccountBuilder.with(balance("100.00")).build();
  assertThatThrownBy(() -> account.debit(money("150.00")))
    .isInstanceOf(InsufficientFundsException.class);
}
```

---

## Layer 2 — Integration Tests

**Scope:** one service end-to-end inside its own boundary — HTTP in, DB out, Kafka out, all real.

**Tools:** Spring Boot Test + **Testcontainers** (real Postgres + real Kafka). No H2. No embedded Kafka.

**What belongs here:**
- Every API endpoint (`POST /api/v1/transfers` → 201 + row in DB + event on Kafka)
- Saga happy path (all steps complete)
- Saga sad path (compensation fires when step N fails)
- Idempotency-Key replay returns the cached result
- Flyway migrations apply cleanly on a fresh container
- Audit event emitted for every state-changing op

**Coverage gate:** every endpoint covered; every saga path (happy + each compensation) covered.

**Rules:**
- Real Postgres via Testcontainers — H2 hides type / SQL-dialect bugs
- Real Kafka via Testcontainers — embedded Kafka diverges on partition / consumer-group semantics
- Use Awaitility for async assertions (no `Thread.sleep`)
- Transactional rollback or `@DirtiesContext` between tests
- One scenario per test

**Example skeleton:**
```java
@SpringBootTest
@Testcontainers
class TransferIntegrationTest {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
  @Container static KafkaContainer kafka = new KafkaContainer(...);
  // ... test methods
}
```

---

## Layer 3 — Contract Tests

**Scope:** the wire format between a consumer and a provider service.

**Tools:** Spring Cloud Contract or Pact.

**What belongs here:**
- One contract per consumer-provider pair (e.g., `transfer-service` → `ledger-service`)
- Producer side: verify the provider honors the published contract
- Consumer side: stub the provider from the contract in consumer tests

**Coverage gate:** every cross-service boundary has a contract.

**Why:** catches breaking changes at build time, not in staging.

---

## Layer 4 — End-to-End (E2E) Tests

**Scope:** browser → all services → DB, in a deployed-like environment.

**Tools:** Cypress or Playwright.

**What belongs here (target: ~8 tests total per feature):**
- Happy path: user submits a transfer, sees success, balance updates
- 1–2 critical error UX paths (insufficient balance, daily limit hit)
- Confirmation dialog appears before submit
- Correct error messages render

**Coverage gate:** happy path automated for every user-facing feature.

**Rules:**
- Keep the count small — these are slow and flaky-prone
- Run against a deployed stack (staging or ephemeral env), not local mocks
- No business logic assertions here — that belongs in unit/integration

---

## Layer 5 — Performance Tests

**Scope:** sustained load against the running stack.

**Tools:** Gatling or k6 (kept under `infra/`).

**SLA gates (per feature, unless overridden):**
- **p95 ≤ feature SLA** (e.g., 500ms for a transfer)
- **p99 ≤ 2× p95**
- **Throughput:** meet target RPS without degradation
- **Error rate < 0.1%**

**When to run:**
- Baseline established before first release
- Re-run nightly against staging
- Re-run on any change to a money-handling code path

---

## Coverage Rules (recap)

| Rule | Threshold |
|---|---|
| Overall line coverage | ≥ 80% |
| Money-handling code | ≥ 95% |
| Mutation score (PIT) on money paths | ≥ 70% |
| Branches | every error path has ≥ 1 test |

If you cannot meet a gate after honest effort, **add tests** — do not lower the gate. If the code is genuinely untestable, the design is wrong; loop back to dev.
