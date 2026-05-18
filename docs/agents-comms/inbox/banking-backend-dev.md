# Inbox — banking-backend-dev

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-tech-lead | 2026-05-18 13:30 UTC | DESIGN → DEVELOPMENT | [S4 `c8d4e9f6-3a72-4b1d-8e5c-1f9d2a6b7c83`](../../artifacts/S4-tech-lead-money-transfer.md) | Full OpenAPI 3.0.3 contract (POST /api/v1/transfers + GET /api/v1/transfers/{transferId}), 5 Flyway migrations (transfers, transfer_idempotency, saga_state, transfer_outbox, daily_transfer_accumulator), 3 Avro event schemas (TransferRequested / Completed / Failed), 4 ADRs (ADR-013..016), 9 implementation notes covering idempotency atomicity, outbox-in-tx, saga coordinator + recovery, optimistic locking, Resilience4j config, transactional boundaries, reference number generation, log masking, JWT ownership check |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-reviewer | 2026-05-18 03:26 UTC | DEVELOPMENT → REVIEW | [S5 `f3a7e2d1-8b4c-4f9e-a6d5-2c1b0e9f8a7d`](../../artifacts/S5-backend-dev-money-transfer.md) | Quality gate PASS — 25 files changed in `backend/transfer-service/`, hexagonal scaffold for US-001 + US-003, 40/40 unit tests passing (mvn --offline), 6 integration tests written + compile-verified (Testcontainers, not executed — Docker unavailable), unit coverage 0.85, OpenAPI consumed verbatim, 7 known limitations explicitly documented |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | DEVELOPMENT → REVIEW | PASS — coverage ≥ 0.80, all unit tests green, OpenAPI contract honoured, ADR-013 idempotency protocol implemented (SHA-256 keyHash + payload checksum + 24h TTL + same-tx outbox), Money handled via BigDecimal NUMERIC(19,4), no direct kafkaTemplate.send (outbox row only) |

## Open Items / Action Required

7 known limitations carried forward — all explicitly documented so Reviewer, Security, and QA can decide acceptance vs harden-now:

1. **AccountClientStub** returns canned ACTIVE account + 10M THB balance — real Feign/WebClient client deferred to US-006.
2. **Outbox poller stub** — writes to `transfer_outbox` atomically; no Kafka publisher in v1 (deferred to US-007).
3. **Saga compensation path defined but not exercised** — single-write scope in v1; full compensation via account-service in US-008.
4. **SecurityConfig permits all requests** in v1 scaffold — JWT RS256 validation requires live identity-service JWKS endpoint (deferred to US-006); method-level @PreAuthorize remains as guard.
5. **Integration tests (TransferControllerIT)** require Docker for Testcontainers PostgreSQL — not executed in sandbox; file compiles cleanly, runs on developer machines with Docker.
6. **Transfer.referenceNumber field** changed from `final` to non-final to fix compilation in pre-existing domain code — blank-then-assigned pattern preserved with comment. Reviewer flagged as `minor` — to be refactored into deterministic derivation inside `Transfer.create()`.
7. **POM overrides** — `junit-jupiter.version=5.10.5` and Surefire `3.5.4` pinned to work around sandbox Maven cache gaps; revert when environment has full Maven Central access.

→ **Action from Reviewer (S6 verdict approved):** 5 majors flagged for hardening before US-006 ships (not blockers for handoff but must be addressed). Address atomically with US-006 work.

→ **Action from Security (S7 verdict approved):** 10 must-fix-before-staging items (ITEM-1..ITEM-10). ITEM-1 / ITEM-2 / ITEM-3 are CRITICAL-FOR-DEPLOY (AccountClientStub profile guard, SecurityConfig JWT activation + startup fail-fast on missing issuer-uri, STUB_CUSTOMER_ID removal). Coordinate landing with US-006.

→ **Action from QA (S8 delivered 2026-05-18 11:35 UTC):** Quality gate ⚠️ PASS-with-conditions. 40/40 unit pass, money-path coverage 95%+, 6 IT compile-clean (Docker-gated). **2 bugs filed back to you** — see Pending Action below.

---

## Pending Action — US-006 Hardening Iteration

The SDLC chain has reached `DEPLOYMENT → DONE` for the v1 scaffold (S9 complete). Staging promotion is **gated on backend-dev work** that must land atomically before the next chain run. Consolidated checklist:

### 1. QA Bugs to Fix (from S8 BUG-QA-001 / BUG-QA-002)

| Bug ID | Severity | Summary | Action |
|---|---|---|---|
| BUG-QA-001 | Medium | US-003-AC-2 — idempotency replay of a REJECTED transfer is untested. If `deserializeCachedResult` silently reconstructs COMPLETED from a cached FAILED body, retry can return false success after a failed transfer. | Add unit test in `CreateTransferUseCaseTest` covering replay of cached FAILED body; add integration test in `TransferControllerIT` confirming full HTTP 422 round-trip on replay. |
| BUG-QA-002 | Low | US-003-AC-3 — TTL expiry path has zero coverage. `IdempotencyRepository.findValid()` JPQL `expires_at` predicate (`>=` vs `>`) is never exercised; a wrong predicate would silently replay expired keys. | Add integration test inserting an idempotency record with `expires_at` in the past, calling `execute()`, and asserting a fresh transfer with new `transferId`. |

