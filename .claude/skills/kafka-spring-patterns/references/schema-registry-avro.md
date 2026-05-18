# Schema Registry + Avro / Protobuf

Banking events are **contracts**. Use a Schema Registry (Confluent or Apicurio) so producer/consumer agree on shape and evolution is checked at publish time.

## Choose Avro or Protobuf

| | Avro | Protobuf |
|---|---|---|
| Schema lookup | Required at runtime (Registry) | Optional but recommended |
| Generated code | Specific records / Generic | Strongly typed messages |
| Default-value handling | Strong (compatibility-aware) | Required to write `optional` |
| Polyglot ecosystem | Native (Kafka + Hadoop) | Native (gRPC + Kafka) |
| **Default for banking** | ✅ **Avro** (broader Kafka tooling) | Use if already on gRPC |

## Subject Naming

Use `TopicNameStrategy` (default): subject = `<topic>-value`.

For event-per-topic (which we follow), this gives a 1:1 schema:topic mapping — simplest to reason about.

For events with siblings sharing one topic, use `RecordNameStrategy` or `TopicRecordNameStrategy`.

## Compatibility Mode

Set per subject:

| Mode | Producer can | Consumer must |
|---|---|---|
| `BACKWARD` (default) | Add optional field, remove required | Upgrade first |
| `FORWARD` | Remove optional field | Old consumers keep working |
| `FULL` | Add optional, default required | Both directions safe |
| `BACKWARD_TRANSITIVE` | Same as BACKWARD, vs all prior versions | Safer for long-lived topics |

**For banking events** — default to `BACKWARD_TRANSITIVE`. Bump topic major version (`.v2`) for breaking changes.

## Producer Config (Avro)

```yaml
spring:
  kafka:
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    properties:
      schema.registry.url: https://schema-registry.internal
      auto.register.schemas: false      # CI registers; runtime only reads
      use.latest.version: true
      basic.auth.credentials.source: USER_INFO
      basic.auth.user.info: ${SCHEMA_REGISTRY_USER}:${SCHEMA_REGISTRY_PASS}
```

`auto.register.schemas=false` in prod → schemas are committed via CI (`schema-registry-maven-plugin`), reviewed in PR.

## Example Schema — TransferInitiated

```json
{
  "type": "record",
  "namespace": "com.bank.payments.events",
  "name": "TransferInitiated",
  "fields": [
    { "name": "eventId",      "type": { "type": "string", "logicalType": "uuid" } },
    { "name": "occurredAt",   "type": { "type": "long", "logicalType": "timestamp-micros" } },
    { "name": "transferId",   "type": { "type": "string", "logicalType": "uuid" } },
    { "name": "fromAccountId","type": { "type": "string", "logicalType": "uuid" } },
    { "name": "toAccountId",  "type": { "type": "string", "logicalType": "uuid" } },
    {
      "name": "amount",
      "type": {
        "type": "bytes",
        "logicalType": "decimal",
        "precision": 18,
        "scale": 4
      }
    },
    { "name": "currency",     "type": "string" },
    { "name": "initiatedBy",  "type": "string" }
  ]
}
```

Notes:
- `decimal` (bytes + precision/scale) — never use `float`/`double` for money
- UUIDs as logical types so SDKs surface them as `UUID` not `String`
- `occurredAt` is `timestamp-micros` (microsecond precision)
- **No PII** — `customerName`, `panMasked`, etc. require separate review + redaction strategy

## Evolution Rules (concrete)

| Change | Safe under BACKWARD? |
|---|---|
| Add optional field with default | ✅ |
| Add required field (no default) | ❌ — breaking |
| Remove optional field | ✅ |
| Remove required field | ❌ |
| Rename field | ❌ (treat as remove + add) — use `aliases` |
| Change field type | ❌ — bump major |
| Reorder fields | ✅ (Avro) |
| Promote `int` → `long` | ✅ |

When a breaking change is unavoidable: create new topic `.v2`, dual-publish for a transition window, migrate consumers, retire `.v1`.

## CI Check

Maven plugin runs in CI before deploy:

```xml
<plugin>
  <groupId>io.confluent</groupId>
  <artifactId>kafka-schema-registry-maven-plugin</artifactId>
  <configuration>
    <schemaRegistryUrls>
      <param>${schema.registry.url}</param>
    </schemaRegistryUrls>
    <subjects>
      <payments.transfer.initiated.v1-value>
        src/main/avro/TransferInitiated.avsc
      </payments.transfer.initiated.v1-value>
    </subjects>
    <compatibilityLevels>
      <param>BACKWARD_TRANSITIVE</param>
    </compatibilityLevels>
  </configuration>
  <executions>
    <execution>
      <goals><goal>test-compatibility</goal></goals>
    </execution>
  </executions>
</plugin>
```

A breaking change fails the build — not the deploy.

## Common Pitfalls

- Letting producer auto-register schemas in prod → drift, no review
- Using `String`/JSON for financial events to "ship fast" → no contract, breaking changes undetected
- Storing money as `double` in Avro → silent rounding
- Wrong namespace per environment → consumer fails to deserialize
- Not pinning schema version in producer (`use.latest.version`) → flaky behavior under registry churn
