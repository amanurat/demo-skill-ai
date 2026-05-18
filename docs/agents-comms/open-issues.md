# Open Issues — Money Transfer

> Consolidated tracking: BA open questions, Architect risks, Reviewer concerns, Security must-fix-before-staging, Backend known limitations. Updated after each phase handoff.
> Cross-reference: [timeline.md](timeline.md) · [dashboard.md](dashboard.md) · [decisions-log.md](decisions-log.md)

---

## BA Open Questions (S2) — Pending SME Confirmation

Source: `docs/artifacts/S2-ba-money-transfer.json` → `payload.open_questions[]`

| ID | Question | Owner | Blocking? |
|---|---|---|---|
| OQ-001 | Daily limit reset timezone — Bangkok time (UTC+7) vs UTC? Affects customers transacting around midnight. | Business / Compliance SME | ⚠️ Architecturally decided in ADR-006 (Bangkok). Blocks `DailyTransferAccumulator` go-live; **not** blocking US-001/US-003 dev. |
| OQ-002 | Per-tx and daily limits are "configurable per customer tier" — who manages tier assignment and overrides? Self-service or ops-driven? Affects whether an admin API is in scope for v1. | Ops | No (deferred — admin API out of v1 scope) |
| OQ-003 | Own-to-own transfer UX flow — should source/destination validation differ for self-transfers (no payee lookup, no masked-name confirmation)? | UX | No (defer to frontend phase) |
| OQ-004 | Notification channel fallback policy — if a channel is unavailable, is the transfer "notified" if 1 of 3 delivers? (Current AC assumes 2 of 3.) | Product | No (default 2-of-3 holds; needs sign-off) |
| OQ-005 | AML 2,000,000 THB threshold scope — inbound credits, outbound debits, or combined volume? AML Act typically refers to cash transactions; does it apply to electronic intra-bank? | Compliance SME | ⚠️ Architecturally decided in ADR-007 (outbound, per-customer). Blocks AML feature design go-live; **not** blocking US-001/US-003 dev. |
| OQ-006 | `COMPENSATION_FAILED` (double-fault) — what is the customer-facing SLA for manual Ops resolution? Regulatory obligation to restore funds within a specific timeframe? | Ops | No (runbook scope) |
| OQ-007 | Idempotency-Key generation — client-side (Angular) or server-side? If client, what format (UUID v4 recommended)? SDK behavior on timeout? | Mobile / Web teams | No (ADR-013 ratifies SHA-256(client-supplied raw key); UUID v4 expected; needs client-team confirmation) |
| OQ-008 | Payee masked name display — "first name + last initial" may be insufficient for common Thai names (many "Somchai S."). UX confirmation needed; consider showing last 4 of account number alongside. | UX | No (US-010 scope) |

---

## Architect Risks (S3)

Source: `docs/artifacts/S3-solution-architect-money-transfer.json` → `payload.risks[]`

| ID | Risk | Severity | Mitigation status |
|---|---|---|---|
| RISK-001 | Saga compensation double-fault (`COMPENSATION_FAILED`) leaves funds locked when both credit and compensating re-credit fail. | High | Mitigated by: (1) persisted `saga_state` for recovery; (2) reconciliation job for `COMPENSATION_PENDING/FAILED` > 10 min; (3) Ops runbook (OQ-006 input); (4) daily ledger-service reconciliation flags unbalanced journals. ADR-001. |
| RISK-002 | Daily accumulator hot-row contention at midnight reset; concurrent first-transfers race to insert. | Medium | Mitigated by `INSERT ... ON CONFLICT DO UPDATE` on `(account_id, accumulation_date)`; subsequent transfers fall into optimistic-lock update path. Integration test with parallel inserts required. |
| RISK-003 | Kafka consumer lag spike during 500 TPS bursts causes notification SLA breach. | Medium | Mitigated by: 30 topic partitions; HPA on `kafka_consumer_lag`; pre-warmed provider connections; per-provider circuit breaker; parallel `CompletableFuture` dispatch per event. |
| RISK-004 | `transfer_idempotency` TTL purge job lag fills disk (~8.6M rows/day at 100 TPS). | Low | Mitigated by: hourly purge `DELETE WHERE expires_at < now() LIMIT 100000`; row-count alert > 30M; partition `transfer_idempotency` by `created_at` day so old partitions can be `DROP`ped. |
| RISK-005 | OQ-001 / OQ-005 assumptions wrong → rework on ADR-006 (timezone) and ADR-007 (AML scope). | Medium | Mitigated by: (1) ADR-006 isolated to one config flag; (2) ADR-007 schema is `account_id`-based but aggregation to `customer_id` is a view change (no migration); (3) both flagged as blockers to go-live. Backend dev US-001/US-003 unaffected. |
| RISK-006 | `account-service` becomes synchronous bottleneck — every transfer makes 4–5 sync calls. | High | Mitigated by: Resilience4j time-limiter 800ms + circuit breaker 50% errors / 10s; bulkhead with separate thread pools; HPA with pre-provisioned replicas; combine 3 status+balance reads into single "reserve-with-check" RPC (Tech Lead design owed). |

