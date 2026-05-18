# STRIDE Threat Model — Banking Lens

Reference loaded on demand by `banking-security-patterns` skill. Apply per new endpoint or per material change to an existing one.

## Severity Legend
- **blocker** — must mitigate before merge (no exceptions for money/auth paths)
- **major** — must mitigate or document compensating control before merge
- **minor** — track in backlog; OK to defer if risk accepted
- **info** — awareness only

## When to Apply

- Every new HTTP endpoint, Kafka topic consumer, or scheduled job
- Every change to an existing endpoint that alters auth, input shape, or side effects
- Every new external integration (third-party API, vendor callback)
- As a pre-implementation step during design review — not only at code review

## STRIDE Table

| Threat | Question | Mitigation Examples | Banking Examples |
|---|---|---|---|
| **S**poofing | Can someone pretend to be another user? | Strong auth, mTLS, JWT validation | Forged JWT for another customer's account; stolen API key for a partner bank callback; SIM-swap bypass on OTP |
| **T**ampering | Can data be modified in flight or at rest? | TLS, signatures, write-once audit, optimistic locking | Modifying transfer amount mid-request; editing a posted ledger entry; race on balance update without `@Version` |
| **R**epudiation | Can a user deny an action? | Audit trail with signed events, idempotency keys | Customer denies initiating a transfer; missing audit event for a fee waiver; operator denies a manual adjustment |
| **I**nformation disclosure | What sensitive data leaks? | Encryption, masking, PII handling, log redaction | Card PAN in DEBUG log; full account number in error response; PII in URL query string captured by access logs |
| **D**enial of service | Can throughput be overwhelmed? | Rate-limit, circuit breaker, bulkhead | Brute-force login on auth endpoint; expensive search query without paging; downstream core-banking timeout cascading |
| **E**levation of privilege | Can a low-priv user do high-priv ops? | RBAC, method-level `@PreAuthorize`, principle of least privilege | Customer role calling an admin reversal endpoint; teller approving own transaction; mass-assignment escalating to `role=ADMIN` |

## Per-Threat Apply Notes

### Spoofing
- Validate JWT signature with RS256 public key from JWKS; reject `alg: none` and `alg: HS256` in prod
- Require mTLS for service-to-service and partner-bank callbacks
- Bind sessions to device fingerprint where regulator permits

### Tampering
- Use optimistic locking (`@Version`) on all money-bearing entities
- Sign audit events; store append-only with hash-chain where high-assurance is required
- TLS 1.3 in transit; AES-256-GCM at rest for PII columns

### Repudiation
- Emit an audit event for every state-changing financial op (transfer, reversal, fee, limit change)
- Idempotency-Key on financial POST/PUT — caller cannot deny by replaying with different intent
- Capture actor (user / system), source IP, correlation ID, and immutable timestamp

### Information disclosure
- Log redaction filter — block PAN, CVV, full account number, JWT, password, OTP, national ID
- Mask in API responses unless caller is explicitly entitled
- Never put PII in URL paths or query strings

### Denial of service
- Rate-limit on auth + high-cost endpoints (Resilience4j / gateway)
- Circuit breaker on downstream calls; bulkhead pools per dependency
- Pagination required on list endpoints; max page size enforced

### Elevation of privilege
- `@PreAuthorize` on application services, not only controllers
- Avoid binding request body directly to JPA entity — use DTO
- Deny-by-default; explicit allow per role
