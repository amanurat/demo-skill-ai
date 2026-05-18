# Consumer Patterns — `spring-kafka` 3.x

## Baseline Consumer Factory

```java
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public ConsumerFactory<String, SpecificRecord> consumerFactory(KafkaProperties props) {
    Map<String, Object> cfg = new HashMap<>(props.buildConsumerProperties());
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
    cfg.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

    // Banking hard rules
    cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    cfg.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    // Tune to throughput / latency requirement
    cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
    cfg.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
    cfg.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45_000);
    cfg.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15_000);

    return new DefaultKafkaConsumerFactory<>(cfg);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> kafkaListenerContainerFactory(
      ConsumerFactory<String, SpecificRecord> cf,
      KafkaTemplate<String, SpecificRecord> template) {

    ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(cf);
    factory.setConcurrency(3);  // = container threads per @KafkaListener
    factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
    factory.getContainerProperties().setObservationEnabled(true);  // OTel
    factory.setCommonErrorHandler(errorHandler(template));
    return factory;
  }

  @Bean
  public DefaultErrorHandler errorHandler(KafkaTemplate<String, SpecificRecord> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1_000L);
    backoff.setMultiplier(2.0);
    backoff.setMaxInterval(10_000L);
    var handler = new DefaultErrorHandler(recoverer, backoff);

    // Poison messages → DLT immediately, no retry
    handler.addNotRetryableExceptions(
        DeserializationException.class,
        SchemaMismatchException.class,
        IllegalArgumentException.class);

    return handler;
  }
}
```

## Listener — manual ack pattern

```java
@Component
@RequiredArgsConstructor
public class TransferInitiatedListener {

  private final TransferSagaOrchestrator orchestrator;
  private final InboxRepository inbox;

  @KafkaListener(
      topics = "payments.transfer.initiated.v1",
      groupId = "transfer-saga",
      containerFactory = "kafkaListenerContainerFactory")
  public void onTransferInitiated(
      @Payload TransferInitiated event,
      @Header(name = "event-id") String eventId,
      @Header(name = "X-Request-Id") String requestId,
      Acknowledgment ack) {

    MDC.put("requestId", requestId);
    MDC.put("eventId", eventId);
    try {
      if (inbox.exists(UUID.fromString(eventId))) {
        log.info("dedup skip eventId={}", eventId);
        ack.acknowledge();
        return;
      }
      orchestrator.handle(event);
      inbox.markProcessed(UUID.fromString(eventId));
      ack.acknowledge();
    } finally {
      MDC.clear();
    }
  }
}
```

## Key Rules

| Rule | Why |
|---|---|
| `enable.auto.commit=false` | Avoid committing before processing finishes (message loss on crash) |
| `AckMode.MANUAL_IMMEDIATE` | Explicit ack after success — pairs with consumer idempotency |
| `isolation.level=read_committed` | Skip aborted transactional messages (must, if producer uses tx) |
| `ErrorHandlingDeserializer` wrap | Deserialization errors flow to error handler instead of crashing container |
| Concurrency ≤ partitions | Extra threads are idle; size to actual partitions per pod |
| Inbox table (`event-id` PK) | Idempotent processing — duplicate delivery is harmless |

## Concurrency Sizing

```
total_consumer_threads = pods × factory.concurrency  ≤  topic.partitions
```

Example: topic has 12 partitions, 3 pods → set `concurrency=4` (3 × 4 = 12).

## Batch Consumer (for high throughput, e.g. projections)

```java
factory.setBatchListener(true);

@KafkaListener(topics = "audit.event.recorded.v1")
public void onBatch(List<ConsumerRecord<String, AuditEvent>> records, Acknowledgment ack) {
  auditRepo.saveAll(records.stream().map(r -> map(r)).toList());
  ack.acknowledge();
}
```

Pair with `RetryableTopic` or batch error handler — single bad record in a batch must not poison the rest.

## Retry Topics (delayed retry without blocking the partition)

```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(delay = 1000, multiplier = 2.0),
    autoCreateTopics = "false",
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    include = {TransientDownstreamException.class})
@KafkaListener(topics = "payments.transfer.initiated.v1", groupId = "settlement")
public void onSettle(TransferInitiated event) { ... }
```

Creates `payments.transfer.initiated.v1-retry-0`, `…-retry-1`, `…-dlt` topics automatically. Use for transient downstream failures (HTTP 5xx from settlement service).

## Common Pitfalls

- Long-running processing > `max.poll.interval.ms` → consumer kicked from group → duplicate delivery storm
- Catching exceptions inside listener and acking anyway → silent message loss
- Sharing one `KafkaTemplate` between transactional and non-transactional flows
- Not bumping `concurrency` after adding partitions
- DLT without alerting → messages rot, ops never knows
