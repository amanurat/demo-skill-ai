# Outbox Dispatcher — DB → Kafka (safe, idempotent)

The Outbox pattern guarantees **at-least-once delivery** to Kafka without dual-write hazards. The business transaction writes to one DB; a separate dispatcher polls outbox rows and publishes to Kafka.

See companion: [`idempotency-saga-outbox.md`](../../spring-boot-banking/references/idempotency-saga-outbox.md) for the broader saga context.

## Schema (Flyway)

```sql
CREATE TABLE outbox (
  event_id        UUID PRIMARY KEY,
  aggregate_type  VARCHAR(64)  NOT NULL,
  aggregate_id    VARCHAR(64)  NOT NULL,
  event_type      VARCHAR(128) NOT NULL,
  topic           VARCHAR(128) NOT NULL,
  payload         JSONB        NOT NULL,
  headers         JSONB        NOT NULL DEFAULT '{}',
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  attempts        INT          NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  sent_at         TIMESTAMPTZ,
  last_error      TEXT
);

CREATE INDEX outbox_pending_idx
  ON outbox (next_attempt_at)
  WHERE status = 'PENDING';

CREATE INDEX outbox_aggregate_idx
  ON outbox (aggregate_type, aggregate_id);
```

Notes:
- `event_id` is the **producer-supplied dedup key** — consumer side dedups on this
- Partial index on `status='PENDING'` keeps poll cheap as table grows
- Retain SENT rows for a window (7–30 days) for replay / audit, then archive

## Business Transaction (writes Outbox)

```java
@Transactional
public TransferId initiateTransfer(InitiateTransferCommand cmd) {
  // 1. Apply domain change
  Account from = accounts.lockById(cmd.fromAccountId());
  from.debit(cmd.amount());
  accounts.save(from);

  Transfer transfer = Transfer.initiated(cmd);
  transfers.save(transfer);

  // 2. Stage event in Outbox (same tx)
  outbox.save(OutboxEvent.builder()
      .eventId(UUID.randomUUID())
      .aggregateType("Transfer")
      .aggregateId(transfer.id().toString())
      .eventType("TransferInitiated")
      .topic("payments.transfer.initiated.v1")
      .payload(mapper.toAvroJson(transfer))
      .headers(Map.of(
          "X-Request-Id", MDC.get("requestId"),
          "traceparent", currentTraceparent()))
      .build());

  return transfer.id();
}
```

**Hard rule**: never call `kafkaTemplate.send()` inside this transaction. The Outbox row is the durable proof of intent.

## Dispatcher Loop

```java
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

  private final OutboxRepository outbox;
  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final Clock clock;
  private static final int BATCH = 100;
  private static final int MAX_ATTEMPTS = 10;

  @Scheduled(fixedDelayString = "${outbox.dispatch.interval-ms:500}")
  public void dispatch() {
    List<OutboxEvent> batch = outbox.lockNextPending(BATCH, clock.instant());
    if (batch.isEmpty()) return;

    for (OutboxEvent ev : batch) {
      try {
        ProducerRecord<String, byte[]> record = toRecord(ev);
        kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);   // sync, wait for ack
        outbox.markSent(ev.eventId(), clock.instant());
        meter.counter("outbox.dispatch.ok").increment();
      } catch (Exception ex) {
        int attempts = ev.attempts() + 1;
        Instant nextAt = clock.instant().plus(backoff(attempts));
        outbox.markFailed(ev.eventId(), attempts, nextAt, abbrev(ex.getMessage()));
        meter.counter("outbox.dispatch.failed", "topic", ev.topic()).increment();

        if (attempts >= MAX_ATTEMPTS) {
          log.error("outbox event giving up eventId={} topic={}", ev.eventId(), ev.topic(), ex);
          alert.fire(OutboxStuckAlert.of(ev));
        }
      }
    }
  }

  private Duration backoff(int attempts) {
    return Duration.ofSeconds(Math.min(60L * (1L << attempts), 3600L));  // cap 1h
  }
}
```

Repository:

```java
@Query(value = """
    SELECT * FROM outbox
     WHERE status = 'PENDING'
       AND next_attempt_at <= :now
     ORDER BY next_attempt_at
     LIMIT :limit
     FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<OutboxEvent> lockNextPending(@Param("limit") int limit, @Param("now") Instant now);
```

`FOR UPDATE SKIP LOCKED` lets multiple dispatcher pods run in parallel without stepping on each other.

## Multiple Pods — Coordination

| Approach | Pros | Cons |
|---|---|---|
| `FOR UPDATE SKIP LOCKED` (recommended) | Stateless, no leader election | DB load grows with pods |
| Single-instance scheduler (ShedLock) | Lowest DB load | Throughput capped by one pod |
| Debezium CDC → Kafka Connect | Highest throughput, near-realtime | Operational complexity |

For most banking microservices, `SKIP LOCKED` with 2–3 pods is the right balance. Move to Debezium only when proven bottleneck.

## Cleanup

```sql
-- Archive then delete (run nightly)
INSERT INTO outbox_archive SELECT * FROM outbox
 WHERE status = 'SENT' AND sent_at < now() - interval '7 days';
DELETE FROM outbox
 WHERE status = 'SENT' AND sent_at < now() - interval '7 days';
```

## Metrics — must emit

- `outbox.pending.count` (gauge, polled)
- `outbox.dispatch.ok` (counter, tag: topic)
- `outbox.dispatch.failed` (counter, tag: topic)
- `outbox.dispatch.latency` (timer — staged → sent)
- `outbox.stuck.count` (gauge — rows with attempts ≥ N)

Alert on:
- `outbox.pending.count > 1000` for 5 min → dispatcher stuck
- `outbox.dispatch.latency.p95 > 30s` → broker / network issue
- `outbox.stuck.count > 0` → manual intervention needed

## Replay

```sql
UPDATE outbox SET status='PENDING', attempts=0, next_attempt_at=now(), last_error=NULL
 WHERE aggregate_id = :id AND event_type = :type;
```

Combined with consumer-side inbox dedup, replay is safe.

## Common Pitfalls

- Forgetting `.get()` (async send + immediate `markSent`) → message lost on broker failure
- Not setting `event-id` header → consumer can't dedup
- Polling too aggressively (50ms) → DB CPU spike; 200–500ms is usually fine
- Holding the row lock across the Kafka send (use short transaction, `markSent` in own tx if needed)
- Cleanup deletes too aggressive → loses replay capability for incident debugging