---

## Reviewer Concerns (S6) — 5 Majors Carried Forward

Source: `docs/artifacts/S6-reviewer-money-transfer.json` → `payload.comments[]` where `severity=major`. All accepted-for-v1 but must land before US-006.

| # | File · Line | Rule | Major finding | Fix expected in |
|---|---|---|---|---|
| 1 | `infrastructure/client/AccountClientStub.java:22` | `anti-pattern: silent-stub-in-production` | `@Component` with no profile guard; auto-wired in any env and returns canned 10M THB ACTIVE. Money-safety hazard once SecurityConfig hardens. | US-006 (and Security ITEM-1) |
| 2 | `infrastructure/persistence/TransferRepositoryAdapter.java:51` | `performance: redundant-select-before-save` | Every `save()` does `findById` first; on hot path `createTransfer` does this twice per request (insert PENDING + update FINAL) — two extra SELECTs on a p95<1s endpoint. | Iteration 2 perf pass before staging |
| 3 | `application/usecase/CreateTransferUseCase.java:107` | `observability: missing-otel-spans-and-metrics` | No `@WithSpan`, no `Counter`/`Timer` for `transfer.attempts/completed/failed`, no MDC `traceId` baggage. Blocks NFR Prometheus + OTel SLO measurement. | Before QA can measure SLA gate (S8) |
| 4 | `interfaces/rest/TransferController.java:58` | `security: hardcoded-customer-id` | `STUB_CUSTOMER_ID` UUID used as authenticated principal for every request, bypassing JWT subject. With permit-all chain this makes `@PreAuthorize` the sole guard and IDOR effectively passes. | US-006 (and Security ITEM-3) |
| 5 | `application/usecase/CreateTransferUseCase.java:130` | `best-practice: double-call-to-account-client` | `AccountClient.getAccountInfo(sourceAccountId)` called once in use case AND once inside `TransferSaga.execute()` — doubles latency budget with real Resilience4j-decorated client; balance may also drift between calls. | Iteration 2 perf pass (refactor: pass `AccountInfo` into saga). |

Minor (13) and nit (5) comments are not blockers but are listed in S6 JSON `payload.comments[]` and should be cleaned during US-006 hardening.

### Reviewer "concerns" escalated explicitly (`known_limitations_concerns`)

1. `AccountClientStub` auto-wired in production with no profile guard — money-safety hazard the moment `SecurityConfig` is hardened; must NOT ship to any environment reachable from real users.
2. `STUB_CUSTOMER_ID` hardcoded principal + permit-all `SecurityConfig` means `@PreAuthorize` is the sole guard, and ownership checks in `findById` will succeed for any caller as long as transfers were created under the same stub — must be removed atomically with US-006.

---

## Security Must-Fix-Before-Staging (S7)

Source: `docs/artifacts/S7-security-money-transfer.json` → `payload.must_fix_before_staging[]`

