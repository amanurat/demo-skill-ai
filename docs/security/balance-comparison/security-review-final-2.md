# Security Review — balance-comparison (G8 Re-Review, Iteration 2)

**Date:** 2026-05-22
**Reviewer agent:** `banking-security`
**Feature:** `balance-comparison`
**Branch:** `stage/02-balance-comparison`
**Prior verdict:** `changes_requested` (2 blocking findings — F-1 HIGH, F-2 MEDIUM)
**Refactoring iteration:** 3 (final)
**Verdict:** ✅ **`approved`** — all blocking findings resolved; non-blocking findings unchanged.

---

## 1. Executive Summary

Iteration 3 of `banking-refactoring` addressed both blocking PII-in-logs findings from the prior G8 review. Verification of all touched files confirms:

- **F-1 (HIGH, CWE-532, PDPA §22):** All `customerId={}`, `accountId={}`, and Redis `key={}` log placeholders now wrap their value in `LogMasking.maskId(...)` or `LogMasking.maskKey(...)`. Centralized utility lives in `infrastructure.rest.LogMasking` and is covered by dedicated regex-assertion unit tests.
- **F-2 (MEDIUM, defense-in-depth):** `logback-spring.xml` introduced with profile-aware appenders (`dev`, `staging`, `prod`) and regex-replace masking for both full UUID and 10–16 digit PAN-like sequences.

C-2 (audit-event metadata-only) and C-3 (single header reader) invariants are intact — `AuditEventRecord.java` and `IborCheckFilter.java` were not disturbed.

No new findings introduced by the refactor. G8 PASS.

---

## 2. STRIDE Status (unchanged — log masking introduces no new entry points)

| Threat | Entry point | Mitigation | Status |
|---|---|---|---|
| **S**poofing | `GET /api/v1/balance-dashboard` | OAuth2/OIDC + JWT sub-derived customerId (`CustomerIdResolver`); IDOR-safe by construction | ✅ Pass |
| **T**ampering | `X-Customer-Id` header | `IborCheckFilter` cross-checks header vs JWT sub; mismatch → 403 + FORBIDDEN audit; ArchUnit rule prevents stray header reads (single reader = `IborCheckFilter`) | ✅ Pass |
| **R**epudiation | All success / forbidden / error paths | `AuditEventPublisher.publish()` Kafka event at every BR-014 call site; metadata-only payload (C-2) | ✅ Pass |
| **I**nformation Disclosure (Logs) | Logger calls across 4 files | **NEW**: `LogMasking.maskId/maskKey` at all call sites + `logback-spring.xml` regex safety net (F-1 + F-2 fixed) | ✅ Pass |
| **I**nformation Disclosure (Cache) | Redis | `spring.data.redis.ssl.enabled=true`; key prefix-only logging via `maskKey` | ✅ Pass |
| **D**oS | Upstream account-service | Resilience4j: TimeLimiter(outer) → CB → Retry → Bulkhead(inner); cache fail-open returns empty Optional | ✅ Pass |
| **E**levation | Controller authority | `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` on controller (NOT service) | ✅ Pass |

---

## 3. OWASP Top 10 — Updated Checklist

| # | Category | Status | Notes |
|---|---|---|---|
| A01 | Broken Access Control | ✅ Pass | IDOR-safe — customerId derived from JWT sub only; `IborCheckFilter` blocks tampered headers |
| A02 | Cryptographic Failures | ✅ Pass | Redis TLS; JWT RS256 via api-gateway; no service-issued tokens |
| A03 | Injection | ✅ Pass | Typed `UUID` params; no raw SQL; no template rendering on inputs |
| A04 | Insecure Design | ✅ Pass | Hex-arch ports/adapters; metadata-only audit event (C-2) |
| A05 | Security Misconfiguration | ✅ Pass | `Cache-Control: private, no-store`; feature flag default false |
| A06 | Vulnerable Components | ✅ Pass | No new dependencies introduced in refactor |
| A07 | Auth Failures | ⚠️ Non-blocking (F-3) | JWT sessionStorage — XSS-recoverable; ticket BC-DEBT-002 filed |
| A08 | Software & Data Integrity | ✅ Pass | Kafka audit event signed via standard producer config |
| **A09** | **Security Logging & Monitoring Failures** | ✅ **Now Pass** (was Fail in v1) | **F-1 + F-2 fixed — see §4** |
| A10 | SSRF | N/A | No outbound URL constructed from user input |

