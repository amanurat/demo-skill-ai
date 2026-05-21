# G8 Security Review — balance-comparison

> Agent: banking-security · Gate: G8 Vulnerability Floor

## Verdict: changes_requested

**Blockers:** F-1 (HIGH — PII in logs), F-2 (MEDIUM — missing logback masking filter)
**Non-blocking:** F-3 (accepted with backlog ticket), F-4 (already tracked as C-1), F-5 (informational)

---

## STRIDE per endpoint (GET /api/v1/balance-dashboard)

| Threat | Control | Verified at | Status |
|---|---|---|---|
| **Spoofing** | JWT Bearer + `@PreAuthorize("hasAuthority('SCOPE_accounts:read')")` + RS256 issuer-bound | `SecurityConfig.java:44-46`, `BalanceDashboardController.java:41`, `application.yml:11` | ✅ PASS |
| **Tampering** | `IborCheckFilter` compares `X-Customer-Id` header to JWT sub; `CustomerIdResolver` reads JWT sub only; `CustomerIdSourceRule` ArchUnit + deliberate-violation fixture | `IborCheckFilter.java:79-136`, `CustomerIdResolver.java:37-47`, `CustomerIdSourceRule.java:43-120` | ✅ PASS |
| **Repudiation** | `AuditEventPublisher.publish()` invoked on SUCCESS (cache HIT + MISS), FORBIDDEN, and ERROR paths; BR-014 enforced | `BalanceDashboardService.java:99-102`, `IborCheckFilter.java:108-113` | ✅ PASS |
| **Information Disclosure** | Audit payload restricted to 9 metadata fields; generic problem-detail copy; `Cache-Control: private, no-store`; `accountId` not in DOM | `AuditEventRecord.java`, `ProblemDetailAdvice.java:42-48`, `account-row.component.html` | ⚠️ SEE F-1 (PII in app logs) |
| **Denial of Service** | Resilience4j: TimeLimiter(300ms) + CB + Retry + Bulkhead(20,0ms); ingress rate-limit deferred to NetworkPolicy (DevOps P1) | `AccountClientAdapter.java:96-104`, `application.yml:86-126` | ✅ PASS |
| **Elevation of Privilege** | JWT sub sole `customerId` source; ArchUnit enforces `getHeader("X-Customer-Id")` callable only by `IborCheckFilter`; violation fixture confirms rule fires | `CustomerIdResolver.java`, `CustomerIdSourceRule.java:108-120` | ✅ PASS |

---

## OWASP Top 10 (2021)

| # | Category | Status |
|---|---|---|
| A01 Broken Access Control | ✅ PASS — IDOR defense via IborCheckFilter + JWT-only customerId + ArchUnit |
| A02 Cryptographic Failures | ✅ PASS — Redis TLS ssl.enabled=true all 3 profiles; RS256 JWT; at-rest deferred to ASSUMPTION-TL-004 |
| A03 Injection | ✅ PASS — no SQL; Redis key uses UUID type (injection impossible); no shell exec |
| A04 Insecure Design | ✅ PASS — balance as decimal-string; double-submit protection on retry; feature flag prod=false |
| A05 Security Misconfiguration | ✅ PASS — CSRF disabled+documented; STATELESS; actuator health.show-details=never |
| A06 Vulnerable Components | ⏭ Out of scope — defer to SCA scan in CI (DevOps P2) |
| A07 Identification/Auth Failures | ✅ PASS — @PreAuthorize at class level; UUID validation in CustomerIdResolver |
| A08 Software & Data Integrity | ✅ PASS — Kafka idempotence=true; Apicurio Avro schema enforced; contract test exists |
| A09 Security Logging Failures | ❌ FAIL — see F-1 (unmasked PII in logs) and F-2 (missing masking filter) |
| A10 SSRF | ✅ PASS — single upstream call (AccountClientLib); no user-controlled URL targets |

---

## Findings

### F-1 — PII in application logs — HIGH (BLOCKING)

**OWASP:** A09:2021 · **CWE-532** Information Exposure Through Log Files · **PDPA §22 / GDPR Art. 5(1)(c)**

**Files / lines:**
- `BalanceDashboardController.java:75-76` — `log.debug("... customerId={} ...", customerId, ...)`
- `BalanceDashboardService.java:104-105, 129-130, 138` — three log.debug/warn calls printing full customerId UUID
- `AccountClientAdapter.java:109, 112, 115, 121, 124, 153, 178` — six occurrences logging `customerId={}` and `accountId={}` at WARN level
- `RedisCacheRepository.java:83, 107, 111` — logs full cache key (contains customerId UUID)

**Impact:** Anyone with SIEM/Loki read access can enumerate active customer IDs, correlate to forbidden/error events.

**Note:** `IborCheckFilter` already demonstrates the correct pattern (maskId to first 8 chars). Extract this to a shared utility.

**Fix:**
1. Extract `maskId(UUID id)` from `IborCheckFilter` to `infrastructure.rest.LogMasking` utility class
2. Replace all `customerId={}` / `accountId={}` / `key={}` log arguments with `LogMasking.maskId(customerId)`
3. For RedisCacheRepository, log only the key prefix (not the full UUID suffix)
4. Add unit test asserting no log statement produces unmasked UUID pattern

### F-2 — Missing logback-spring.xml masking filter — MEDIUM (BLOCKING)

**OWASP:** A09:2021 · **CWE-532**

**File:** `backend/balance-dashboard-service/src/main/resources/logback-spring.xml` — **does not exist**

**Impact:** No defense-in-depth against future developers adding PII log statements. Implementation-notes §1 (module layout) references `logback-spring.xml` with "reuses observability-lib JSON encoder + masking filter".

**Fix:** Create `logback-spring.xml` with a Logback pattern converter that masks UUID-shaped strings to `xxxxxxxx-****` and 10-16 digit sequences to `****<last-4>`. Reference the observability-lib masking filter.

### F-3 — JWT in sessionStorage (XSS-recoverable) — MEDIUM (NON-BLOCKING, backlog)

**OWASP:** A07:2021 · **CWE-922**

**File:** `frontend/src/app/services/auth.service.ts:22-23, 46-47`

`sessionStorage` is an improvement over `localStorage` (R-FE-003 fix) but still readable by same-origin XSS. No XSS sinks present in this feature (no `innerHTML`, no `bypassSecurityTrust*`, no `eval`). Acceptable for demo; file v1.1 backlog ticket for HttpOnly cookie + BFF migration.

**Action:** Create backlog item `BC-DEBT-002: Migrate auth token to HttpOnly cookie + BFF` — non-blocking.

### F-4 — Privacy notice / consent UI — LOW (already tracked as C-1)

Deferred post-demo. Purpose limitation documented in every audit event (`purpose: "balance-inquiry"`). No new action required for G8.

### F-5 — Rate-limiting at ingress, not app layer — INFO

Future rate-limiting MUST be implemented at ingress (NGINX/Istio) on authenticated JWT `sub` claim, NOT on `X-Forwarded-For` at application layer. Document this constraint in DevOps P1 NetworkPolicy spec.

---

## PCI-DSS

No card data in scope. Deposit accounts only. No PAN, CVV, or card number anywhere in source. Masked account number (`^\*+\d{4}$`) pattern already in place.

## Secrets scan

All sensitive values in all `application*.yml` files use `${ENV_VAR}` placeholder pattern. No hardcoded credential literals found.
**Note:** Full branch secrets scan (gitleaks/trufflehog) should run in CI pipeline (DevOps P2) — cannot grep git history in static review.

---

*banking-security · 2026-05-21 · G8 gate*