| # | Item | Severity tier | OWASP / Finding | Phase to fix | S9 status |
|---|---|---|---|---|---|
| 1 | `@Profile('!prod & !staging')` + `@ConditionalOnProperty('transfer.account-client.stub.enabled')` on `AccountClientStub`; real `AccountClient` adapter must be present; startup-failure bean if env is staging/prod and no real adapter. | CRITICAL-FOR-DEPLOY | A05 Misconfiguration · S-03 medium · CWE-1188 | Pre-staging (US-006) | ❌ Backend-dev — **still blocking staging** |
| 2 | Uncomment `SecurityConfig.oauth2ResourceServer(jwt)`; `spring.security.oauth2.resourceserver.jwt.issuer-uri` from Vault; `@ConditionalOnExpression` throws `BeanInitializationException` if env is staging/prod and issuer-uri blank. | CRITICAL-FOR-DEPLOY | A05 · S-01 medium · CWE-1188 | Pre-staging (US-006) | ❌ Backend-dev — **still blocking staging** |
| 3 | Delete `STUB_CUSTOMER_ID` from `TransferController`; replace with `@AuthenticationPrincipal Jwt` extracting `sub` (initiatorUserId) and `customer_id` claims; throw 401 if absent. Must land atomically with ITEM-2. | CRITICAL-FOR-DEPLOY | A01 Broken-Access-Control · S-02 medium · CWE-639 | Pre-staging (US-006) | ❌ Backend-dev — **still blocking staging** |
| 4 | Remove `DB_PASSWORD` default `transfer_pass` from `application.yml`; use `${DB_PASSWORD}` with no default; move local defaults to `application-local.yml`; add CI secret-scan gate. | BEFORE-PROD | A05 · S-04 low · CWE-258 | Before prod | ⚠️ DevOps wired Vault CSI stub in Helm (extraVolumes + SecretProviderClass); backend-dev still owns YAML cleanup |
| 5 | Add rate-limit on `POST /api/v1/transfers` (Bucket4j / Resilience4j `@RateLimiter` 10 req/sec per JWT subject); add `@PreAuthorize` scope `hasAuthority(SCOPE_transfer.write)`. | BEFORE-PROD | A04 Insecure-Design · S-10 low · CWE-770 | Before prod | ❌ Backend-dev — not started |
| 6 | `OutboxEventPublisher` must populate `headers` with at least `{correlationId, traceparent, eventId}` for Kafka consumer correlation. | BEFORE-US-007-RELAY-SHIPS | A09 · S-06 low · CWE-778 | Before US-007 | ❌ Backend-dev — deferred (US-007 dependency) |
| 7 | ServletFilter (or try/finally) that calls `MDC.clear()` at end of every request; integration test asserting MDC empty between requests on same thread. | BEFORE-STAGING | A09 · S-05 low · CWE-117 | Before staging | ❌ Backend-dev — **still blocking staging** |
| 8 | Wire Micrometer-tracing + W3C `traceparent` ServletFilter populating `MDC.traceId`; OR rename `ProblemDetail.traceId` key to `correlationId` for v1. | BEFORE-STAGING | A09 · S-12 info · CWE-778 | Before staging | ❌ Backend-dev — **still blocking staging** |
| 9 | DevOps controls: actuator on internal management port 9090; K8s NetworkPolicy restricting Prometheus scrape; `swagger-ui` disabled in prod profile; K8s topology constraint pinning pods + DB to Thailand region (PDPA data residency). | BEFORE-STAGING | A05 · S-09 low · CWE-489 + PDPA data residency | S9 DevOps | ✅ DevOps closed — `MANAGEMENT_SERVER_PORT=9090` + NetworkPolicy egress allow-list + `SPRINGDOC_SWAGGER_UI_ENABLED=false` + topology spread for Thailand region (also non-root container + resource limits + PDB minAvailable 2 + HPA min 3 / max 12) |
| 10 | SCA scan (Dependabot / OWASP-DependencyCheck) wired in CI; SBOM published per build; container scan (Trivy / Grype) on every image. | BEFORE-PROD | Supply-chain governance | S9 DevOps | ✅ DevOps closed — Semgrep + OWASP DC + Trivy fs in `sast-sca` stage; Trivy image scan in `container-scan` stage; syft SBOM + cosign signing in `push-registry` stage |

**Summary (post-S9):**
- ✅ Addressed at infra layer by DevOps (8 controls): ITEM-9 (actuator port + NetworkPolicy + swagger-ui + topology spread + non-root + resource limits) and ITEM-10 (SCA + SBOM + cosign + container scan). Also Vault stub (partial coverage of ITEM-4).
- ❌ Still blocking staging (5 backend items): ITEM-1, ITEM-2, ITEM-3 (CRITICAL-FOR-DEPLOY), ITEM-7, ITEM-8 (BEFORE-STAGING). All owned by backend-dev for US-006.

### Security additional findings (informational, not in must-fix)

