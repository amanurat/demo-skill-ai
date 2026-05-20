# Fallback — Backend Implementation Summary

> **Use only if `banking-backend-dev` fails in Act 4**
> Paste / open this on screen to recover demo flow

---

## What this would have produced

`backend/transaction-query-service/` หรือ extend ของ `backend/transfer-service/`:

### Java Files (target ~15)

```
src/main/java/com/example/transactionquery/
├── api/
│   ├── TransactionController.java        # @RestController + @GetMapping
│   ├── TransactionDto.java               # Response record (masked counterparty)
│   ├── TransactionFilterRequest.java     # Query params binding
│   └── advice/ProblemDetailHandler.java  # RFC 7807 error responses
├── application/
│   ├── TransactionQueryService.java      # @Service + @Transactional(readOnly=true)
│   └── TransactionMaskingService.java    # Mask account number per PDPA
├── domain/
│   └── Transaction.java                  # JPA entity (read-only view)
├── infrastructure/
│   ├── TransactionRepository.java        # JpaRepository + JpaSpecificationExecutor
│   ├── TransactionSpecification.java     # Specification builder (from/to/type)
│   └── security/JwtCustomerIdResolver.java # Extract customer_id from JWT (NO query param)
└── config/
    └── SecurityConfig.java               # JWT validation + rate limit filter
```

### Tests (target ~12)

```
src/test/java/.../
├── TransactionQueryServiceTest.java          # Unit (8 cases)
│   - happy path returns paged
│   - empty result
│   - date range invalid
│   - customer_id from JWT (not query)
│   - large dataset uses index
│   - sort order applied
│   - mask applied
│   - readOnly transaction asserted
├── TransactionControllerWebMvcTest.java      # WebMvc slice (4 cases)
└── TransactionQueryIntegrationTest.java      # Testcontainers (PostgreSQL)
```

### Key Snippets

**Controller (idempotent read endpoint):**
```java
@GetMapping("/api/v1/transactions")
public ResponseEntity<Page<TransactionDto>> list(
    @AuthenticationPrincipal Jwt jwt,
    @Valid TransactionFilterRequest filter,
    @PageableDefault(size = 20, sort = "executedAt", direction = DESC) Pageable pageable
) {
    String customerId = jwt.getSubject();           // ← from JWT, never from query
    return ResponseEntity.ok(service.list(customerId, filter, pageable));
}
```

**Specification (no N+1, indexed query):**
```java
@Transactional(readOnly = true)
public Page<TransactionDto> list(String customerId, TransactionFilterRequest f, Pageable p) {
    Specification<Transaction> spec = Specification
        .where(byCustomerId(customerId))
        .and(byDateRange(f.from(), f.to()))
        .and(byType(f.type()));
    return repository.findAll(spec, p).map(maskingService::toDto);
}
```

**Masking (PDPA-compliant):**
```java
String maskAccount(String accountNumber) {
    if (accountNumber == null || accountNumber.length() < 4) return "***";
    String last4 = accountNumber.substring(accountNumber.length() - 4);
    return "xxx-x-x" + last4 + "-x";
}
```

---

## Talking points (60 sec)

1. "Controller อ่าน customer_id จาก JWT.sub — ไม่ใช่ query param. ป้องกัน IDOR ทันที"
2. "Service ใช้ @Transactional(readOnly=true) — connection pool ใช้ replica ได้, lock น้อย"
3. "Specification builder รวม filter — query ออกมาเดี่ยวๆ ใช้ composite index (customer_id, executed_at DESC)"
4. "Masking ทุก response — counterparty account ไม่หลุดเลข 10 ตัวจริง"
5. "Test 12 cases รวม Testcontainers จริง — ไม่ใช่ mock"

---

## Comparison vs Money Transfer

| | View History | Money Transfer |
|---|---|---|
| Files | ~15 | 54 |
| Tests | ~12 | 40 |
| Saga | ไม่มี | มี (3 steps) |
| Kafka | ไม่มี (อาจมี view event) | 14 events |
| Idempotency-Key | ไม่ต้อง (read) | บังคับ |
| Complexity | Low | High |
