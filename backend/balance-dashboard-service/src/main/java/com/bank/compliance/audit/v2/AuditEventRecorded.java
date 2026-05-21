package com.bank.compliance.audit.v2;

/**
 * Avro v2 generated class stub for AuditEventRecorded.
 *
 * <p>In production this class is generated from the Avro schema registered in Apicurio
 * under namespace {@code com.bank.compliance.audit.v2}. The namespace MUST match exactly —
 * mismatch causes Apicurio/Confluent deserializer rejection at the consumer.
 *
 * <p>This stub mirrors the Avro v2 field mapping from ADR-007 §2.3.
 * Field {@code payload} is always null from BDS (legacy v1 producers only).
 *
 * <p>NAMESPACE: com.bank.compliance.audit.v2 — verified by KafkaAuditEventPublisherContractTest.
 */
public class AuditEventRecorded {

    private String eventType;      // "BALANCE_INQUIRY"
    private String actorId;        // UUID lower-case string
    private String channel;        // MOBILE_BANKING | WEB | API
    private String correlationId;  // OTel trace ID
    private long   timestamp;      // epoch millis UTC
    private String result;         // SUCCESS | FAILURE | FORBIDDEN | ERROR
    private String purpose;        // "balance-inquiry" (v2 first-class field)
    private Boolean cacheHit;      // true/false/null
    private Integer accountCount;  // aggregate count, 0 for FORBIDDEN/ERROR
    private Object payload;        // always null from BDS

    public AuditEventRecorded() {}

    public String getEventType()        { return eventType; }
    public void setEventType(String v)  { this.eventType = v; }

    public String getActorId()          { return actorId; }
    public void setActorId(String v)    { this.actorId = v; }

    public String getChannel()          { return channel; }
    public void setChannel(String v)    { this.channel = v; }

    public String getCorrelationId()    { return correlationId; }
    public void setCorrelationId(String v) { this.correlationId = v; }

    public long getTimestamp()          { return timestamp; }
    public void setTimestamp(long v)    { this.timestamp = v; }

    public String getResult()           { return result; }
    public void setResult(String v)     { this.result = v; }

    public String getPurpose()          { return purpose; }
    public void setPurpose(String v)    { this.purpose = v; }

    public Boolean getCacheHit()        { return cacheHit; }
    public void setCacheHit(Boolean v)  { this.cacheHit = v; }

    public Integer getAccountCount()    { return accountCount; }
    public void setAccountCount(Integer v) { this.accountCount = v; }

    public Object getPayload()          { return payload; }
    public void setPayload(Object v)    { this.payload = v; }
}
