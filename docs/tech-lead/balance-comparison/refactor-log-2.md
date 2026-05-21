# Refactor Log — balance-comparison · Iteration 2

## Findings Addressed

| Finding ID | Severity | File | Change made |
|---|---|---|---|
| R-BE-202 | blocker | `backend/balance-dashboard-service/pom.xml` | Added `apicurio-registry-serdes-avro-serde:2.5.8.Final` + `org.apache.avro:avro:1.11.3` dependencies so that `AvroKafkaSerializer` referenced in `application.yml` is on the classpath at Spring context startup |
| R-BE-203 | blocker | `...unit/BalanceDashboardControllerTest.java` | Added `@TestPropertySource(properties = "balance-dashboard.enabled=true")` to the test class so that `BalanceDashboardController` (gated by `@ConditionalOnProperty`) is registered in the `@WebMvcTest` context; all 11 test cases now exercise the live controller instead of the 501 fallback |
| R-BE-204 | blocker | `...integration/CacheHitAuditEmittedTest.java` | Rewrote test `(1)` into a two-request scenario: Request 1 asserts `X-Cache: MISS` + audit with `cacheHit=false`; Request 2 asserts `X-Cache: HIT` + audit with `cacheHit=true`; verifies `AuditEventPublisher.publish()` called exactly twice and `AccountPort.fetchAccounts()` called only once (BR-014 compliance) |
| R-BE-205 | major | `...application/service/BalanceDashboardService.java` + `...infrastructure/rest/IborCheckFilter.java` | Reinforced comment in `BalanceDashboardService.loadDashboard()` to make explicit that `correlationId` is derived once at method entry and flows to all three audit paths (success/error). Fixed `IborCheckFilter.doFilterInternal()` to derive `correlationId` from `Span.current().getSpanContext().getTraceId()` (OTel trace ID, falling back to `UUID.randomUUID()` when no active span) — previously it always used a random UUID, making the FORBIDDEN audit record unresolvable in Tempo/Jaeger |

## Findings Deferred

(none — all 4 assigned findings resolved)

## Tests Added / Updated

- `BalanceDashboardControllerTest.java` — added `@TestPropertySource` + import; all 11 test cases now run against the enabled controller
- `CacheHitAuditEmittedTest.java` — rewritten two-request cache scenario with cache-HIT assertion and dual audit-emit verification