---

## 4. Verification of Refactor (F-1 + F-2)

### 4.1 F-1 — Call-site PII masking (HIGH → RESOLVED)

**Required artifact:** `infrastructure/rest/LogMasking.java`

| Check | Result |
|---|---|
| `public static String maskId(UUID id)` exists | ✅ line 41 |
| `public static String maskId(String id)` exists | ✅ line 52 |
| `public static String maskKey(String key)` exists | ✅ line 74 |
| No domain-layer imports | ✅ only `java.util.UUID` imported |
| Null-safe (`null` → `"***"`) | ✅ all three methods guard nulls + short strings |
| Idempotent | ✅ asserted by `LogMaskingTest.maskId_idempotent` |

**Call-site verification:**

| File | Lines previously raw | Status after refactor |
|---|---|---|
| `BalanceDashboardController.java` | L75–76 | ✅ `LogMasking.maskId(customerId)` (L76) |
| `BalanceDashboardService.java` | L104–105, L129–130, L138, plus cache.get fail-open | ✅ Wrapped at L106, L131, L139, L155 — every `customerId={}` now passes through `LogMasking.maskId(customerId)` |
| `AccountClientAdapter.java` | L109, L112, L115, L121, L124, L153, L178 | ✅ All 6 catch-block `log.warn` calls and the `balanceAsOf.null` / `parse.failed` logs now use `LogMasking.maskId(...)` — see L110, L113, L116, L122, L125, L154, L179 |
| `RedisCacheRepository.java` | L83, L107, L111 | ✅ `cache.get.failed`, `cache.put`, `cache.put.failed` all use `LogMasking.maskKey(key)` (L84, L108, L112) |

**Test verification:** `src/test/java/com/bank/balancedashboard/unit/LogMaskingTest.java`

- ✅ Exists.
- ✅ Asserts `FULL_UUID_PATTERN` (`[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`) is NOT found in `maskId(UUID)`, `maskId(String)`, and `maskKey` output (3 assertions).
- ✅ Edge cases: null UUID, null String, too-short string, alternate key prefix, idempotency.

### 4.2 F-2 — Logback masking safety net (MEDIUM → RESOLVED)

**Required artifact:** `src/main/resources/logback-spring.xml`

| Check | Result |
|---|---|
| File exists | ✅ 168 lines |
| UUID regex masking (`[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}` → `xxxxxxxx-****`) | ✅ Applied in dev, staging, prod profiles |
| 10–16 digit sequence masking (`\b\d{10,16}\b` → `****-REDACTED`) | ✅ Applied in dev, staging, prod profiles |
| `<springProfile>` separating dev / staging / prod | ✅ Three profile blocks (`dev,default`, `staging`, `prod`) |
| TODO referencing observability-lib `MaskingConverter` | ✅ Comment at lines 12–16 referencing `OBS-042` |
| Prod gates DEBUG | ✅ `com.bank.balancedashboard` set to `INFO` in prod profile (line 157) |

Pattern uses Logback's built-in `<replace>` which does not support back-references; this is documented inline (line 47) as the rationale for full-redaction of digit runs rather than last-4 retention. Acceptable for v1 — operators can still trace via `correlationId` (OTel trace ID).

### 4.3 Invariant Preservation Audit

**C-2 (audit metadata-only) — `domain/audit/AuditEventRecord.java`:**

- ✅ Record fields unchanged: `eventType, actorId, channel, correlationId, timestamp, result, purpose, cacheHit, accountCount`.
- ✅ No `balance`, `accountId`, `accountNumber`, `accounts`, `balanceAsOf`, `currency` re-introduced.
- ✅ Zero Spring/Kafka/Redis imports in this domain class.
- ✅ Factory methods (`success`, `forbidden`, `error`) untouched.

**C-3 (single X-Customer-Id reader) — `infrastructure/rest/IborCheckFilter.java`:**

- ✅ Comment block (L41–43) still declares this as the only permitted `getHeader("X-Customer-Id")` reader.
- ✅ Local private `maskId(String)` helper (L148) retained — intentional encapsulation to avoid coupling filter to `LogMasking`. This is acceptable because the filter pre-dates `LogMasking` and shares identical semantics (first-8 + `***`). Future cleanup tracked but **non-blocking** (cosmetic).
- ✅ ArchUnit `CustomerIdSourceRule` enforcement comment intact.

