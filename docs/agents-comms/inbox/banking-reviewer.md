# Inbox — banking-reviewer

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-backend-dev | 2026-05-18 03:26 UTC | DEVELOPMENT → REVIEW | [S5 `f3a7e2d1-8b4c-4f9e-a6d5-2c1b0e9f8a7d`](../../artifacts/S5-backend-dev-money-transfer.md) | 25 files changed in `backend/transfer-service/`, hexagonal scaffold for US-001 + US-003, 40/40 unit tests passing, 6 integration tests written (Docker required), coverage 0.85, 7 documented known limitations |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-security | 2026-05-18 03:42 UTC | REVIEW → SECURITY | [S6 `9e2c4a8b-5f31-4d72-b3a1-7e6c0d9f4b21`](../../artifacts/S6-reviewer-money-transfer.md) | Quality gate PASS — verdict **approved**. Findings: **0 blocker / 5 major / 13 minor / 5 nit**. Hexagonal layering respected, Transfer aggregate enforces invariants, Money uses BigDecimal + compareTo equality, idempotency follows ADR-013 (SHA-256 + payload checksum + 24h TTL + 409 on conflict), outbox written same-tx as business write (no direct `kafkaTemplate.send`). 25 files reviewed. |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | REVIEW → SECURITY | PASS — verdict approved; zero blockers; all majors are v1-acceptable-with-explicit-guard-required-before-US-006; coverage threshold met (0.85 ≥ 0.80) |

## Open Items / Action Required

5 major findings flagged as **v1-acceptable-but-must-harden** before US-006 ships — sent to Security as `known_limitations_concerns`:

1. **`anti-pattern: silent-stub-in-production`** (AccountClientStub.java:22) — `@Component` with no profile guard means it will be auto-wired in production, returning canned 10M THB ACTIVE account regardless of input. Money-safety hazard the moment SecurityConfig hardens. → Fix: `@Profile("!prod")` + `@ConditionalOnProperty` + startup WARN + readiness-probe fail.
2. **`security: hardcoded-customer-id`** (TransferController.java:58) — `STUB_CUSTOMER_ID` UUID passed as authenticated principal for every request; combined with permit-all chain means `findById` ownership check passes for any caller. → Fix: extract from JWT `sub` claim; gate controller with `@Profile("!prod")` until US-006.
3. **`observability: missing-otel-spans-and-metrics`** (CreateTransferUseCase.java:107) — no OTel `@WithSpan`, no Micrometer Counter/Timer for transfer.attempts / completed / failed / idempotency.hits, no MDC `traceId` wiring. Critical for SLO measurement (p95<1s, p99<3s). → Fix: add `@Observed` / `@WithSpan`, inject `MeterRegistry`, wire baggage propagator.
4. **`best-practice: double-call-to-account-client`** (CreateTransferUseCase.java:130) — `AccountClient.getAccountInfo(sourceAccountId)` called twice per transfer (use-case ownership check + saga step 1). With real Resilience4j time-limiter 800ms, doubles latency budget. → Fix: fetch once, pass `AccountInfo` into `TransferSaga.execute(...)`.
5. **`performance: redundant-select-before-save`** (TransferRepositoryAdapter.java:51) — every `save()` does `findById` first to preserve `createdAt`; runs twice per request on the hot path. → Fix: set createdAt in domain factory or use `@CreationTimestamp`.

Other findings (13 minor + 5 nit) all marked acceptable for v1 and tracked in S6 payload `comments[]`. All security-relevant minors were re-flagged by Security in S7.

→ **Action**: monitor Backend Dev's next iteration (US-006). Re-review when those five majors are addressed. No outstanding feedback loops with Backend Dev as of approval timestamp.

## Skills Referenced When Working

- `.claude/skills/code-review-checklists/SKILL.md` — review heuristics: anti-patterns to flag (silent stubs, double network calls, mutable JPA entities, dead exception catches), severity rubric (blocker / major / minor / nit), how to write actionable `suggested_fix` blocks, when to approve-with-conditions vs request-changes.
- `.claude/skills/spring-boot-banking/SKILL.md` — domain-layer purity (no JPA annotations in domain), use-case `@Transactional` boundary discipline, MapStruct vs hand-written mappers, sealed `@ControllerAdvice` for RFC 7807 Problem Details, MDC propagation pitfalls.
- `.claude/skills/banking-security-patterns/SKILL.md` — Money handling (BigDecimal + compareTo + NUMERIC(19,4)), idempotency hashing (SHA-256 of key + payload checksum, 24h TTL), masked logging (AccountId.toString last-4), no PAN / CVV anywhere, outbox-in-tx for audit-grade state changes.

## Workflow Hooks

- On S5 from Backend Dev → review against banking hard rules + best-practices checklists; emit S6 with verdict (approved / changes_required / blocked) and severity-rated findings.
- On `changes_required` verdict → ping Backend Dev with concrete diffs in `suggested_fix`; re-review when Dev re-emits S5 (bump iteration); max 3 re-review cycles before escalation.
- On Architect/Tech Lead spec change → re-review the affected diff if Dev has already shipped.
- On Security adding findings that overlap with review scope → cross-reference in next review; suppress duplicates in S6.
