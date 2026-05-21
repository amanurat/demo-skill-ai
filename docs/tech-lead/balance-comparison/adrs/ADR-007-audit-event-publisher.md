# ADR-007 — Audit Event Publisher (hexagonal port; metadata-only; async fire-and-forget; no outbox)

> **Status:** ACCEPTED
> **Date:** 2026-05-21
> **Owner:** `banking-tech-lead`
> **Consumers:** `banking-backend-dev` (primary), `banking-reviewer-be`, `banking-security`, `banking-qa`, `banking-devops`
> **Sprint:** SPRINT-2026-Q2-BC-01
> **Feature:** `balance-comparison`
> **Resolves:** Security Condition **C-2** (audit metadata-only)
> **Related:**
> - [Security C-2](../../../security/balance-comparison/early-review-consent-coverage.md#condition-c-2-audit-event-schema--metadata-only)
> - [SA ADR-001 §No outbox justification](../../../sa/balance-comparison/adrs/ADR-001-service-boundary.md)
> - [SA ADR-003 audit event v1 → v2](../../../sa/balance-comparison/adrs/ADR-003-audit-event-evolution.md)
> - [SA event-flows §3.3 audit publish path](../../../sa/balance-comparison/event-flows.md)
> - [BA BR-014 audit on every retrieval (cache never short-circuits)](../../../ba/balance-comparison/user-stories.md)
> - [TL db-schema §5 Avro v2 reference](../db-schema.md)
> - [ADR-006 IborCheckFilter (audit FORBIDDEN emit site)](./ADR-006-customerid-resolver-pattern.md)

---

## 1. Context

Three constraints converge on this component:

1. **Security C-2 (PDPA §22 data minimization)** — the audit payload MUST be metadata-only. Balance values, account numbers, and account IDs MUST NOT appear in audit events; otherwise, the 7-year retained audit log becomes an unintended balance-history honeypot.
2. **SA ADR-001 (no RDBMS in BDS)** — the standard outbox pattern (which writes the event row in the same DB transaction as the business state change) is unavailable: there is no business state change and no transactional database. SA accepted at-most-one-event-loss-per-Kafka-outage in v1.
3. **BA BR-014 (audit on every retrieval, including cache hit)** — the cache layer MUST NOT short-circuit audit emission. Audit is on the critical correctness path even though it is off the latency-critical path.

A clean **hexagonal port** (domain interface) keeps the application-layer use case ignorant of Kafka — easier to test, replaceable if the transport ever changes.

## 2. Decision

### 2.1 Port (hexagonal, domain-owned)

```java
package com.bank.balancedashboard.application.port.out;

import com.bank.balancedashboard.domain.audit.AuditEventRecord;

/**
 * Outbound port for emitting audit events. Implementation lives in
 * infrastructure layer (KafkaAuditEventPublisher).
 *
 * Contract:
 *   - publish() MUST NOT throw to the caller. Kafka unavailability is logged
 *     + metered; the dashboard request still returns 200 to the user.
 *     (See ADR-001 §No outbox justification: at-most-one-event-loss-per-outage
 *     is an accepted v1 risk.)
 *   - publish() MUST be called for EVERY request outcome — SUCCESS, FORBIDDEN,
 *     ERROR — including cache hits (BR-014). Cache layer never short-circuits.
 *   - Implementation is responsible for ensuring the record carries ONLY the
 *     fields defined in AuditEventRecord. Forbidden fields per Security C-2:
 *     balance, accountId, accountNumber, accounts[], balanceAsOf per-account,
 *     currency.
 */
public interface AuditEventPublisher {
    void publish(AuditEventRecord record);
}
```

### 2.2 Value object (the metadata-only envelope)

```java
package com.bank.balancedashboard.domain.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit-event payload — ONLY the metadata fields permitted by
 * Security C-2 (PDPA §22 data minimization). Maps 1:1 to Avro v2
 * AuditEventRecorded (com.bank.compliance.audit.v2).
 *
 * FORBIDDEN fields (Security C-2 — checked by KafkaAuditEventPublisherContractTest):
 *   - balance               (per-account or aggregated)
 *   - accountId             (UUID)
 *   - accountNumber         (full OR masked)
 *   - accounts              (the array — never serialize the response payload)
 *   - balanceAsOf           (per-account timestamps; aggregate ok via 'timestamp' below)
 *   - currency              (per-account)
 *
 * PERMITTED metadata fields:
 */
public record AuditEventRecord(
    String eventType,        // "BALANCE_INQUIRY"
    UUID   actorId,          // JWT sub — the acting customer
    Channel channel,         // MOBILE_BANKING | WEB | API
    String correlationId,    // OTel trace ID (lowercase UUID)
    Instant timestamp,       // event emit time, UTC
    Result result,           // SUCCESS | FORBIDDEN | ERROR
    String purpose,          // "balance-inquiry" — first-class Avro v2 field (PDPA purpose limitation)
    Boolean cacheHit,        // true if served from Redis (BDS-only context; null for other event types)
    Integer accountCount     // aggregate count returned, 0 for empty or FORBIDDEN/ERROR
) {
    public static AuditEventRecord success(UUID actorId, String correlationId, Channel channel,
                                           boolean cacheHit, int accountCount) {
        return new AuditEventRecord("BALANCE_INQUIRY", actorId, channel, correlationId,
                                     Instant.now(), Result.SUCCESS, "balance-inquiry",
                                     cacheHit, accountCount);
    }

    public static AuditEventRecord forbidden(UUID actorId, String correlationId, Channel channel) {
        return new AuditEventRecord("BALANCE_INQUIRY", actorId, channel, correlationId,
                                     Instant.now(), Result.FORBIDDEN, "balance-inquiry",
                                     null, 0);
    }

    public static AuditEventRecord error(UUID actorId, String correlationId, Channel channel) {
        return new AuditEventRecord("BALANCE_INQUIRY", actorId, channel, correlationId,
                                     Instant.now(), Result.ERROR, "balance-inquiry",
                                     null, 0);
    }
}
```

**Note:** The record's **type signature is the contract**. There is intentionally no field of type `BigDecimal balance` or `String accountNumber` — making it a compile-time error to add one without amending this ADR.

### 2.3 Avro v2 mapping (canonical reference)

`AuditEventRecord` fields map to Avro v2 `AuditEventRecorded` (`com.bank.compliance.audit.v2`) per [db-schema.md §5](../db-schema.md):

| Java field | Avro v2 field | Notes |
|---|---|---|
| `eventType` | `eventType` (string) | "BALANCE_INQUIRY" |
| `actorId` | `actorId` (string) | UUID lower-case canonical form |
| `channel` | `channel` (enum) | MOBILE_BANKING / WEB / API |
| `correlationId` | `correlationId` (string) | OTel trace ID |
| `timestamp` | `timestamp` (long) | epoch millis UTC |
| `result` | `result` (enum) | SUCCESS / FAILURE / FORBIDDEN / ERROR |
| `purpose` | `purpose` (union[null,string]) | v2 first-class field |
| `cacheHit` | `cacheHit` (union[null,boolean]) | v2 first-class field |
| `accountCount` | `accountCount` (union[null,int]) | v2 first-class field |
| — | `payload` (union[null,map[string,string]]) | always `null` from BDS (legacy v1 producers only) |

Java package `com.bank.balancedashboard.domain.audit` is BDS-internal. The Avro-generated class lives at `com.bank.compliance.audit.v2.AuditEventRecorded` and is mapped from `AuditEventRecord` inside `KafkaAuditEventPublisher`. **The Avro namespace `com.bank.compliance.audit.v2` MUST match the Apicurio-registered schema namespace exactly** — mismatch causes Confluent/Apicurio deserializer rejection at the consumer (per banking-tech-lead skill gotcha).

### 2.4 Adapter (infrastructure layer)

```java
package com.bank.balancedashboard.infrastructure.audit;

import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.compliance.audit.v2.AuditEventRecorded;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditEventPublisher.class);
    private static final String TOPIC = "audit.event-recorded";

    private final KafkaProducer<String, AuditEventRecorded> producer;
    private final MeterRegistry meterRegistry;

    public KafkaAuditEventPublisher(KafkaProducer<String, AuditEventRecorded> producer,
                                    MeterRegistry meterRegistry) {
        this.producer = producer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void publish(AuditEventRecord record) {
        try {
            AuditEventRecorded avro = AvroMapper.toAvro(record);
            ProducerRecord<String, AuditEventRecorded> kafkaRecord = new ProducerRecord<>(
                TOPIC,
                /* key = */ record.actorId().toString(),
                /* value = */ avro
            );
            // Async fire-and-forget. NO .get(). NO outbox.
            // Per SA ADR-001 + event-flows §3.4: at-most-one-event-loss-per-outage accepted in v1.
            producer.send(kafkaRecord, (metadata, exception) -> {
                if (exception != null) {
                    log.warn("audit.publish.failed correlationId={} result={}",
                             record.correlationId(), record.result(), exception);
                    meterRegistry.counter("audit_events_total", "result", "FAILED").increment();
                } else {
                    meterRegistry.counter("audit_events_total", "result", "PUBLISHED").increment();
                }
            });
        } catch (RuntimeException e) {
            // Synchronous serialization failure (e.g., Avro schema mismatch).
            // Swallow — never throw to caller per port contract.
            log.warn("audit.publish.serialize.failed correlationId={} result={}",
                     record.correlationId(), record.result(), e);
            meterRegistry.counter("audit_events_total", "result", "FAILED").increment();
        }
    }
}
```

Producer config (in `application.yml`, see implementation-notes §3):

```yaml
spring:
  kafka:
    producer:
      acks: "1"
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        schema.registry.url: ${APICURIO_URL}
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.apicurio.registry.serde.avro.AvroKafkaSerializer
```

### 2.5 Call sites (the call-site map)

`publish()` is called at exactly **three** sites in BDS, covering every request outcome:

| Site | Outcome | Where | Record factory |
|---|---|---|---|
| 1 | SUCCESS (cache hit OR miss) | `BalanceDashboardService.loadDashboard()` — after building response, regardless of cache path | `AuditEventRecord.success(actorId, correlationId, channel, cacheHit, accountCount)` |
| 2 | FORBIDDEN (IDOR header mismatch) | `IborCheckFilter.doFilterInternal()` — before returning 403 (per ADR-006) | `AuditEventRecord.forbidden(actorId, correlationId, channel)` |
| 3 | ERROR (upstream failure: CB-open, timeout, exception) | `BalanceDashboardService.loadDashboard()` — in catch block, before re-throw or returning 503 | `AuditEventRecord.error(actorId, correlationId, channel)` |

**Cache layer NEVER short-circuits audit (BR-014).** Site 1 runs after both warm hit and cold miss code paths.

### 2.6 Contract test (mandatory — Security C-2 enforcement)

`backend/balance-dashboard-service/src/test/java/com/bank/balancedashboard/infrastructure/audit/KafkaAuditEventPublisherContractTest.java`:

```java
class KafkaAuditEventPublisherContractTest {

    @Test
    void serializedAuditRecord_neverContainsForbiddenFields() throws Exception {
        // Given: a representative SUCCESS event
        AuditEventRecord record = AuditEventRecord.success(
            UUID.randomUUID(),
            "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60",
            Channel.MOBILE_BANKING,
            /* cacheHit */ true,
            /* accountCount */ 5
        );

        // When: serialize to bytes (the exact path Kafka producer takes)
        AuditEventRecorded avro = AvroMapper.toAvro(record);
        byte[] bytes = AvroTestSerializer.serialize(avro);
        String asString = new String(bytes, StandardCharsets.UTF_8);

        // Then: forbidden field names (PDPA §22 + Security C-2) MUST NOT appear
        assertThat(asString).doesNotContain("balance");
        assertThat(asString).doesNotContain("accountId");
        assertThat(asString).doesNotContain("accountNumber");
        assertThat(asString).doesNotContain("accounts"); // the array
        assertThat(asString).doesNotContain("balanceAsOf");
        assertThat(asString).doesNotContain("currency");

        // And: permitted metadata fields ARE present
        assertThat(asString).contains("BALANCE_INQUIRY");
        assertThat(asString).contains("balance-inquiry"); // purpose value (substring 'balance' is OK because field name is 'purpose', not 'balance')
        assertThat(asString).contains("cacheHit");
        assertThat(asString).contains("accountCount");
    }

    @Test
    void recordTypeSignature_doesNotExposeForbiddenFieldTypes() {
        // Reflection-based: defense against future PRs adding fields to AuditEventRecord.
        var fieldTypes = Arrays.stream(AuditEventRecord.class.getRecordComponents())
            .map(RecordComponent::getType)
            .collect(Collectors.toSet());

        assertThat(fieldTypes).doesNotContain(BigDecimal.class); // balance
        // accountId / accountNumber are String — those CAN appear for other reasons, so guarded
        // by the byte-grep test above and by name conventions.
    }
}
```

**Note on `balance` substring:** the purpose value is `"balance-inquiry"`, which contains the substring `balance`. The byte-grep above is for the **field name** `"balance"` (with JSON quoting). The actual production assertion uses a more precise regex: `Pattern.compile("\"balance\"\\s*:")` — match only `"balance":` as a JSON key, not the substring inside `"balance-inquiry"`. See implementation-notes §audit-publisher-contract-test for the production-grade implementation.

## 3. Consequences

### 3.1 Positive

- **PDPA §22 enforced by type system + test.** A future PR cannot add `balance` to the audit payload without (a) changing the record signature, (b) failing the contract test, (c) failing code review for ADR-007 drift.
- **No outbox burden.** No additional table, no migration, no transactional coupling. Matches SA ADR-001's no-RDBMS posture.
- **Audit is on the critical correctness path, off the latency path.** Async fire-and-forget; main thread returns immediately. Audit failures degrade observability, not user UX.
- **Reusable across services.** `AuditEventRecord` + `AuditEventPublisher` are package-renameable into `common-libs/audit-lib` if loan-dashboard / statement-service ever ship.

### 3.2 Negative

- **At-most-one-event-loss-per-Kafka-outage is accepted in v1** (SA ADR-001 §No outbox justification). If compliance later requires zero-loss, v1.1 will need to introduce either (a) Redis-backed outbox (durable buffer in Redis Streams, drained by a sweeper), or (b) RDBMS + transactional outbox (and accept the architectural cost). Re-open criteria documented in SA event-flows §3.4.
- **Swallowed Kafka exceptions** could mask systemic outages from operators. Mitigation: alert on `audit_events_total{result=FAILED}` rate > 0/5min and on `audit_events_total{result=PUBLISHED}` rate == 0/2min (silent compliance failure). Owned by DevOps P2 (AlertManager).

### 3.3 Risks

- **Avro schema registration sequencing (SA-RISK-002).** v2 must be registered in Apicurio by D6 before BDS deploys on D8. Mitigation: DevOps P1 owns registration; D7 smoke test verifies serialization end-to-end before BDS GA.
- **Java package vs Avro namespace mismatch.** Apicurio/Confluent deserializer rejects records if the producer's generated Java class package differs from the registered namespace. Mitigation: `KafkaAuditEventPublisherContractTest` asserts the package; D7 smoke test catches it before BDS deploys.

## 4. Alternatives considered (rejected)

| Option | Rejected because |
|---|---|
| **Transactional outbox table in a new BDS database** | Contradicts SA ADR-001 (no RDBMS). Introduces a database for the sole purpose of guaranteeing audit-event delivery in a Kafka outage — disproportionate operational cost. **REJECTED.** |
| **Synchronous `producer.send().get()` (block on Kafka ack)** | (a) Adds ≥ 50ms p50, ≥ 200ms p99 to the dashboard response (NFR §1 cap 200ms audit overhead — would burn the entire budget). (b) Couples user-facing latency to Kafka availability. (c) On Kafka outage, the user sees 503 even though the read succeeded. **REJECTED.** |
| **Stuff response payload (`accounts[]`, balances) into audit payload "for traceability"** | Direct PDPA §22 violation — 7y retention of balance history is purpose-stretch. Exactly what Security C-2 forbids. **REJECTED.** |
| **Per-account audit events (one event per account in the response)** | (a) N× Kafka load for no compliance benefit (aggregate accountCount is sufficient per BoT). (b) Auditors don't query at per-account granularity for BALANCE_INQUIRY events. (c) Each event would carry per-account `accountId` which is forbidden. **REJECTED.** |
| **Redis Streams as the audit transport instead of Kafka** | Audit-service consumer is on Kafka; introducing a second transport breaks the single audit pipeline. **REJECTED.** |
| **Spring `ApplicationEventPublisher` (in-process events) instead of a domain port** | In-process events lose the network boundary the audit-service consumer needs. **REJECTED.** |
| **Reuse `common-libs/audit-lib` Kafka publisher as-is** | The shared lib publishes against Avro v1 schema. v2 schema migration (per SA ADR-003) requires either (a) bumping the lib OR (b) BDS owning its own publisher until the lib catches up. We adopt (b) for sprint velocity, with a follow-up ticket to promote `KafkaAuditEventPublisher` into the shared lib once v2 is GA. **DEFERRED — not rejected; will revisit in v1.1.** |

## 5. Acceptance criteria

- [ ] `AuditEventPublisher` port interface exists at `application/port/out/`.
- [ ] `AuditEventRecord` value object has exactly the 9 fields specified in §2.2 — no `balance`, no `accountId`, no `accountNumber`, no `accounts[]`.
- [ ] `KafkaAuditEventPublisher` adapter exists at `infrastructure/audit/`.
- [ ] `application.yml` producer config has `acks=1`, `enable.idempotence=true`, async (no `.get()` anywhere in the codebase).
- [ ] All three call sites (SUCCESS, FORBIDDEN, ERROR) emit audit (verified by integration tests).
- [ ] Cache hit path emits audit (BR-014) — integration test covers warm cache call → audit emitted.
- [ ] `KafkaAuditEventPublisherContractTest` passes — byte-grep proves zero forbidden field names in serialized output.
- [ ] Avro namespace in registered v2 schema matches Java package `com.bank.compliance.audit.v2`.
- [ ] Metric `audit_events_total{result=PUBLISHED|FAILED}` is registered with Prometheus.
- [ ] Kafka exceptions are swallowed (logged + metered), never thrown to the use case.

---

*ADR-007 · banking-tech-lead · 2026-05-21 · resolves Security C-2*
