# Review Checklist (Backend)

### Architecture / Design
- [ ] Hexagonal layers respected (no JPA in interfaces, no controllers in domain)
- [ ] Domain logic in entities/VOs, not anemic services
- [ ] No service-to-service synchronous chain > 2
- [ ] Bounded context boundaries respected
- [ ] Idempotency-Key handled for financial endpoints
- [ ] Outbox pattern for events (not direct Kafka send in tx)
- [ ] Saga steps have explicit compensations

### Code Quality
- [ ] SOLID applied (especially SRP, DIP)
- [ ] Constructor injection (not field)
- [ ] DTO ↔ Entity mapping via MapStruct (no JPA leaks)
- [ ] Money as `BigDecimal` (not `double`)
- [ ] `BigDecimal.equals` vs `compareTo` used correctly
- [ ] Exceptions specific (not bare `Exception`)
- [ ] Optional used at API boundary, not as fields
- [ ] Null checks reasoned (or `@NonNull` annotation)

### Tests
- [ ] Unit coverage ≥ 80% (≥ 95% for money paths)
- [ ] Integration tests with real Postgres + Kafka (Testcontainers)
- [ ] Test names describe behavior, not method
- [ ] Edge cases tested (insufficient, duplicate, daily limit, partial failure)
- [ ] No `@MockBean` of class under test

### Security / Banking Hard Rules
- [ ] No hardcoded secrets
- [ ] No PII / card numbers / JWTs in logs
- [ ] No `String.format`-built SQL
- [ ] Input validated with Bean Validation
- [ ] Auth annotation on every sensitive endpoint

### Observability
- [ ] Logger uses structured fields (no `+` concat)
- [ ] Correlation ID propagated via MDC
- [ ] Custom business metrics added
- [ ] OTel spans on key operations
