# S6 — Reviewer — Money Transfer

> Summary of [`S6-reviewer-money-transfer.json`](S6-reviewer-money-transfer.json). The JSON remains the source of truth for envelope validation; this file makes the payload human-scannable.

## Envelope

| Field | Value |
|---|---|
| Artifact ID | `9e2c4a8b-5f31-4d72-b3a1-7e6c0d9f4b21` |
| From | `banking-reviewer` |
| To | `banking-security` |
| Phase | `REVIEW` |
| Feature | `money-transfer` |
| Timestamp | `2026-05-18T03:42:00Z` |
| Verdict | ✅ approved |
| Iteration | 1 |
| Quality Gate | ✅ Passed |
| Previous artifact | `f3a7e2d1-8b4c-4f9e-a6d5-2c1b0e9f8a7d` |

## TL;DR

✅ **Approved** to proceed to security review. Solid v1 scaffold — hexagonal layering is respected, the Transfer aggregate enforces invariants, Money uses BigDecimal NUMERIC(19,4) with compareTo-based equality, idempotency follows ADR-013 (SHA-256 key hash + payload checksum + 24h TTL + 409 on conflict), and outbox writes occur in the same `@Transactional` as the business write. Several majors must be addressed before US-006 lands a real account-service — notably `AccountClientStub` being indistinguishable from a production bean, double DB lookups in the JPA adapter, and missing OpenTelemetry/MDC `traceId` wiring.

## Key Findings

### Severity Distribution

| Severity | Count |
|---|---|
| Blocker | 0 |
| Major | 5 |
| Minor | 13 |
| Nit | 5 |
| **Total** | **23** |

### Comments — Blockers

None ✅

### Comments — Majors (5)

| File:Line | Rule | Message | Suggested Fix |
|---|---|---|---|
| `infrastructure/client/AccountClientStub.java:22` | anti-pattern: silent-stub-in-production | `AccountClientStub` is `@Component` with no profile guard; will be auto-wired in prod, returning a canned 10M THB ACTIVE account. Money-safety hazard the moment SecurityConfig is hardened (US-006). | Add `@Profile("!prod")` or `@ConditionalOnMissingBean(AccountClient.class)` + `@ConditionalOnProperty("transfer.account-client.stub.enabled=true")`. WARN at startup; fail readiness probe if profile=prod and no real adapter present. |
| `infrastructure/persistence/TransferRepositoryAdapter.java:51` | performance: redundant-select-before-save | Every `save()` does `findById(transferId)` first just to preserve `createdAt`. On hot path `createTransfer` does this twice per request (insert PENDING + update FINAL). Two extra SELECTs in the critical-path transaction for a p95<1s SLA endpoint. | Set `createdAt` in domain factory or via `@PrePersist`, use `@CreationTimestamp`, or `EntityManager.merge()` with populated entity. |
| `application/usecase/CreateTransferUseCase.java:107` | observability: missing-otel-spans-and-metrics | No OpenTelemetry `@WithSpan`, no Micrometer `Counter`/`Timer` for `transfer.attempts` / `transfer.completed` / `transfer.failed` / `idempotency.hits`, and no sampler bias. NFR requires Prometheus + OTel. Critical for SLO measurement (p95<1s, p99<3s). | Add `@Observed` or `@WithSpan` on `execute()` and `findById()`; inject `MeterRegistry` and emit `Counter("transfer.requests", Tags.of("result", status.name()))` + `Timer.builder("transfer.duration")`. Wire Sleuth/Micrometer baggage for `correlationId` so `MDC.traceId` is populated. |
| `interfaces/rest/TransferController.java:58` | security: hardcoded-customer-id | `STUB_CUSTOMER_ID` hardcoded UUID is the authenticated principal for every request, bypassing the JWT subject claim entirely. Combined with `SecurityConfig.permitAll`, `@PreAuthorize("isAuthenticated()")` is the only nominal guard. Ownership checks effectively pass for all callers. Acceptable for v1 ONLY because no production endpoint is exposed. | Add `Authentication` parameter, extract `sub` claim via `Jwt.getSubject()`. Throw 401 if absent. Until US-006, gate controller with `@Profile("!prod")` or fail-fast on missing JWT issuer config. |
| `application/usecase/CreateTransferUseCase.java:130` | best-practice: double-call-to-account-client | `AccountClient.getAccountInfo(sourceAccountId)` called once at line 130 (ownership check) and again inside `TransferSaga.execute()` line 69. With Resilience4j this doubles latency budget (2 × 800ms time-limiter) and burns 2 bulkhead slots. Two calls also not atomically consistent. | Fetch once in use case, pass `AccountInfo` into `TransferSaga.execute(transfer, sourceAccountInfo, destinationAccountInfo)`. Or remove use-case ownership call entirely and rely on saga step 1. |

