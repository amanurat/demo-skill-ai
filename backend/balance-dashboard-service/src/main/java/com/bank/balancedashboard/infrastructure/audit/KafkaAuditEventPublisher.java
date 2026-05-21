package com.bank.balancedashboard.infrastructure.audit;

import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.compliance.audit.v2.AuditEventRecorded;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter for {@link AuditEventPublisher}.
 *
 * <p>Async fire-and-forget — NO {@code .get()} anywhere (ADR-007 §2.4).
 * Publisher key: {@code record.actorId().toString()} — partitions by customer
 * for ordering within the customer's audit stream.
 *
 * <p>All exceptions (Kafka unavailability, serialization failure) are swallowed:
 * logged at WARN + metered — never thrown to the application layer.
 *
 * <p>Producer config from application.yml: acks=1, enable.idempotence=true,
 * max.in.flight.requests.per.connection=5.
 *
 * <p>Avro namespace {@code com.bank.compliance.audit.v2} MUST match Apicurio-registered
 * schema namespace exactly (verified by KafkaAuditEventPublisherContractTest).
 */
@Component
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditEventPublisher.class);

    static final String TOPIC = "audit.event-recorded";

    // R-BE-012: use Avro-typed KafkaTemplate to enforce Apicurio schema registry wiring.
    // Producer must be configured with io.apicurio.registry.serde.avro.AvroKafkaSerializer
    // and spring.kafka.producer.properties.schema.registry.url per ADR-007 §2.4.
    private final KafkaTemplate<String, AuditEventRecorded> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public KafkaAuditEventPublisher(
            KafkaTemplate<String, AuditEventRecorded> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publishes an audit event asynchronously.
     *
     * <p>Per ADR-007 §2.4 contract: this method MUST NOT throw.
     * Kafka failure → log WARN + increment {@code audit_events_total{result=FAILED}}.
     *
     * @param record the Security C-2 compliant audit record (no forbidden fields)
     */
    @Override
    public void publish(AuditEventRecord record) {
        try {
            // Map domain record to Avro DTO (Security C-2: only 9 permitted metadata fields)
            AuditEventRecorded avro = AvroMapper.toAvro(record);

            // Producer is wired with AvroKafkaSerializer against Apicurio schema registry
            // (spring.kafka.producer.value-serializer = io.apicurio.registry.serde.avro.AvroKafkaSerializer).
            // The schema.registry.url is set from ${SCHEMA_REGISTRY_URL} in application.yml.
            // ADR-007 §2.4: KafkaTemplate<String, AuditEventRecorded> enforces the contract at compile time.
            String key = record.actorId().toString(); // actorId is guaranteed non-null by AuditEventRecord compact constructor

            // Async fire-and-forget — callback handles success/failure metering
            kafkaTemplate.send(TOPIC, key, avro)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("audit.publish.failed correlationId={} result={}",
                                    record.correlationId(), record.result(), ex);
                            meterRegistry.counter("audit_events_total", "result", "FAILED")
                                    .increment();
                        } else {
                            meterRegistry.counter("audit_events_total", "result", "PUBLISHED")
                                    .increment();
                        }
                    });

        } catch (RuntimeException e) {
            // Synchronous failure (serialization error, Kafka producer not initialized, etc.)
            // Swallow — never throw to caller per AuditEventPublisher contract.
            log.warn("audit.publish.serialize.failed correlationId={} result={}",
                    record.correlationId(), record.result(), e);
            meterRegistry.counter("audit_events_total", "result", "FAILED").increment();
        } catch (Exception e) {
            // Catch all — belt-and-suspenders so publish() truly never throws
            log.warn("audit.publish.unexpected-error correlationId={} result={}",
                    record.correlationId(), record.result(), e);
            meterRegistry.counter("audit_events_total", "result", "FAILED").increment();
        }
    }
}
