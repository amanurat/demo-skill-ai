package com.bank.balancedashboard.infrastructure.audit;

import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.compliance.audit.v2.AuditEventRecorded;

/**
 * Maps {@link AuditEventRecord} domain value object to the Avro v2
 * {@link AuditEventRecorded} generated class.
 *
 * <p>Security C-2 compliance: this mapper ONLY copies the 9 metadata fields
 * defined in {@link AuditEventRecord}. No balance, accountId, accountNumber,
 * accounts[], balanceAsOf, or currency values are added here.
 *
 * <p>Avro namespace {@code com.bank.compliance.audit.v2} MUST match the
 * Apicurio-registered schema namespace exactly (ADR-007 §2.3 risk).
 */
public class AvroMapper {

    private AvroMapper() {
        // Utility class — do not instantiate
    }

    /**
     * Maps a domain AuditEventRecord to its Avro v2 counterpart.
     *
     * @param record domain audit record (Security C-2 compliant — no forbidden fields)
     * @return Avro v2 AuditEventRecorded ready for Kafka serialization
     */
    public static AuditEventRecorded toAvro(AuditEventRecord record) {
        AuditEventRecorded avro = new AuditEventRecorded();
        avro.setEventType(record.eventType());
        avro.setActorId(record.actorId() != null ? record.actorId().toString() : null);
        avro.setChannel(record.channel() != null ? record.channel().name() : null);
        avro.setCorrelationId(record.correlationId());
        avro.setTimestamp(record.timestamp() != null ? record.timestamp().toEpochMilli() : 0L);
        avro.setResult(record.result() != null ? record.result().name() : null);
        avro.setPurpose(record.purpose());
        avro.setCacheHit(record.cacheHit());
        avro.setAccountCount(record.accountCount());
        avro.setPayload(null); // always null from BDS (legacy v1 producers only)
        return avro;
    }
}