### Comments — Minors (13)

| File:Line | Rule | Message |
|---|---|---|
| `domain/model/Transfer.java:21` | mutable-aggregate-field | `referenceNumber` dropped `final` and is now mutated by `assignReferenceNumber`. Two-phase assignment unnecessary because reference is deterministically derived from UUID. Fix: derive in `Transfer.create()` and revert to `final`. |
| `application/usecase/CreateTransferUseCase.java:218` | jackson-record-order-not-guaranteed-cross-jvm | `canonicalize()` relies on Jackson serializing local record fields in declaration order. Not a JLS guarantee — silent miss-match could allow duplicate transfers. Fix: `@JsonPropertyOrder` or `TreeMap<String,String>`. |
| `application/usecase/CreateTransferUseCase.java:249` | implicit-timezone-in-reference-number | `generateReferenceNumber` uses Asia/Bangkok local date. Transfer at 23:59 BKK could show YYYYMMDD disagreeing with UTC `createdAt`. Fix: document in OpenAPI or render in UTC; add unit test with `java.time.Clock` injection. |
| `infrastructure/persistence/TransferJpaEntity.java:32` | lombok-data-on-jpa-entity | `@Getter` + `@Setter` removes encapsulation; any caller can mutate state. Fix: `@Setter(AccessLevel.PACKAGE)` so only the adapter can mutate. |
| `application/saga/TransferSaga.java:120` | unused-AccountFrozenException-catch | The `catch (AccountFrozenException)` branch on line 123 is unreachable — FROZEN checks at lines 73/99 use `isFrozen()` directly. Dead code. Fix: remove catch, or refactor `isFrozen()` to throw. |
| `infrastructure/persistence/IdempotencyRepositoryAdapter.java:70` | redundant-findById-on-update | `updateResult` does `findById -> setter -> save`. Composite PK already known. Fix: JPQL `@Modifying @Query("UPDATE ... WHERE keyHash=? AND ownerCustomerId=?")`. |
| `interfaces/rest/ProblemDetailExceptionHandler.java:79` | traceId-mdc-key-never-populated | `MDC.get("traceId")` read but nothing in codebase ever does `MDC.put("traceId", ...)`. Always null in ProblemDetail response. Fix: rename key to `correlationId` (which IS populated), or add traceparent baggage propagator. |
| `interfaces/rest/TransferController.java:90` | mdc-leak-on-exception | `MDC.put` with no `try/finally MDC.clear()`. Thread-pool reuse leaks `correlationId` and `idempotencyKeyPrefix` to next request. Fix: try/finally `MDC.remove(...)`, or install `MdcServletFilter`. |
| `infrastructure/config/SecurityConfig.java:60` | deferred-jwt-permits-all-in-scaffold | `anyRequest().authenticated()` configured but `http.oauth2ResourceServer(jwt)` commented out — nothing to validate against. Fix: throw startup failure via `@ConditionalOnExpression` if `issuer-uri` missing in prod. |
| `infrastructure/persistence/OutboxEventPublisher.java:73` | empty-headers-string | `headers` hardcoded to `"{}"`. W3C `traceparent` and `correlationId` belong as Kafka headers. Future relay loses tracing context. Fix: serialize `{"correlationId": ..., "traceparent": MDC.get("traceparent")}`. |
| `resources/application.yml:14` | db-password-default-in-yaml | `${DB_PASSWORD:transfer_pass}` provides a default password in YAML — silent startup with known credential if env var missing. Fix: drop the default; keep defaults only in `application-local.yml`. |
| `application/usecase/CreateTransferUseCase.java:137` | initiatorUserId-equals-customerId-stub | `command.customerId()` passed as both `initiatorUserId` AND `initiatorCustomerId`. Audit trail silently wrong (two domain concepts collapsed). Fix: distinct `initiatorUserId` field from JWT `sub`. |
| `test/.../TransferControllerIT.java:86` | deleteAll-instead-of-truncate | `@BeforeEach` uses `deleteAll` on three tables; FK order must be maintained. Fix: `@Sql` with `TRUNCATE ... CASCADE`. |

### Comments — Nits (5)

<details>
<summary>Click to expand 5 nits</summary>

