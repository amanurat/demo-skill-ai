# OWASP Top 10 (2021) — Banking Lens

Reference loaded on demand by `banking-security-patterns` skill. Run this checklist on every PR that touches auth, money, PII, or external integrations.

## Severity Legend
- **blocker** — auto-fail; cannot merge
- **major** — must fix or document compensating control
- **minor** — track and fix in next iteration

## Table

| ID | Check | Concrete Code Example / Mitigation |
|---|---|---|
| **A01** Broken Access Control | Every endpoint authz tested; horizontal + vertical | `@PreAuthorize("hasRole('CUSTOMER') and #accountId == authentication.principal.accountId")` on `/accounts/{accountId}/transfers`. Test that customer A cannot read customer B's account (horizontal); customer cannot call admin-only endpoint (vertical). |
| **A02** Crypto Failures | RS256 JWT, TLS 1.3, AES-GCM at rest, no MD5/SHA1, no static IV | JWT validator configured with JWKS URL + `RS256`; reject `alg: none`/`HS256`. Postgres column encryption via pgcrypto + AES-256-GCM with random IV per row. Never `MessageDigest.getInstance("MD5")` for anything security-relevant. |
| **A03** Injection | JPA parameterized only; no `String.format` in queries; input validated | `@Query("select a from Account a where a.id = :id")` — never `String.format("... where id = %s", id)`. Validate request DTOs with `@Valid` + Jakarta Bean Validation. |
| **A04** Insecure Design | Threat model exists; rate limits; idempotency | STRIDE table completed before coding (see [stride-threat-model.md](stride-threat-model.md)). Rate limit on `/auth/login`, `/auth/otp`, `/transfers`. `Idempotency-Key` header required on all financial POST/PUT. |
| **A05** Security Misconfig | Actuator hardened; CORS strict; no debug in prod | `management.endpoints.web.exposure.include=health,info` only; secure `/actuator/**` behind separate port + auth. CORS allow-list per environment, never `*`. `logging.level.root=INFO` in prod. |
| **A06** Vulnerable Components | SCA scan; pinned versions; SBOM published | Dependabot / Renovate enabled; OWASP Dependency-Check or Snyk in CI breaks build on `critical`/`high`. Generate SBOM (CycloneDX) and publish on each release. |
| **A07** Auth Failures | Lockout, MFA on high-risk, password rules, session mgmt | Account lockout after N failures with exponential backoff. MFA required for transfers > threshold, limit changes, device enrollment. Password rules per NIST SP 800-63B. Refresh tokens rotated + revocable. |
| **A08** Software/Data Integrity | Signed JARs, signed events (audit), dependency provenance | Build artifacts signed (Sigstore / GPG); verified at deploy. Audit events signed with service key; downstream consumers verify. Dependencies pulled from internal mirror with provenance checks. |
| **A09** Logging Failures | Structured logs, correlation IDs, no PII, retention met | JSON logs with `traceparent` + `X-Request-Id` propagated via MDC. PII redaction filter applied at appender level. Log retention ≥ 1 year online, ≥ 7 years archived for financial events. |
| **A10** SSRF | URL allow-list, no user-controlled redirects | Outbound HTTP client wrapped — host must match allow-list (partner banks, vendors). Never accept a user-supplied URL for server-side fetch. Disable HTTP redirects or restrict to same host. |