- **S-07 (low, CWE-532)** — `TransferSaga` logs `transfer.getSourceAccountId()` which today masks correctly but is fragile to future `toString` refactors. Defense-in-depth: make `AccountId.toString` final + unit test, or explicit masking at the call site.
- **S-08 (low, CWE-532)** — `OutboxEventPublisher` payload includes full account UUIDs; document event schema as "personal data — internal only"; confirm Kafka topic ACLs; add `transfer_outbox` retention purge (`dispatched=true AND created_at < now() - 30d`).
- **S-11 (info, CWE-916)** — SHA-256 (unsalted, no HMAC) used for Idempotency-Key hash. Acceptable because the key is a client-generated UUID v4 (122 bits) and the hash is an index, not an authenticator. Document at the call site to prevent future misuse.

---

## QA Bugs Filed (S8)

Source: `docs/artifacts/S8-qa-money-transfer.json` → `payload.bugs_found[]`. Both looped back to `banking-backend-dev` for fix in US-006.

| Bug ID | Severity | Summary | Affected class | Loop back to | Action |
|---|---|---|---|---|---|
| BUG-QA-001 | Medium | **US-003-AC-2 untested** — idempotency replay of a REJECTED transfer is never verified. `updateResult` writes `httpCode=422` for failed transfers; if `deserializeCachedResult` silently reconstructs a COMPLETED status from a cached FAILED body (e.g., Jackson edge case), a consumer retry after a failed transfer could receive a false success response. | `CreateTransferUseCase.deserializeCachedResult` + `IdempotencyRepositoryAdapter.updateResult` | banking-backend-dev | Add unit test in `CreateTransferUseCaseTest` covering replay of cached FAILED body; add integration test in `TransferControllerIT` confirming full HTTP 422 round-trip on replay. |
| BUG-QA-002 | Low | **US-003-AC-3 untested** — TTL expiry path has zero coverage. `IdempotencyRepository.findValid()` filters by `expires_at` but the JPQL predicate (`>=` vs `>`) is never exercised; a wrong predicate would silently replay expired keys as if active. | `IdempotencyRepositoryAdapter.findValid` (JPQL WHERE clause) | banking-backend-dev | Add integration test inserting an idempotency record with `expires_at` in the past, calling `execute()`, and asserting a fresh transfer is created (not replayed) with a new `transferId`. |

### Tests Recommended for Next Iteration (T-001..T-008)

Source: `S8 payload.tests_recommended_for_next_iteration[]`. **Not bugs** — gaps to close in US-006.

| ID | Test name | Type | AC / Source | Priority |
|---|---|---|---|---|
| T-001 | `should_return_same_rejection_when_replaying_failed_transfer_key` | Integration | US-003-AC-2 | High |
| T-002 | `should_treat_key_as_new_transfer_when_idempotency_ttl_expired` | Integration | US-003-AC-3 | High |
| T-003 | `should_return_400_memo_too_long_when_memo_exceeds_200_characters` | Integration | US-001-AC-4 | Medium |
| T-004 | `should_persist_outbox_row_with_required_audit_fields` | Integration | US-001-AC-2 | Medium |
| T-005 | `should_mask_accountId_in_toString_of_AccountId` | Unit | Security S-07 | Medium |
| T-006 | `transferCompletes_underP95Sla_at_100rps` | Performance | US-001-AC-1 (p95 < 1s) | High |
| T-007 | `concurrentTransfers_doNotDoubleDebit` | Integration | US-007 | High |
| T-008 | `should_clear_mdc_between_requests_on_same_thread` | Integration | Security S-05 / ITEM-7 | Medium |

---

## DevOps Discovered (S9) — 7 Schema Mismatches Fixed During Smoke Test

Source: `docs/artifacts/S9-devops-money-transfer.json` → `payload.migration_fixes`. These were pre-existing backend bugs in the Flyway DDL vs the JPA entity annotations, **surfaced for the first time** by real Docker runtime during smoke testing (invisible to unit tests; QA's Testcontainers were Docker-gated so didn't catch them either). DevOps patched the migration files to unblock the smoke test; backend-dev must reconcile entity annotations in US-006.