- **`application/saga/TransferSaga.java:63`** — `documentation: stub-disclaimer-in-logs` — `saga.step=DEBITED` logs `"[stub: no real account-service call in v1]"` per-request. Operators may see this in grep history post-US-006. Move to one-time startup log in `AccountClientStub`.
- **`pom.xml:152`** — `build: env-specific-version-pin` — `junit-platform-launcher 1.10.5` + `surefire 3.5.4` pinned for corporate Maven mirror cache gap. Add TODO with ticket id or move into a profile activated by `-Doffline-cache`.
- **`application/port/in/TransferResult.java:32`** — `bigdecimal-on-wire-is-via-mapper` — `TransferResult.amount` is `BigDecimal` only stringified at boundary. Add `@JsonSerialize(using = ToStringSerializer.class)` for defense-in-depth.
- **`domain/model/Money.java:153`** — `hashCode-uses-stripTrailingZeros` — `hashCode` uses `amount.stripTrailingZeros()` while equals uses `compareTo`. Contract holds; allocates new BigDecimal per hash. Memoize if used as HashMap key on hot path.
- **`TransferServiceApplication.java:14`** — `enableScheduling-without-scheduler` — `@EnableScheduling` on but no `@Scheduled` method anywhere. Remove now and re-add with outbox relay in US-007, or add TODO comment.

</details>

### Metrics Checked

| Metric | Value |
|---|---|
| Coverage | 0.85 |
| Lint | not_run |
| Build | not_run |
| Files reviewed | 25 |
| Unit tests passing | 40/40 (per dev artifact) |
| Integration tests | 6 written, not executed (Docker unavailable) |

### Known Limitations — Accepted

- `AccountClientStub` returns canned 10M THB ACTIVE account — acceptable for US-001/US-003 scaffold; flagged with `@Profile`/`@ConditionalOn` guard fix before US-006.
- Outbox poller writes rows but no Kafka publisher in v1 — acceptable; rows queryable for relay in US-007.
- Saga compensation path defined but not exercised — acceptable for single-write scope; full exercise in US-008.
- `SecurityConfig` defers real JWT validation — acceptable ONLY if service is not exposed externally; must be flipped on before US-006/prod deploy.
- Integration tests not executed due to no Docker — acceptable for scaffold; QA must run them in CI.
- POM junit/surefire version overrides for offline cache — acceptable as a temporary workaround.

### Known Limitations — Concerns ⚠️

- ⚠️ **`AccountClientStub` auto-wired in production with no profile guard** is a money-safety hazard the moment SecurityConfig is hardened — must NOT ship to any environment reachable from real users.
- ⚠️ **`STUB_CUSTOMER_ID` hardcoded principal in `TransferController`** coupled with permit-all SecurityConfig means the `@PreAuthorize` is the sole guard; ownership checks in `findById` succeed for any caller as long as transfers were created under the same stub — must be removed atomically with US-006.

## Quality Gate Check

- [x] 0 blockers
- [x] All majors documented with explicit fix paths
- [x] Hexagonal layering preserved
- [x] Domain invariants enforced in aggregate (not service)
- [x] `Money` uses `BigDecimal` + `NUMERIC(19,4)` + `compareTo` equality
- [x] Idempotency follows ADR-013 protocol
- [x] Outbox + business write in same `@Transactional`
- [x] No direct `kafkaTemplate.send` outside outbox
- [x] Coverage ≥ 0.80 (actual 0.85)
- [x] Known limitations explicitly enumerated (accepted vs concerns)

## Reviewer's Recommendation

**Approve with concerns documented.** Implementation correctness is sound and stub-posture is the dominant theme of findings. The must-fix-before-staging items (notably `AccountClientStub` profile guard, JWT activation, and removal of `STUB_CUSTOMER_ID`) must be flagged for Security to assess and downstream DevOps to enforce as a deployment gate. The 5 majors are acceptable-for-v1 only because the service is not exposed; they must land atomically with US-006.

## Action Items

For `banking-security`:
1. Assess the deployment-environment-gated risks (stub posture) for criticality and compliance impact (PDPA / BoT).
2. Run SAST + secret scan on the v1 code; confirm no PII in logs and no PAN/CVV anywhere.
3. Produce STRIDE per endpoint for `POST /api/v1/transfers` and `GET /api/v1/transfers/{transferId}`.
4. Convert the reviewer's "concerns" list into a formal `must_fix_before_staging` set with gating advice.
5. Confirm banking hard rules (no secrets in code, no PII in logs, no float for money, idempotency on financial POST, audit-via-outbox, no PAN/CVV).

## Links

- **Source JSON:** [S6-reviewer-money-transfer.json](S6-reviewer-money-transfer.json)
- **Previous artifact:** [S5-backend-dev-money-transfer.md](S5-backend-dev-money-transfer.md)
- **Next artifact:** [S7-security-money-transfer.md](S7-security-money-transfer.md)
- **Communications timeline:** [../agents-comms/timeline.md](../agents-comms/timeline.md)
