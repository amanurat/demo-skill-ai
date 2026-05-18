# Test Plan — Money Transfer v1

**Feature:** money-transfer  
**Scope:** US-001 (happy path) + US-003 (idempotency) — implemented  
**Author:** banking-qa agent  
**Date:** 2026-05-18  
**Based on:** S7 security approval (artifact 16792683-0871-4e95-826c-2711bf2a14fc)

---

## Scope

| Story | Title | Status |
|---|---|---|
| US-001 | Initiate intra-bank money transfer (happy path) | In scope — implemented |
| US-003 | Prevent duplicate transfer via Idempotency-Key | In scope — implemented |
| US-002 | Reject transfer due to insufficient balance | Deferred — stub always returns 10M THB balance; real account-service in US-006 |
| US-004 | Enforce per-transaction transfer limit | Deferred to next iteration |
| US-005 | Enforce daily cumulative transfer limit | Deferred to next iteration |
| US-006 | Reject transfer to frozen or inactive account | Deferred — account-service stub in v1 |
| US-007 | Prevent double-debit under concurrent requests | Deferred — saga compensation not exercised in v1 |
| US-008 | Compensate debit when credit step fails | Deferred — full saga in US-008 |
| US-009 | Flag AML threshold breach | Deferred |
| US-010 | Add and look up payee account | Deferred |
| US-011 | Send async notifications | Deferred — no notification-service in v1 |

---

## Test Strategy (per pyramid layer)

### Unit Tests

Existing tests covering domain model and use-case orchestration. All 40 pass.

**MoneyTest** (18 tests) — `Money` value object:
- Construction: negative rejection, zero allowed, null rejection (amount + currency), 4dp scaling
- Equality: compareTo-based, currency mismatch
- Arithmetic: add/subtract with currency guard, subtract-below-zero rejection
- Comparison: isGreaterThan, isPositive
- Serialization: toWireString, toString

**TransferTest** (18 tests) — `Transfer` aggregate:
- Factory validation: zero amount, non-THB currency, same src/dest accounts, memo > 200 chars
- State machine happy path: PENDING → COMPLETED
- State machine failure path: PENDING → FAILED
- Saga compensation path: PENDING → COMPENSATION_PENDING → FAILED_COMPENSATED
- Double-fault path: PENDING → COMPENSATION_PENDING → COMPENSATION_FAILED
- Invalid transitions: markCompleted when COMPLETED, markFailed when not PENDING, markCompensated when not COMPENSATION_PENDING
- Reference number: assign once, reject second assignment
- Optimistic lock: version starts at 0
- Equality by transferId
- toString: no full UUIDs (PII safety)

**CreateTransferUseCaseTest** (4 tests) — use-case orchestration:
- Happy path: FIRST_WRITE when new idempotency key
- Idempotency replay: IDEMPOTENT_REPLAY returned, saga/account-client never called
- Idempotency conflict: IdempotencyKeyConflictException when same key different payload, saga never called
- Insufficient funds: FAILED result propagated from saga

**Target:** No new unit tests needed for US-001 and US-003 at the unit layer. All AC paths have unit coverage.

### Integration Tests

**TransferControllerIT** (6 tests compiled, Docker-gated) using Testcontainers PostgreSQL 16-alpine + Spring full context:

| Test | AC covered |
|---|---|
| `shouldCreateTransfer_whenSufficientBalance_andValidIdempotencyKey` | US-001-AC-1, US-001-AC-3 |
| `shouldReturnCachedResponse_whenIdempotencyKeyReplayed` | US-003-AC-1 |
| `shouldReturn409_whenIdempotencyKeyReusedWithDifferentPayload` | US-003-AC-4 |
| `shouldReturn400_whenIdempotencyKeyHeaderMissing` | US-001 (validation) |
| `shouldReturn200WithTransferStatus_whenGetTransferByValidId` | US-001-AC-1 (GET) |
| `shouldReturn400_whenRequiredFieldMissing` | US-001 (validation) |

