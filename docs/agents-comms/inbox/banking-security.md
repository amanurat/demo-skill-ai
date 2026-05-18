# Inbox — banking-security

> Personal communications view for this agent. What you've received, produced, and what's pending.

## Active Feature: money-transfer

## Received (upstream)

| # | From | Date | Phase | Artifact | Summary |
|---|---|---|---|---|---|
| 1 | banking-reviewer | 2026-05-18 03:42 UTC | REVIEW → SECURITY | [S6 `9e2c4a8b-5f31-4d72-b3a1-7e6c0d9f4b21`](../../artifacts/S6-reviewer-money-transfer.md) | Verdict approved; 0 blocker / 5 major / 13 minor / 5 nit; dominant theme = stub-posture risks (silent stub, hardcoded customer_id, missing OTel, double account call, redundant select-before-save); reviewer flagged 2 specific `known_limitations_concerns` for security attention |

## Produced (downstream)

| # | To | Date | Phase | Artifact | Outcome |
|---|---|---|---|---|---|
| 1 | banking-qa | 2026-05-18 04:05 UTC | SECURITY → QA | [S7 `16792683-0871-4e95-826c-2711bf2a14fc`](../../artifacts/S7-security-money-transfer.md) | Quality gate PASS — verdict **approved**. Findings: **0 critical / 0 high / 3 medium / 7 low / 2 info**. Banking hard rules all green (Money=BigDecimal, no PAN, no PII in logs, idempotency on POST, audit-via-outbox, account masking, sealed error handler, mass-assignment safe) except `no_secrets_in_code=false` (DB_PASSWORD default in YAML, low-severity). STRIDE matrix completed for both endpoints. PCI-DSS scope = OUT (no card data). PDPA / GDPR pass with explicit data-residency follow-ups. |

## Quality Gate Record

| Iteration | Phase Transition | Result |
|---|---|---|
| 1 | SECURITY → QA | PASS — 0 critical, 0 high; all medium findings are deployment-environment-gated (stub/scaffold posture acceptable for local-only v1); all medium + low findings tracked in `must_fix_before_staging` with explicit Spring-profile-guard + startup-fail-fast remediation |

## Open Items / Action Required

10 must-fix-before-staging items handed off — Backend Dev and DevOps own most, but Security tracks them through closure:

- **ITEM-1 [CRITICAL-FOR-DEPLOY]** AccountClientStub gating (`@Profile('!prod & !staging')` + `@ConditionalOnProperty` + startup-failure bean) — owner: Backend Dev, due before US-006.
- **ITEM-2 [CRITICAL-FOR-DEPLOY]** `SecurityConfig.oauth2ResourceServer(jwt)` uncomment + Vault-supplied `issuer-uri` + startup `BeanInitializationException` if missing in staging/prod — owner: Backend Dev, due before US-006.
- **ITEM-3 [CRITICAL-FOR-DEPLOY]** Delete `STUB_CUSTOMER_ID`; use `@AuthenticationPrincipal Jwt` and extract `sub` + `customer_id` claims; throw 401 if absent — owner: Backend Dev, lands atomically with ITEM-2.
- **ITEM-4 [BEFORE-PROD]** Remove `${DB_PASSWORD:transfer_pass}` default; require env var; move local-only defaults to `application-local.yml`; add CI secret-scan gate — owner: Backend Dev + DevOps.
- **ITEM-5 [BEFORE-PROD]** Rate-limit on POST /api/v1/transfers (Bucket4j / Resilience4j `@RateLimiter` 10 req/sec per JWT subject) + `@PreAuthorize` scope check `hasAuthority(SCOPE_transfer.write)` — owner: Backend Dev.
- **ITEM-6 [BEFORE-US-007-RELAY-SHIPS]** `OutboxEventPublisher.headers` populated with `{correlationId, traceparent, eventId}` — owner: Backend Dev, lands with US-007 relay.
- **ITEM-7 [BEFORE-STAGING]** ServletFilter for `MDC.clear()` in finally; integration test asserting MDC empty between requests on same thread — owner: Backend Dev.
- **ITEM-8 [BEFORE-STAGING]** Micrometer-tracing + W3C `traceparent` ServletFilter populating `MDC.traceId` (or rename ProblemDetail key to `correlationId`) — owner: Backend Dev.
- **ITEM-9 [BEFORE-STAGING]** Actuator on internal port 9090; K8s NetworkPolicy restricting Prometheus scrape; swagger-ui disabled in prod profile; K8s topology constraint pinning pods + DB to Thailand region (PDPA data residency) — owner: DevOps.
- **ITEM-10 [BEFORE-PROD]** SCA (Dependabot / OWASP-DependencyCheck) wired in CI; SBOM per build; Trivy/Grype on every image — owner: DevOps in S8/S9.

→ **Re-review triggers**: must re-run security review after US-006 ships (real JWT decoder + real AccountClient + STUB_CUSTOMER_ID removed). Coordinate with Backend Dev iteration counter.

→ **DevOps coordination**: ITEM-9 + ITEM-10 forwarded to DevOps inbox once that agent activates.

## Skills Referenced When Working

- `.claude/skills/banking-security-patterns/SKILL.md` — banking hard rules checklist (no secrets in code, no PII / PAN in logs, no float for money, idempotency on financial POST, audit-for-state-changes via outbox, masked account IDs, JWT RS256, TLS 1.2+, rate-limit on auth, mass-assignment safety), OWASP Top 10 mapping (A01..A09 per finding), STRIDE-per-endpoint matrix template, PDPA / GDPR / PCI-DSS scoping rubric.

## Workflow Hooks

- On S6 from Reviewer → run SAST-mental-model + secret scan + STRIDE per endpoint + banking-hard-rules check + compliance mapping; emit S7 with verdict and findings rated `critical / high / medium / low / info`.
- On `changes_required` verdict (critical / high finding) → ping Backend Dev directly; require re-emit before approving QA handoff.
- On Backend Dev re-iteration (US-006 etc.) → re-scan affected files; close-out matching `must_fix_before_staging` items; emit S7 v2 only if new findings.
- On compliance/regulatory change (e.g., PDPA amendment, BoT new circular) → re-evaluate retention, residency, audit immutability decisions.