### 4.4 Final Sweep for Raw PII Log Args

Performed `grep -rn -E '(customerId|accountId|key)\s*=\s*\{\}'` across `src/main/java/`. All four hits are in files that use `LogMasking.maskId(...)` / `LogMasking.maskKey(...)` for the bound argument. No raw bindings remain.

`correlationId={}` log arguments are intentional — `correlationId` is the OTel W3C trace ID (lowercase 32-hex), classified as observability metadata, not PII. Safe to log unmasked.

---

## 5. Compliance

### 5.1 PCI-DSS

- **Scope affected:** ❌ No PAN, no CVV, no cardholder data crosses this service. Account IDs are internal UUIDs, not PANs.
- **Controls met:** N/A — out of scope.

### 5.2 PDPA / GDPR

- **PII handled:** ✅ Yes — `customerId` and `accountId` are personal identifiers under PDPA §6.
- **Lawful basis:** Performance of contract (account ownership) + legitimate interest (security audit).
- **Data minimization:** ✅ C-2 enforces metadata-only audit events; logs now masked via `LogMasking` + logback safety net.
- **Retention:** Logs retained 90 days (DevOps log shipper policy); audit Kafka topic 7 years (regulatory).
- **Consent withdrawal:** Tracked separately under C-1 / F-4 (deferred to v1.1).

---

## 6. Findings Status (all 5)

| ID | Severity | Category | Prior status | Current status | Notes |
|---|---|---|---|---|---|
| **F-1** | HIGH | A09 / CWE-532 (PII in logs) | blocking | ✅ **RESOLVED** | All call sites wrap IDs in `LogMasking.maskId/maskKey`; unit tests assert no full UUID in masked output |
| **F-2** | MEDIUM | A09 / CWE-532 (defense-in-depth) | blocking | ✅ **RESOLVED** | `logback-spring.xml` added with regex masking + profile separation |
| F-3 | MEDIUM | A07 / CWE-922 (sessionStorage JWT) | non-blocking | unchanged | Backlog ticket `BC-DEBT-002` filed for HttpOnly cookie + BFF in v1.1 |
| F-4 | LOW | Privacy / consent UI | non-blocking | unchanged | Deferred post-demo; tracked as C-1 in `early-review-consent-coverage.md` |
| F-5 | INFO | Rate limiting | non-blocking | unchanged | Documented constraint for DevOps P1 NetworkPolicy spec |

No new findings introduced by the refactor.

---

## 7. Scan Summary

| Scan | Result |
|---|---|
| SAST (semantic grep for PII placeholders) | ✅ Clean — zero raw `customerId={}`/`accountId={}`/`key={}` bindings outside `LogMasking` indirection |
| SCA (CVE) | ✅ 0 critical (no dependency changes in refactor) |
| Secrets in code | ✅ None — `git log --all -p \| grep -iE '(password\|secret\|token\|key)\s*='` returns no findings on this branch |
| Container | ✅ Unchanged from prior G8 — no Dockerfile in scope yet (DevOps P2) |
| DAST | ⏭️ Deferred to staging (DevOps P2) |

---

## 8. Final Verdict

✅ **`approved`** — quality gate **G8 PASS**.

**Rationale:**

1. Both blocking findings (F-1 HIGH, F-2 MEDIUM) verified resolved via code-and-test inspection.
2. C-2 and C-3 security invariants preserved (audit metadata-only; single header reader).
3. No new findings introduced by the refactor.
4. Non-blocking findings (F-3 / F-4 / F-5) properly tracked in backlog / documentation.

**Next stage:** Parallel fork to `banking-qa P2` (full automation suite) and `banking-docs` (API ref, CHANGELOG, dev guide, ADR index). Both must complete before `banking-devops P2` deploys to staging.

**Backlog carry-forward:**

- `BC-DEBT-002` — Migrate frontend JWT storage from sessionStorage → HttpOnly cookie + BFF (v1.1)
- `OBS-042` — Ship observability-lib `MaskingConverter`; replace inline `<replace>` regex in `logback-spring.xml`
- `C-1` — Privacy notice / consent UI (deferred to v1.1 per Designer P3)

---

*Generated by `banking-security` agent · G8 iteration 2 · 2026-05-22*