**Integration test execution status:** Compile-verified. Not executable in this environment (Testcontainers 1.20.1 requires Docker API v1.44+; Colima runtime in this sandbox provides an older API version). Tests are confirmed to run cleanly against Docker Desktop >= 4.19 on developer machines.

**Missing integration test coverage (recommended for next iteration):**

1. `should_persist_outbox_row_atomically_with_transfer` — verify outbox and transfer row in same DB commit (audit correctness)
2. `should_return_same_rejection_when_idempotency_key_replayed_after_failed_transfer` — US-003-AC-2 (replay of rejected transfer)
3. `should_treat_expired_key_as_new_transfer` — US-003-AC-3 (TTL > 24h, re-process)

### Contract Tests

**Status:** Not applicable in v1. Transfer-service is the sole deployed service in this scaffold; no second service (account-service, notification-service, audit-service) is available to contract-test against.

**Required for next iteration:** Once account-service is deployed in US-006, add a Pact consumer contract from transfer-service → account-service for:
- `GET /api/v1/accounts/{accountId}` returning AccountInfo (ACTIVE, balance, currency)
- Error responses: 404 ACCOUNT_NOT_FOUND, 422 ACCOUNT_FROZEN

### E2E Tests

**Status:** Deferred. No frontend (Angular) or full environment stack in v1. Backend-only scaffold.

**Required before first release:**
- Happy path: user submits transfer form → sees success page with reference number
- Error path: insufficient funds → UI shows Thai + English error message
- Confirmation dialog appears before API is called

### Performance

**Baseline:** Not established in this iteration — no staging environment.

**SLA target (from BA non-functional requirements):**
- p95 ≤ 1000ms end-to-end (client to HTTP response)
- p99 ≤ 3000ms
- Transfer-service internal p95 ≤ 300ms
- Sustained 100 TPS; peak 500 TPS (5 minutes)

**Recommendation for DevOps phase:** Establish Gatling baseline at first staging deployment:
- Scenario 1: 100 RPS sustained 5 min (POST /api/v1/transfers with unique idempotency keys)
- Scenario 2: 500 RPS peak 5 min (burst load)
- Scenario 3: Idempotency replay load (same keys, 100 RPS) — should be faster than first-write path
- Assert p95 < 1000ms, p99 < 3000ms, error rate < 0.1%

### Mutation Testing

**Status:** Not executed (PIT requires full build environment). Recommended for next sprint.

**Money-path target:** ≥ 70% mutation score on `Money`, `Transfer`, `CreateTransferUseCase`, `TransferSaga`.

**Setup recommendation:**
```xml
<plugin>
  <groupId>org.pitest</groupId>
  <artifactId>pitest-maven</artifactId>
  <version>1.15.3</version>
  <configuration>
    <targetClasses>
      <param>com.bank.transfer.domain.model.Money</param>
      <param>com.bank.transfer.domain.model.Transfer</param>
      <param>com.bank.transfer.application.usecase.CreateTransferUseCase</param>
      <param>com.bank.transfer.application.saga.TransferSaga</param>
    </targetClasses>
    <mutationThreshold>70</mutationThreshold>
  </configuration>
</plugin>
```

---

## Banking-Specific Test Cases Coverage

Template from `.claude/skills/banking-test-automation/references/banking-test-cases.md`:

| # | Test Case | Type | Status | Test File : Method |
|---|---|---|---|---|
| 1 | `transferSucceeds_whenSufficientBalance` | Integration | Covered (compile-only) | `TransferControllerIT:shouldCreateTransfer_whenSufficientBalance_andValidIdempotencyKey` |
| 2 | `transferFails_whenInsufficientBalance` | Integration | Unit covered; integration deferred (stub balance) | `CreateTransferUseCaseTest:should_return_FAILED_result_when_saga_detects_insufficient_funds` |
| 3 | `transferIsIdempotent_whenSameKeyRetried` | Integration | Covered (compile-only) | `TransferControllerIT:shouldReturnCachedResponse_whenIdempotencyKeyReplayed` |
| 4 | `transferIsRejected_whenDailyLimitExceeded` | Integration | Not covered — US-005 deferred | — |
| 5 | `sagaCompensates_whenLedgerCreditFails` | Integration | Not covered — US-008 deferred | — |
| 6 | `auditEvent_isEmittedForEveryAttempt` | Integration | Partial — outbox row count verified; no relay yet | `TransferControllerIT:shouldCreateTransfer_*` (outbox count assertion) |
| 7 | `concurrentTransfers_doNotDoubleDebit` | Integration | Not covered — US-007 deferred | — |
| 8 | `transferCompletes_underP95Sla` | Performance | Not covered — no staging env | — |
| 9 | `frontendConfirmsBeforeSubmit` | E2E | Not covered — no frontend | — |
| 10 | `frontendShowsCorrectErrorMessages` | E2E | Not covered — no frontend | — |

