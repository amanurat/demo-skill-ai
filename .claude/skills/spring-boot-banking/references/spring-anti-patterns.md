# Spring Boot Banking — Anti-Patterns

Reference loaded on demand by `spring-boot-banking` skill. Catalog of patterns to flag in code review and avoid in implementation.

## Severity Legend
- **blocker** — must fix before merge
- **major** — should fix; OK to merge if tracked with follow-up
- **minor** — improve; can defer
- **nit** — style only

## Domain & Architecture

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| **Anemic Domain Model** | major | Business rules scattered in services; bypassable from any caller | Put rules in entities/value objects (`Account.debit()` enforces invariants) |
| **God Service** (one class doing everything) | major | Hard to reason about, test, deploy, scale | Split by bounded context |
| **Distributed Monolith** (sync chains > 2 services) | blocker | Breaks independent deployment; cascading failures | Async events or consolidate back to one service |
| **Splitting by layer** (`controller-service`, `repo-service`) | blocker | Wrong boundary; couples deploy cycles | Split by domain (bounded context) |
| **Shared database** between services | blocker | Couples schemas, breaks ownership | One DB per service; share via API/events |

## Java / Spring Code

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| `@Autowired` on fields | minor | Hides dependencies; hard to test | Constructor injection (use `@RequiredArgsConstructor`) |
| `@Data` on entities | minor | Mutable + broken `equals`/`hashCode` based on all fields | `@Getter` + `@EqualsAndHashCode(of = "id")` |
| `@Transactional` on controller | minor | Wrong layer; can't compose | Move to `application/usecase/` |
| Catching bare `Exception` | major | Hides real bugs (NPE, IllegalState, etc.) | Catch specific types; let unknowns propagate |
| Swallowing exceptions (`catch { }`) | major | Silent failures | Log + rethrow with context, or handle explicitly |
| Returning JPA entities from controllers | major | Lazy-load surprises during serialization; tight coupling | Map to DTO via MapStruct |
| Hidden side effects in getters | minor | Surprises during serialization / logging | Pure getters only |
| `OneToMany` to giant collection | major | Memory blow-ups on load | Paged repo or remove association |
| Using `String.format` for SQL | blocker | SQL injection | JPA parameterized queries; `@Query` with `:name` |

## Money Handling

| Anti-Pattern | Severity | Why It's Bad |
|---|---|---|
| **Floats / doubles for money** | blocker | Precision loss accumulates; legally wrong |
| `BigDecimal.equals()` vs `compareTo()` confusion | major | `equals` checks scale; `1.0 != 1.00`; use `compareTo` for value |
| Money column as `FLOAT` / `DOUBLE` in DB | blocker | Same precision issue |
| Hardcoded currency in domain | major | Multi-currency expansion painful | Currency as VO/enum |

## Distributed Systems

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| **Direct Kafka send inside `@Transactional`** | major | Message may be lost if tx rolls back | Outbox pattern |
| Missing **Idempotency-Key** on financial POST/PUT | blocker | Retries cause double-debit | Mandatory header; cache result with TTL |
| Sync HTTP chain > 2 services for writes | blocker | Cascading failures, distributed monolith | Async events |
| No saga compensation defined | major | Partial failures leave inconsistent state | Explicit compensating actions per step |
| Idempotent op without retry policy | minor | Wasted transient failures | Resilience4j retry |
| Non-idempotent op WITH retry policy | major | Double-execution risk | Make idempotent first, then retry |

## Persistence

| Anti-Pattern | Severity | Why It's Bad | What to Do Instead |
|---|---|---|---|
| Missing `@Version` on money entities | major | Lost updates under concurrency | Add `version BIGINT` column + `@Version` |
| Editing a released Flyway migration | blocker | Schema drift; deploys break | Add new migration; never edit released |
| `DELETE` on audit / financial tables | blocker | Compliance violation (7-yr retention) | Soft delete or archive only |
| Non-reversible migration without down plan | major | Can't recover from bad deploy | Document down SQL even if not auto-run |
| `@Transactional` on `@RestController` | minor | Tx boundary wrong; can't compose | Move to use-case layer |

## Security

| Anti-Pattern | Severity | Why It's Bad |
|---|---|---|
| **Hardcoded secret** (in code, config, ConfigMap) | blocker | Audit + leak risk |
| **PII / card number / JWT in logs** | blocker | Compliance violation (PCI-DSS, GDPR, PDPA) |
| HS256 JWT (shared secret) in prod | blocker | If config leaks, all tokens forgeable; use RS256 |
| Mass-assignment (binding request body directly to entity) | major | Privilege escalation; field tampering |
| Missing `@PreAuthorize` on sensitive endpoint | major | Broken access control (OWASP A01) |
| Unauthenticated endpoint exposing customer data | blocker | Direct compliance + reputation hit |

## Testing

| Anti-Pattern | Severity | Why It's Bad |
|---|---|---|
| `@MockBean` of class under test | major | Tests don't test the real code |
| Tests asserting nothing (always pass) | major | Green theatre; missed regressions |
| Test names like `test1`, `testTransfer` | minor | No behavior described |
| Sleep-based waits (`Thread.sleep(2000)`) | major | Flaky; slow | Use Awaitility with conditions |
| H2 instead of Testcontainers Postgres | major | Behavior diverges from prod | Real Postgres via Testcontainers |
| Embedded Kafka instead of Testcontainers Kafka | major | Same divergence | Testcontainers Kafka |
| Snapshot tests for everything | minor | Brittle; passes on irrelevant changes | Targeted assertions |
| Disabling tests instead of fixing | major | Coverage rot | Fix or quarantine with explicit ticket |

## Observability

| Anti-Pattern | Severity | Why It's Bad |
|---|---|---|
| String-concat log messages | minor | No structured fields; hard to query | `kv("field", value)` style |
| Unbounded label cardinality in metrics (`customer_id` as label) | major | Prometheus OOM | Bucket or omit |
| Logging full request body on error | major | PII leak risk | Log only safe fields |
| No correlation ID in logs | major | Cannot trace requests across services | MDC + `traceparent` propagation |

## When to Use This Reference

- Reviewing a backend PR — scan for these patterns
- Diagnosing a bug — many bugs trace back to these anti-patterns
- Onboarding a new developer — assigned reading
- Refactoring legacy code — prioritize fixing blockers first
