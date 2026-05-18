# Inbox — banking-qa

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-security | 2026-05-18 04:05 UTC | SECURITY → QA | [S7 `16792683-0871-4e95-826c-2711bf2a14fc`](../../artifacts/S7-security-money-transfer.md) | **Processed (S8 emitted 2026-05-18 11:35 UTC).** Verdict approved (0 critical / 0 high / 3 medium / 7 low / 2 info), 10 must-fix-before-staging items tracked, banking hard rules all green except `no_secrets_in_code` (low), PCI-DSS out of scope, STRIDE done for both endpoints, security-positive testing required for idempotency replay + conflict, ownership check, masked-logging behaviour. Ingested into S8 as test-design inputs. |

(Context for QA — earlier upstream artifacts visible for traceability:)

| Ref | From | Date | Artifact | Why relevant |
|---|---|---|---|---|
| S5 | banking-backend-dev | 2026-05-18 03:26 UTC | [S5 `f3a7e2d1-8b4c-4f9e-a6d5-2c1b0e9f8a7d`](../../artifacts/S5-backend-dev-money-transfer.md) | 40 unit + 6 integration tests to verify; 25 files changed; known_limitations to test around |
| S6 | banking-reviewer | 2026-05-18 03:42 UTC | [S6 `9e2c4a8b-5f31-4d72-b3a1-7e6c0d9f4b21`](../../artifacts/S6-reviewer-money-transfer.md) | 18 findings (5 major + 13 minor) to inform negative test design |
| S2 | banking-ba | 2026-05-18 09:00 UTC | [S2 `a3f7c2e1-84b0-4d9f-b6e3-2c51f0d8a749`](../../artifacts/S2-ba-money-transfer.md) | 43 AC across 11 user stories — source of truth for AC coverage gap analysis |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-devops | 2026-05-18 11:35 UTC | QA → DEPLOYMENT | [S8 `c2f84d17-3b9e-4a06-95c1-8e7d0f2a5b3c`](../../artifacts/S8-qa-money-transfer.md) | ⚠️ **Quality gate PASS (with conditions).** 40/40 unit tests pass (verified by actual `mvn test` on JDK 21). Money-path coverage: `Money` 98.0%, `Transfer` 95.2%, `CreateTransferUseCase` 86.2%. 6 integration tests compile-clean but **Docker-gated** (Colima API v1.32 < required v1.44) — CI must run them. Contract / E2E / Performance / Mutation: deferred per scope. AC coverage US-001: 3/4 (2 partial). US-003: 3/4 (2 explicit gaps). |
| 2 | banking-backend-dev | 2026-05-18 11:35 UTC | BUG (filed back) | BUG-QA-001 (medium) — see [S8](../../artifacts/S8-qa-money-transfer.md#bug-qa-001--medium) | US-003-AC-2 idempotency replay of REJECTED transfer untested. Action: add unit test in `CreateTransferUseCaseTest` for cached FAILED body + integration test in `TransferControllerIT` for HTTP 422 replay round-trip. Backend-dev US-006. |
| 3 | banking-backend-dev | 2026-05-18 11:35 UTC | BUG (filed back) | BUG-QA-002 (low) — see [S8](../../artifacts/S8-qa-money-transfer.md#bug-qa-002--low) | US-003-AC-3 TTL expiry path has zero coverage. Action: integration test inserting idempotency record with `expires_at` in past and asserting a fresh transfer is created. Backend-dev US-006. |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | QA → DEPLOY | ⚠️ PASS-with-conditions — unit gate met (40/40, money-path ≥ 95%), integration gate Docker-gated (CI must execute), 2 AC gaps reported as bugs back to backend-dev |

## Open Items / Action Required

**S8 emitted — handoff complete.** DevOps has consumed the artifact and closed the v1 chain (S9). For iteration 2 (US-006), QA will need to re-run when backend-dev resolves BUG-QA-001 / BUG-QA-002 and lands the 5 security blockers — re-execute the full suite (40 unit + 6 IT in CI-with-Docker) + add the 8 recommended tests (T-001..T-008) + perform the Gatling baseline at first staging deploy.

### Historical scope of S8 (already executed)

The work below describes what S8 covered — kept for traceability:

1. **Verify** the 40 unit tests + 6 integration tests Backend Dev shipped — run `mvn test` and (if Docker available) `mvn verify` to execute Testcontainers integration suite. Confirm `40/40 passing` and Testcontainers green.
2. **AC coverage gap analysis** — diff Backend Dev's test list against the 43 AC in S2. v1 only covers US-001 + US-003 (happy path + idempotency); US-002 (insufficient), US-004 (per-tx limit), US-005 (daily limit), US-006 (frozen), US-007 (concurrent double-debit), US-008 (saga compensation), US-009 (AML), US-010 (payee), US-011 (notifications) are **not yet implemented** — file as deferred-test-debt, not blocker.
3. **Add tests** for AC gaps within the current implementation scope:
   - Idempotency-Key replay returns IDEMPOTENT_REPLAY with same body (US-003 AC1).
   - Idempotency conflict (same key, different payload) returns 409 IDEMPOTENCY_KEY_CONFLICT (US-003 AC4).
   - Memo > 200 chars rejected with 400 MEMO_TOO_LONG before any debit (US-001 AC4).
   - Ownership check on GET /transfers/{id} (will need post-US-006 JWT to verify; flag as deferred).
4. **Security-positive tests** from S7 — assert AccountId.toString() returns masked last-4 (defends Finding S-07); assert no stack trace in ProblemDetail body; assert outbox row written same-tx as transfer row.
5. **Performance** — if Docker available, run a p95 micro-benchmark on POST /transfers (target < 1s for synchronous happy path; v1 has stubs so this is a smoke test, not the real perf gate which awaits real account-service in US-006).
6. **Sign off** — emit S8 artifact with `quality_gate_passed: true/false`, test report, AC coverage matrix, deferred-test-debt list; hand to `banking-devops`.

Other open items to track during QA pass:
- Reviewer's 5 majors and Security's 10 must-fix-before-staging — verify they don't introduce regressions when Backend Dev addresses them in US-006 iteration.
- Integration tests use `deleteAll` instead of `TRUNCATE ... CASCADE` (Reviewer S6 minor) — file as test-quality improvement.

## Skills Referenced When Working

- `.claude/skills/banking-test-automation/SKILL.md` — JUnit 5 + AssertJ patterns, Testcontainers PostgreSQL setup, idempotency replay assertions, BigDecimal compareTo assertions (`assertThat(money).usingComparator(Money::compareTo).isEqualTo(...)`), Awaitility for async event assertions, ArchUnit for layering enforcement.
- `.claude/skills/spring-boot-banking/SKILL.md` — `@SpringBootTest` slicing, `@WebMvcTest` vs `@DataJpaTest` vs full-context, `@WithMockUser` for v1 auth-bypass tests (acceptable because real JWT is deferred to US-006), MapStruct mapper testing.

## Workflow Hooks

- On S7 from Security → ingest findings as test-cases-to-add; review Backend Dev's existing test suite; identify AC coverage gaps; add tests for gaps; run full suite; emit S8.
- **On bug discovered (test fails)** → file structured bug back to Backend Dev (artifact-class BUG) with reproducer test; bump iteration; wait for Backend Dev to re-emit S5; re-run.
- On Backend Dev re-iteration (US-006 etc.) → re-run full suite + new tests for newly-implemented AC; close out deferred-test-debt items.
- On DevOps blocker (e.g., CI pipeline failure that needs test split) → coordinate test segmentation (unit / integration / e2e tags).