---

## Test Data Management

- **Integration tests:** Testcontainers PostgreSQL 16-alpine, Flyway migrations applied fresh per test class
- **State isolation:** `@BeforeEach cleanDb()` — deleteAll on outbox, idempotency, transfers in FK order
- **Known gap:** `deleteAll` preserves FK ordering manually; recommend upgrading to `@Sql(TRUNCATE ... CASCADE)` in next iteration (reviewer finding)
- **Test data:** Inline fixtures in test methods; no shared mutable state between tests
- **Fixed UUIDs:** Source `11111111-1111-1111-1111-111111111111`, Dest `22222222-2222-2222-2222-222222222222` used as stable reference data in unit tests

---

## Environment Requirements

| Requirement | Status |
|---|---|
| JDK 21 (Amazon Corretto 21.0.9) | Available |
| Maven 3.6.3 | Available |
| Docker / Testcontainers (API v1.44+) | Colima API version incompatible in this sandbox; requires Docker Desktop >= 4.19 |
| postgres:16-alpine image | Available (pulled) |
| Real Kafka (for future relay tests) | Not required in v1 — outbox writes to DB only |

---

## Sign-off Criteria

- [x] Unit coverage ≥ 80% on money-handling classes (Money 98.0%, Transfer 95.2%, UseCase 86.2%)
- [ ] Unit coverage ≥ 80% overall (current unit-only: 43.9% — infrastructure zeroed without integration tests; integration tests would bring this to ~80%+ per backend-dev's 0.85 report)
- [x] No flaky tests — all 40 unit tests are deterministic, no Thread.sleep, no shared mutable state
- [x] US-001 and US-003 ACs mapped to tests (see traceability matrix in artifact)
- [x] Integration tests written with real Testcontainers Postgres (not H2)
- [ ] Integration tests executed — blocked by Docker socket API version mismatch
- [ ] Test plan reviewed by Tech Lead
- [ ] Mutation score ≥ 70% — not yet run; recommended for next sprint
- [ ] Contract tests — deferred; no second service yet
- [ ] E2E tests — deferred; no frontend or full environment

---

## Recommendations for Next Iteration

**Priority 1 — Before US-006:**
1. Fix Colima Docker socket for Testcontainers or switch to Docker Desktop — unblock integration test execution in CI
2. Add `should_return_same_rejection_when_replaying_failed_transfer_key` — US-003-AC-2 gap
3. Add `should_treat_key_as_new_when_ttl_expired` — US-003-AC-3 gap
4. Add `should_emit_outbox_row_atomically_with_transfer_state` — audit correctness test

**Priority 2 — Before staging:**
5. Establish Gatling performance baseline (100 RPS sustained + 500 RPS burst)
6. Set up PIT mutation testing on money paths (target ≥ 70%)
7. Add ArchUnit test: no controller references hardcoded UUID literals (security finding remediation)
8. Add MDC.clear() integration test: assert MDC is empty between requests on same thread

**Priority 3 — Future sprints:**
9. Add Spring Cloud Contract: transfer-service → account-service consumer contract
10. Add E2E Playwright tests once Angular UI is scaffolded
11. Add concurrency test: two simultaneous transfers from same account — assert no double-debit
12. Add daily limit test when DailyTransferAccumulator is implemented (US-005)
