# Producer Patterns — `spring-kafka` 3.x

## Baseline Producer Factory (idempotent + transactional capable)

```java
@Configuration
public class KafkaProducerConfig {

  @Bean
  public ProducerFactory<String, SpecificRecord> producerFactory(KafkaProperties props) {
    Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
    cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

    // Banking hard rules
    cfg.put(ProducerConfig.ACKS_CONFIG, "all");
    cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    cfg.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

    // Throughput vs latency — pick per topic
    cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
    cfg.put(ProducerConfig.LINGER_MS_CONFIG, 10);
    cfg.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);

    // Delivery timeout > total retry budget
    cfg.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
    cfg.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);

    return new DefaultKafkaProducerFactory<>(cfg);
  }

  @Bean
  public KafkaTemplate<String, SpecificRecord> kafkaTemplate(ProducerFactory<String, SpecificRecord> pf) {
    return new KafkaTemplate<>(pf);
  }
}
```

## Why these settings

| Setting | Why |
|---|---|
| `acks=all` | Wait for ISR ack — no data loss on broker failure |
| `enable.idempotence=true` | Broker dedupes by producer-id + seq → no duplicates on retry |
| `max.in.flight.requests.per.connection ≤ 5` | Required by idempotent producer to preserve ordering |
| `retries=Integer.MAX_VALUE` | Bounded by `delivery.timeout.ms`, not retry count |
| `compression.type=zstd` | Best CPU/ratio tradeoff for JSON/Avro payloads |
| `linger.ms=10` + `batch.size=32K` | Batch sends → 5–10× throughput |

## Sync vs Async Send

**Use sync (`.get()`) when**:
- Outbox dispatcher — must know result to mark row SENT
- Audit / compliance event — must not be lost silently

**Use async (callback) when**:
- High-throughput non-critical events
- Fire-and-forget metrics

```java
// Sync — outbox dispatcher
SendResult<String, SpecificRecord> result =
    kafkaTemplate.send(record).get(5, TimeUnit.SECONDS);

// Async — with callback
kafkaTemplate.send(record).whenComplete((result, ex) -> {
  if (ex != null) {
    log.error("send failed topic={} key={}", record.topic(), record.key(), ex);
    metrics.counter("kafka.send.failed").increment();
  }
});
```

## Producer Headers — banking standard

Always set:

```java
ProducerRecord<String, SpecificRecord> record = new ProducerRecord<>(
    topic, partition, key, payload);
record.headers()
    .add("X-Request-Id", requestId.getBytes(UTF_8))
    .add("traceparent", currentTraceparent().getBytes(UTF_8))
    .add("event-id", eventId.toString().getBytes(UTF_8))
    .add("event-version", "1".getBytes(UTF_8))
    .add("schema-id", schemaId.getBytes(UTF_8));
```

- `event-id` enables consumer-side dedup (inbox table)
- `traceparent` flows through OTel auto-instrumentation for spring-kafka

## Partitioning

| Goal | Partition key |
|---|---|
| Per-account ordering | `accountId` |
| Per-customer ordering | `customerId` |
| Per-saga ordering | `sagaId` (= transferId) |
| Even spread, no ordering | `UUID.randomUUID()` or `null` (round-robin) |

**Never** use timestamp or sequential ID as partition key — creates hot partitions.

## Transactions (only when needed)

Use Kafka transactions only when you need **atomic publish across multiple topics** (rare). For DB+Kafka atomicity, use **Outbox**, not Kafka transactions (chained transactions are fragile and slow).

```java
@Bean
public KafkaTransactionManager<String, SpecificRecord> kafkaTxManager(
    ProducerFactory<String, SpecificRecord> pf) {
  pf.transactionCapable();  // requires transactional.id prefix
  return new KafkaTransactionManager<>(pf);
}
```

Set `transactional.id.prefix` per instance (e.g., `tx-${spring.application.name}-${HOSTNAME}-`) to avoid producer fencing across pods.

## Error Callbacks — must observe

```java
kafkaTemplate.setProducerListener(new ProducerListener<>() {
  @Override
  public void onError(ProducerRecord<String, SpecificRecord> rec, RecordMetadata md, Exception ex) {
    log.error("kafka send failed topic={} key={}", rec.topic(), rec.key(), ex);
    metrics.counter("kafka.producer.errors", "topic", rec.topic()).increment();
  }
});
```

## Common Pitfalls

- Forgetting `.get()` on outbox path → message dropped, outbox row stuck SENT
- Setting `max.in.flight > 5` with idempotence → `ConfigException`
- Mixing transactional and non-transactional producers in same app
- Logging full `payload.toString()` → leaks PII / money values
