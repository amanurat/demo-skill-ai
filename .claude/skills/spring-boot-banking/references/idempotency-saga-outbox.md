# Idempotency, Saga, Outbox — Distributed Transaction Patterns

Reference loaded on demand by `spring-boot-banking` skill. The three patterns every banking service implements to handle money correctly under failure.

## 1. Idempotency-Key

### Why
Network retries, client double-clicks, and timeouts are inevitable. Without an idempotency mechanism, the same logical transfer can debit twice.

### Required For
- All **financial POST/PUT** endpoints (`/transfers`, `/payments`, `/withdrawals`, etc.)
- Any state-changing operation where the client may retry

### Contract
- **Header**: `Idempotency-Key: <uuid-v4>`
- **Required** — reject `400 Bad Request` if missing on financial endpoints
- **TTL**: 24 hours (per `docs/artifacts/S2-ba-money-transfer.json` decision)
- **Scope**: per-customer (key + customer_id, not global)

### Storage
Each service owns a `<service>_idempotency` table:

```sql
CREATE TABLE transfer_idempotency (
    key_hash             VARCHAR(64) PRIMARY KEY,   -- SHA-256 of raw key
    owner_customer_id    UUID NOT NULL,
    transfer_id          UUID,
    request_checksum     VARCHAR(64) NOT NULL,      -- SHA-256 of request body
    cached_response_code SMALLINT NOT NULL,
    cached_response_body JSONB NOT NULL,
    result_status        VARCHAR(20) NOT NULL,      -- PENDING/COMPLETED/FAILED/REJECTED
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL
);
```

### Behavior Matrix
| Scenario | Response |
|---|---|
| Key + checksum match, status COMPLETED | Return cached response (200, `idempotency_status: IDEMPOTENT_REPLAY`) |
| Key + checksum match, status REJECTED | Return cached rejection (422, original error_code) |
| Key matches, checksum differs | `409 Conflict` — `IDEMPOTENCY_KEY_CONFLICT` |
| Key not found OR expired (> 24h) | Process as new request |

### Implementation Sketch
```java
@PostMapping("/transfers")
public ResponseEntity<TransferResponse> create(
        @RequestHeader("Idempotency-Key") @NotBlank String key,
        @RequestBody @Valid TransferRequest req,
        Principal principal) {

    var keyHash = sha256(key);
    var checksum = sha256(req);

    return idempotencyService.findValid(keyHash, principal.getCustomerId())
        .map(cached -> {
            if (!cached.checksum().equals(checksum)) {
                throw new IdempotencyKeyConflictException();
            }
            return ResponseEntity.status(cached.responseCode()).body(cached.body());
        })
        .orElseGet(() -> {
            var result = transferUseCase.execute(req, principal);
            idempotencyService.store(keyHash, checksum, result, Duration.ofHours(24));
            return ResponseEntity.ok(result);
        });
}
```

---

## 2. Saga Pattern (Orchestration)

### Why
A money transfer touches multiple services (account, ledger, audit, notification). Distributed 2PC is unavailable. Saga lets you compose long-running operations with explicit compensation when later steps fail.

### Choice: Orchestration vs Choreography
For banking transfers, **prefer orchestration** — the coordinator owns the state machine:
- Clearer compensation order
- Easier to audit and debug
- Single place to apply timeouts and retries

### State Machine
```
PENDING → DEBITED → CREDITED → COMPLETED
              ↓
        CREDIT_FAILED → COMPENSATION_TRIGGERED → FAILED_COMPENSATED
                                              ↓
                                         COMPENSATION_FAILED  (ops alert)
```

### Coordinator Pattern
- `application/saga/TransferSaga.java` holds the state machine
- Each step is **idempotent** (can re-run safely)
- Saga state persisted in `transfer_saga_state` table — survives restart
- Compensation actions explicit per step

### Compensation Rules
| Step that failed | Compensation |
|---|---|
| `debit-source` | None (no state changed) |
| `credit-destination` | Re-credit source for exact debited amount |
| `emit-event` (already debited + credited) | Retry event emission; if exhausted, ops alert (don't reverse the money) |

### Retry & Timeout
- Each step: retry 3× with exponential backoff (1s, 2s, 4s)
- Saga timeout: 30s end-to-end; if exceeded, trigger compensation
- Compensation retry: 5× over 10 min; if all fail, status `COMPENSATION_FAILED` + page on-call

---

## 3. Transactional Outbox

### Why
You cannot atomically write to PostgreSQL AND publish to Kafka in the same transaction. Naive approaches lose events on crash. Outbox solves this by writing the event to a DB table in the same transaction as the state change, then publishing asynchronously.

### Pattern
```
┌─ Same DB transaction ─────────────────────┐
│ INSERT/UPDATE business_table             │
│ INSERT outbox (event_type, payload, ...) │
└──────────────────────────────────────────┘
                ↓
        [outbox-publisher poller]
                ↓
        Kafka topic
                ↓
        Mark outbox row as PUBLISHED
                ↓
        (optional) Delete after N days
```

### Outbox Table
```sql
CREATE TABLE outbox (
    event_id      UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL,
    headers       JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING/PUBLISHED/FAILED
);

CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE status = 'PENDING';
```

### Publisher
Either:
- **In-process scheduled poller** — `@Scheduled(fixedDelay = 500)` reading unpublished rows
- **Debezium / CDC** — reads Postgres WAL, more robust at scale
- **Transactional Outbox library** (e.g. Apache Camel, Eventuate Tram) for built-in support

### Consumer Side
Because outbox guarantees **at-least-once** delivery:
- Consumers MUST be idempotent (use the event's `event_id` as dedup key)
- Use Kafka transactions on the consumer side when emitting downstream events

### Schema Evolution
- Avro or Protobuf schemas registered in Confluent/Apicurio schema registry
- Backward-compatible changes only without bumping version
- Versioned event names: `TransferCompleted.v1`, `TransferCompleted.v2`

---

## When to Use This Reference

- Adding a new financial endpoint (idempotency is mandatory)
- Designing a multi-service operation (saga)
- Emitting domain events that other services consume (outbox)
- Debugging a "double-debit" or "lost event" incident
- Reviewing a PR that touches money flows
