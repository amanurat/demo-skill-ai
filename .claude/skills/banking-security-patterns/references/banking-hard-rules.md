# Banking Hard Rules — Auto-Fail

Reference loaded on demand by `banking-security-patterns` skill. These rules flip a security review verdict to `changes_requested` immediately. No exceptions.

## Severity Legend
- All rules below are **blocker** — auto-fail. There is no "major" or "minor" tier for hard rules.

## Rules

### 1. Secret hardcoded or in version control
- **Why**: Audit + leak risk; once committed, considered compromised forever. Violates PCI-DSS req 8 / GDPR art 32.
- **How to detect**: `git log -p` scan for high-entropy strings; gitleaks / trufflehog in CI; grep for `password=`, `apiKey=`, `BEGIN PRIVATE KEY` in repo and ConfigMaps; check `application*.yml` for non-`${VAULT_...}` values on sensitive keys.

### 2. PII / card number in logs (even debug)
- **Why**: PCI-DSS req 3.3 (mask PAN), GDPR art 5(1)(c) data minimization, PDPA equivalent. DEBUG logs end up in centralized logging with broader access.
- **How to detect**: grep for `log.*\\.(debug|info|warn|error).*(card|pan|cvv|password|otp|nationalId|fullName)`; review log-redaction filter config; sample real log output in staging.

### 3. JWT in `localStorage`
- **Why**: XSS-readable; any injected script can exfiltrate session. OWASP A07.
- **How to detect**: grep frontend for `localStorage.setItem.*token`, `localStorage.getItem.*token`; confirm tokens stored only in `httpOnly` + `Secure` + `SameSite` cookies.

### 4. Plaintext password in storage
- **Why**: Any DB leak instantly compromises all customer credentials. Violates PCI-DSS req 8.2.1.
- **How to detect**: Inspect user table schema — password column must be hash output (bcrypt/argon2id). Search code for `password = ` writes without `passwordEncoder.encode(...)`.

### 5. Money in `float` / `double`
- **Why**: Binary float cannot exactly represent decimal amounts; rounding errors accumulate; legally wrong. Customers and auditors will notice.
- **How to detect**: grep entities and DTOs for `private (float|double|Float|Double)\\s+(amount|balance|fee|interest|principal)`; check Flyway migrations for `FLOAT`/`DOUBLE` columns on money tables. Must be `BigDecimal` in Java and `NUMERIC(p,s)` in Postgres.

### 6. Missing audit event for state-changing op
- **Why**: Repudiation risk (STRIDE-R); compliance violation; cannot reconstruct who-did-what for a disputed transaction.
- **How to detect**: For every `@PostMapping` / `@PutMapping` / `@DeleteMapping` on financial resources, trace into use-case layer and verify an `AuditEvent` is emitted via Outbox. Check `audit_event` table receives one row per state change in integration tests.

### 7. Missing idempotency on financial POST/PUT
- **Why**: Network retries cause double-debit / duplicate transfers; client SDKs retry by default.
- **How to detect**: For every financial mutating endpoint, controller must require `Idempotency-Key` header and use-case must check/store result keyed on it. Integration test: same request + same key returns same response without side effect.

### 8. HS256 JWT in prod
- **Why**: Shared secret — if config or any verifying service leaks, all tokens can be forged by anyone holding the secret. RS256 separates signing (private) from verification (public).
- **How to detect**: Check Spring Security `JwtDecoder` config; reject `MacAlgorithm.HS*` for prod profile. Inspect token header `alg` in staging — must be `RS256` (or `ES256`).

### 9. TLS < 1.2 anywhere
- **Why**: TLS 1.0/1.1 deprecated; vulnerable to BEAST, POODLE, and protocol-downgrade. PCI-DSS req 4.1.
- **How to detect**: Ingress / gateway / Service Mesh config — `minProtocolVersion: TLSv1_2` (prefer TLSv1_3). Scan endpoints with `testssl.sh` or `sslyze` in CI.

### 10. Unauthenticated endpoint exposing customer data
- **Why**: Direct PII / financial data leak; immediate regulator + reputation hit.
- **How to detect**: Audit Spring Security config — `permitAll()` paths must be enumerated and reviewed. Every `/api/**` path must require authentication unless explicitly justified (e.g., `/api/v1/health`).

### 11. Mass-assignment vulnerability (e.g., `bind(*)` to entity)
- **Why**: Attacker sets fields like `role`, `accountId`, `balance`, or `verified` by including them in the request body. Privilege escalation / data tampering.
- **How to detect**: Controllers must accept a DTO (not entity); DTO must declare only the fields the client may set. Reject controllers that take `@RequestBody UserEntity user` or use `@ModelAttribute` on JPA entities.