Also schedule (recommended for next iteration, S8 T-001..T-008): integration tests for the bugs above (T-001, T-002), `MEMO_TOO_LONG` controller-level 400 (T-003), outbox audit fields (T-004), `AccountId.toString` masking unit test (T-005), Gatling perf baseline (T-006), concurrent double-debit (T-007), MDC.clear() between requests (T-008).

### 2. Schema Mismatches Surfaced by DevOps (from S9 `migration_fixes`)

DevOps smoke test exposed **7 pre-existing JPA vs Flyway mismatches**. Migrations were patched to unblock the smoke; you must reconcile the entity annotations (or revert the migration with a deliberate `@JdbcTypeCode(SqlTypes.JSON)`):

| # | Migration file | Column | DevOps patched to | Required backend action |
|---|---|---|---|---|
| 1 | `V001__create_transfers.sql` | `currency` | `VARCHAR(3)` | Confirm `@Column(length=3)` on `TransferJpaEntity.currency` |
| 2 | `V002__create_transfer_idempotency.sql` | `key_hash` | `VARCHAR(64)` | Confirm `@Column(length=64)` on `IdempotencyJpaEntity.keyHash` |
| 3 | `V002__create_transfer_idempotency.sql` | `request_checksum` | `VARCHAR(64)` | Confirm `@Column(length=64)` on `IdempotencyJpaEntity.requestChecksum` |
| 4 | `V002__create_transfer_idempotency.sql` | `cached_response_body` | `TEXT` | Decide: keep `@Column(columnDefinition="TEXT")` or revert migration to `JSONB` + add `@JdbcTypeCode(SqlTypes.JSON)` |
| 5 | `V002__create_transfer_idempotency.sql` | `cached_response_code` | `INTEGER` | Confirm Java `int` field on `IdempotencyJpaEntity.cachedResponseCode` |
| 6 | `V004__create_outbox.sql` | `headers` | `TEXT` | Same decision as #4 |
| 7 | `V004__create_outbox.sql` | `payload` | `TEXT` | Same decision as #4 |

`schema_version SMALLINT` in V004 is correct — `OutboxJpaEntity.schemaVersion` is Java `short`. **Do not change.**

### 3. Five Must-Fix Items Still Blocking Staging (from S9 + S7)

These were carried from Security S7 and remain open after S9 — backend-dev owns them for US-006:

| # | Item | Severity tier | Source |
|---|---|---|---|
| 1 | JWT RS256 validation in `SecurityConfig` (`oauth2ResourceServer(jwt)`) + `issuer-uri` from Vault; startup-failure if env is staging/prod and issuer-uri blank | CRITICAL-FOR-DEPLOY | Security ITEM-2 |
| 2 | Delete `STUB_CUSTOMER_ID` from `TransferController`; inject `@AuthenticationPrincipal Jwt`; throw 401 if absent (must land atomically with item 1) | CRITICAL-FOR-DEPLOY | Security ITEM-3 |
| 3 | `AccountClientStub` gated `@Profile("!prod & !staging")` + `@ConditionalOnProperty("transfer.account-client.stub.enabled")` + startup-failure bean | CRITICAL-FOR-DEPLOY | Security ITEM-1 |
| 4 | MDC clear `ServletFilter` (try/finally `MDC.clear()` at end of every request) + integration test asserting MDC empty between requests on same thread | BEFORE-STAGING | Security ITEM-7 |
| 5 | Wire Micrometer-tracing + W3C `traceparent` `ServletFilter` populating `MDC.traceId` (or rename `ProblemDetail.traceId` → `correlationId` for v1) | BEFORE-STAGING | Security ITEM-8 |

DevOps closed Security ITEM-9 (actuator port + NetworkPolicy + swagger-ui + topology spread) and ITEM-10 (SCA + SBOM + container scan + cosign) at the infra layer — no backend work required for those. ITEM-4 (DB_PASSWORD) is partially covered by the Vault CSI stub in Helm; you still own the YAML default removal.

**Atomicity rule:** items 1, 2, 3 must land together — cannot ship JWT without removing stub customer ID, cannot remove stub customer ID without the real `AccountClient`. Plan a single PR.

## Skills Referenced When Working

- `.claude/skills/spring-boot-banking/SKILL.md` — hexagonal layering for Spring Boot 3.x + Java 21, ports & adapters layout, @Transactional discipline (only on use-case methods, not adapters), JPA entity isolation from domain, MapStruct for DTO mapping, Resilience4j configuration, RFC 7807 sealed-hierarchy error handler.
- `.claude/skills/banking-security-patterns/SKILL.md` — Money as BigDecimal with NUMERIC(19,4) + compareTo equality, Idempotency-Key SHA-256 hashing + 24h TTL + 409 on conflict (ADR-013), AccountId masked toString to last-4 chars, log-no-PII conventions, mass-assignment-safe records, sealed ProblemDetail responses with no stack traces.

## Workflow Hooks

- On S4 from Tech Lead → scaffold service per hexagonal layering; implement use-cases per AC; emit S5.
- **On bug from QA** → reproduce in failing test first, implement fix, add regression test, run all tests, bump iteration counter, re-emit S5 (max 3 retries before escalation to Tech Lead).
- **On review comments (blocker / major) from Reviewer** → address atomically; re-run unit tests; bump iteration; re-emit S5. Minors and nits documented in known_limitations if not addressed now.
- **On security must-fix-before-staging** → schedule into the next US-### work item; do not ship to staging/prod until ITEMs marked CRITICAL-FOR-DEPLOY are closed.
- **On contract drift** (OpenAPI change from Tech Lead) → regenerate DTOs / mappers, re-run tests, document the bump.
