# Refactor Log — balance-comparison · Iteration 1

> **Agent:** `banking-refactoring`
> **Date:** 2026-05-21
> **Branch:** `stage/02-balance-comparison`
> **Sprint:** SPRINT-2026-Q2-BC-01

---

## Findings Addressed

| Finding ID | Severity | File(s) | Change made |
|---|---|---|---|
| R-BE-001 | blocker | `application-staging.yml` | Removed `ssl.enabled: false` override; set `ssl.enabled: true` with comment to escalate to DevOps if cluster lacks TLS |
| R-BE-002/003/004 | blocker | `domain/exception/DashboardUnavailableException.java` (new), `application/port/out/AccountPort.java`, `application/service/BalanceDashboardService.java`, `infrastructure/client/AccountClientAdapter.java`, `infrastructure/rest/ProblemDetailAdvice.java`, `domain/port/in/LoadDashboardUseCase.java` | Created `DashboardUnavailableException` in domain layer; rewired all layers to use domain exception; infra adapter wraps `UpstreamUnavailableException` before propagating; removed all infra imports from domain and application layers |
| R-BE-005/006 | major | `unit/BalanceDashboardControllerTest.java` (new) | Created `@WebMvcTest` suite with all 11 mandated test cases including critical `balanceIsString_notNumber` assertion |
| R-BE-007 | major | `infrastructure/rest/BalanceDashboardResponse.java` | Moved `@JsonSerialize(using = ToStringSerializer.class)` from private field to getter `getBalance()` |
| R-BE-008 | major | `infrastructure/rest/ProblemDetailAdvice.java` | Split `RuntimeException` catch-all: `DashboardUnavailableException` → 503 + Retry-After: 1; unknown `RuntimeException` → 500 (no Retry-After), log at ERROR |
| R-BE-009/010 | major | `integration/CacheHitAuditEmittedTest.java`, `integration/UpstreamFailureReturns503Test.java` | Added `@Testcontainers` annotation, `static RedisContainer redis` and `static ConfluentKafkaContainer kafka` fields, `@DynamicPropertySource` to wire real infra |
| R-BE-011 | major | `arch/CustomerIdSourceRule.java`, `arch/violations/FakeViolatingService.java` (new) | Added `violationIsDetected()` test that imports `FakeViolatingService` and asserts `assertThatThrownBy(rule.check(classes)).isInstanceOf(AssertionError.class)` |
| R-BE-012 | major | `infrastructure/audit/KafkaAuditEventPublisher.java`, `application.yml` | Changed `KafkaTemplate<String, String>` to `KafkaTemplate<String, AuditEventRecorded>`; removed `ObjectMapper` injection; updated `application.yml` value-serializer to `io.apicurio.registry.serde.avro.AvroKafkaSerializer` |
| R-BE-013 | major | `application/service/BalanceDashboardService.java` | Added OTel `Span.current().getSpanContext()` trace ID as `correlationId`; falls back to `UUID.randomUUID()` only when no active span |
| R-BE-014 | major | `domain/audit/AuditEventRecord.java` | Added compact constructor with `Objects.requireNonNull(actorId)` and blank check on `correlationId` |
| R-FE-001/002 | blocker | `empty-state.component.html`, `error-retry-banner.component.html`, `empty-state.component.ts`, `error-retry-banner.component.ts` | Replaced `onerror="..."` inline handlers with Angular `(error)="onImgError($event)"` event binding; added `onImgError()` method to both component classes |
| R-FE-003 | blocker | `services/auth.service.ts` (new), `guards/auth.guard.ts`, `guards/auth.guard.spec.ts` | Created `AuthService` with Signal-backed state (sessionStorage only); updated guard to inject `AuthService.isAuthenticatedSnapshot()` instead of reading `localStorage`; removed localStorage test case from spec |
| R-FE-004 | major | `pipes/balance-amount.pipe.ts`, `pipes/pipes.spec.ts` | Replaced `parseFloat()` with `Number()`; extracted satang by string-splitting on `.` (not float arithmetic); added `amountOnly` field to `BalanceFormatResult`; added precision tests |
| R-FE-005 | major | `services/dashboard.service.ts` | Migrated from deprecated `retryWhen` to `retry({ count: 3, delay: ... })`; explicit 503-only filter; correct exponential backoff |
| R-FE-006 | major | `components/account-row/account-row.component.html`, `pipes/balance-amount.pipe.ts` | Removed `.replace('฿','').replace('THB','')` template chain; pipe now returns `amountOnly` field; template uses `{{ balanceResult.amountOnly }}` |
| R-FE-007 | major | `components/account-row/account-row.component.ts` | Added pipes to `providers` array; injected via `inject()` instead of `new AccountTypeLabelPipe()` / `new BalanceAmountPipe()` |
| R-FE-008 | major | `containers/dashboard-page/dashboard-page.component.ts` | Removed `console.log('[balance-dashboard] row selected:', account.rank)` |
| R-FE-009 | major | `components/loading-skeleton/loading-skeleton.component.html` | Removed `role="listitem"` from `<li aria-hidden="true">` skeleton rows |

---

## Findings Deferred

None — all blockers and majors were addressed.

Minors and nits (R-BE-015 through R-BE-032, R-FE-010 through R-FE-020) are deferred per scope constraint: refactoring agent fixes only what reviewers identified as blocker/major in this iteration.

---

## Tests Added / Updated

### Backend

| File | Status | Notes |
|---|---|---|
| `unit/BalanceDashboardControllerTest.java` | **NEW** | 11 `@WebMvcTest` cases; includes `balanceIsString_notNumber` assertion |
| `arch/CustomerIdSourceRule.java` | Updated | Added `violationIsDetected()` with `FakeViolatingService` fixture |
| `arch/violations/FakeViolatingService.java` | **NEW** | Deliberate-violation fixture; confirms ArchUnit rule fires |
| `integration/CacheHitAuditEmittedTest.java` | Updated | Added `@Testcontainers`, `RedisContainer`, `ConfluentKafkaContainer`, `@DynamicPropertySource` |
| `integration/UpstreamFailureReturns503Test.java` | Updated | Added `@Testcontainers`, real Redis + Kafka containers; switched to `DashboardUnavailableException` |
| `unit/BalanceDashboardServiceTest.java` | Updated | Replaced `UpstreamUnavailableException` with `DashboardUnavailableException` |

### Frontend

| File | Status | Notes |
|---|---|---|
| `services/auth.service.ts` | **NEW** | Signal-based auth state; sessionStorage only |
| `guards/auth.guard.spec.ts` | Updated | Removed localStorage test case; uses `AuthService` |
| `pipes/pipes.spec.ts` | Updated | Added satang precision test and zero-satang test |

---

## Self-Check Results

| Check | Command | Result |
|---|---|---|
| No infra imports in domain/application | `grep -rn "import.*infrastructure" ...domain/ ...application/` | PASS (0 actual imports) |
| SSL enabled in staging | `grep -rn "ssl.enabled: false" ...main/resources/` | PASS (0 matches) |
| No onerror= in FE | `grep -rn "onerror=" .../balance-comparison/` | PASS (0 matches) |
| No localStorage in auth guard | `grep -n 'localStorage\.' auth.guard.ts` | PASS (0 actual calls) |
| No retryWhen in service | `grep -n 'retryWhen(' dashboard.service.ts` | PASS (0 matches) |
| No console.log in page component | `grep -n 'console\.log' dashboard-page.component.ts` | PASS (comment only) |

---

*Refactor Log 1 · banking-refactoring · 2026-05-21 · iteration 1 · balance-comparison*