| # | Migration file | Column | Was | Now | Reason |
|---|---|---|---|---|---|
| 1 | `V001__create_transfers.sql` | `currency` | `CHAR(3)` (bpchar) | `VARCHAR(3)` | Hibernate `@Column(length=3)` String → varchar |
| 2 | `V002__create_transfer_idempotency.sql` | `key_hash` | `CHAR(64)` (bpchar) | `VARCHAR(64)` | Hibernate `@Column(length=64)` String → varchar |
| 3 | `V002__create_transfer_idempotency.sql` | `request_checksum` | `CHAR(64)` (bpchar) | `VARCHAR(64)` | Same |
| 4 | `V002__create_transfer_idempotency.sql` | `cached_response_body` | `JSONB` | `TEXT` | `@Column(columnDefinition="TEXT")` in `IdempotencyJpaEntity` |
| 5 | `V002__create_transfer_idempotency.sql` | `cached_response_code` | `SMALLINT` (int2) | `INTEGER` (int4) | Java `int` primitive → int4 |
| 6 | `V004__create_outbox.sql` | `headers` | `JSONB` | `TEXT` | `@Column(columnDefinition="TEXT")` in `OutboxJpaEntity` |
| 7 | `V004__create_outbox.sql` | `payload` | `JSONB` | `TEXT` | Same |

`schema_version SMALLINT` in V004 is **correct** — `OutboxJpaEntity.schemaVersion` is Java `short` → int2/SMALLINT (was briefly changed to INTEGER during fix pass and reverted).

**Action for backend-dev:** Decide per column whether to (a) keep the migration fix and align the entity, or (b) change the entity to match the original intent (e.g., revert `cached_response_body` to JSONB with `@JdbcTypeCode(SqlTypes.JSON)`). Track as part of US-006 hardening.

---

## Backend Known Limitations (S5)

Source: `docs/artifacts/S5-backend-dev-money-transfer.json` → `payload.known_limitations[]`. All accepted by Reviewer (S6 `known_limitations_accepted`).

| # | Limitation | File / scope | Lifts in |
|---|---|---|---|
| 1 | `AccountClientStub` returns canned `ACTIVE` 10M THB balance (real Feign/WebClient deferred). | `infrastructure/client/AccountClientStub.java` | US-006 |
| 2 | Outbox poller is a stub — writes to `transfer_outbox` table atomically; no Kafka publisher in v1. | `infrastructure/persistence/OutboxEventPublisher.java` | US-007 |
| 3 | Saga compensation path defined but not exercised — single-write scope. | `application/saga/TransferSaga.java` | US-008 |
| 4 | `SecurityConfig` permits all requests in v1 scaffold — JWT RS256 validation requires live `identity-service` JWKS. `@PreAuthorize` remains as guard. | `infrastructure/config/SecurityConfig.java` | US-006 / ADR-002 |
| 5 | Integration tests (`TransferControllerIT`) require Docker for Testcontainers PostgreSQL — not executed in sandbox; file compiles cleanly. | `src/test/java/com/bank/transfer/interfaces/rest/TransferControllerIT.java` | S8 (QA, on CI with Docker) |
| 6 | `Transfer.referenceNumber` changed from `final` to non-final to fix pre-existing compilation error; blank-then-assigned pattern preserved with comment. | `domain/model/Transfer.java` | Reviewer S6 minor recommends moving derivation into `Transfer.create()` to restore immutability |
| 7 | `pom.xml`: `junit-jupiter.version=5.10.5` and Surefire `3.5.4` pinned to work around sandbox Maven cache gaps; revert when full Maven Central access is available. | `backend/transfer-service/pom.xml` | Once dev environment has full mirror access |

---

## Decision Required From User

_(none currently — chain proceeded autonomously per user directive; flagged here when it changes)_

Next decision the user may want to make:
- **Pacing for S8 → S9:** continue autonomous chain, or pause at QA-complete for user review before DevOps?
- **OQ-001 / OQ-005 escalation:** raise to a real Compliance SME now (would unblock production go-live), or keep as ADR-006 / ADR-007 assumptions while iteration continues?

---

## Issue Count Summary

| Category | Open | Resolved | Total |
|---|---|---|---|
| BA Open Questions (OQ) | 8 | 0 | 8 |
| Architect Risks (RISK) | 6 (mitigated, not closed) | 0 | 6 |
| Reviewer Majors | 5 | 0 | 5 |
| Security Must-Fix-Before-Staging | 5 (backend, blocking staging) | 2 fully closed (ITEM-9, ITEM-10) + 1 partial (ITEM-4 Vault stub) | 10 |
| QA Bugs Filed (BUG-QA-###) | 2 | 0 | 2 |
| QA Recommended Tests (T-###) | 8 | 0 | 8 |
| DevOps Schema Mismatches (migration_fixes) | 7 (patched in migrations; entities pending reconciliation) | 0 | 7 |
| Backend Known Limitations | 7 | 0 | 7 |
| **Total tracked items** | **48 open** | **2 closed + 1 partial** | **53** |
