package com.bank.balancedashboard.contract;

import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Channel;
import com.bank.balancedashboard.infrastructure.audit.AvroMapper;
import com.bank.compliance.audit.v2.AuditEventRecorded;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security C-2 enforcement contract test — ADR-007 §2.6 acceptance criteria.
 *
 * <p>Mandatory test surface:
 * (1) SUCCESS record byte-grep: ZERO matches for forbidden field keys + positive presence checks
 * (2) FORBIDDEN record byte-grep: ZERO matches
 * (3) ERROR record byte-grep: ZERO matches
 * (4) Reflection: AuditEventRecord component names MUST NOT contain forbidden names
 *
 * <p>Pattern: matches JSON keys exactly (won't false-positive on "balance" inside "balance-inquiry")
 * {@code "balance"\s*:} matches key, NOT substring.
 *
 * <p>Why three serialization tests: each factory has different field-population profile;
 * a forbidden field added via SUCCESS factory only would slip past a single-test contract.
 */
class KafkaAuditEventPublisherContractTest {

    /**
     * Regex matches JSON keys EXACTLY — prevents false positive on "balance-inquiry" substring.
     * Matches: {@code "balance":}, {@code "balance" :} etc.
     * Does NOT match: {@code "balance-inquiry"} (no colon follows).
     */
    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
            "\"(balance|accountId|accountNumber|accounts|balanceAsOf|currency)\"\\s*:"
    );

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("(1) SUCCESS record: no forbidden field keys + positive metadata present")
    void serialized_success_record_neverContainsForbiddenFieldKeys() throws Exception {
        // Given
        AuditEventRecord rec = AuditEventRecord.success(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60",
                Channel.MOBILE_BANKING,
                true,
                5
        );

        // When: serialize via AvroMapper then JSON (mimics the production serialization path)
        AuditEventRecorded avro = AvroMapper.toAvro(rec);
        String json = objectMapper.writeValueAsString(avro);

        // Then: FORBIDDEN field keys MUST NOT appear in serialized output (Security C-2 / PDPA §22)
        assertThat(FORBIDDEN_KEY.matcher(json).find())
                .as("Audit event must not carry forbidden field keys per Security C-2 / PDPA §22. Got: %s", json)
                .isFalse();

        // And: positive presence of permitted metadata fields
        assertThat(json).contains("\"eventType\"");
        assertThat(json).contains("BALANCE_INQUIRY");
        assertThat(json).contains("balance-inquiry");  // purpose value (substring 'balance' inside string value is OK)
        assertThat(json).contains("\"cacheHit\"");
        assertThat(json).contains("\"accountCount\"");
        assertThat(json).contains("MOBILE_BANKING");
        assertThat(json).contains("SUCCESS");
    }

    @Test
    @DisplayName("(2) FORBIDDEN record: no forbidden field keys")
    void serialized_forbidden_record_neverContainsForbiddenFieldKeys() throws Exception {
        AuditEventRecord rec = AuditEventRecord.forbidden(
                UUID.randomUUID(), "trace-1", Channel.MOBILE_BANKING);

        AuditEventRecorded avro = AvroMapper.toAvro(rec);
        String json = objectMapper.writeValueAsString(avro);

        assertThat(FORBIDDEN_KEY.matcher(json).find())
                .as("FORBIDDEN audit event must not carry forbidden field keys. Got: %s", json)
                .isFalse();

        assertThat(json).contains("FORBIDDEN");
        assertThat(json).contains("balance-inquiry");
    }

    @Test
    @DisplayName("(3) ERROR record: no forbidden field keys")
    void serialized_error_record_neverContainsForbiddenFieldKeys() throws Exception {
        AuditEventRecord rec = AuditEventRecord.error(
                UUID.randomUUID(), "trace-2", Channel.API);

        AuditEventRecorded avro = AvroMapper.toAvro(rec);
        String json = objectMapper.writeValueAsString(avro);

        assertThat(FORBIDDEN_KEY.matcher(json).find())
                .as("ERROR audit event must not carry forbidden field keys. Got: %s", json)
                .isFalse();

        assertThat(json).contains("ERROR");
    }

    @Test
    @DisplayName("(4) Reflection: AuditEventRecord component names contain NO forbidden names")
    void recordType_signature_doesNotExposeForbiddenComponentNames() {
        Set<String> componentNames = Arrays.stream(AuditEventRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        // FORBIDDEN names per Security C-2 (PDPA §22 data minimization)
        assertThat(componentNames).doesNotContain(
                "balance",
                "accountId",
                "accountNumber",
                "accounts",
                "balanceAsOf",
                "currency"
        );

        // Exactly 9 permitted metadata fields
        assertThat(componentNames).containsExactlyInAnyOrder(
                "eventType", "actorId", "channel", "correlationId",
                "timestamp", "result", "purpose", "cacheHit", "accountCount"
        );
    }

    @Test
    @DisplayName("(5) AvroMapper namespace: com.bank.compliance.audit.v2 matches Apicurio registration")
    void avroMapper_packageNameMatchesApicurioNamespace() {
        // The Avro v2 generated class MUST live in com.bank.compliance.audit.v2
        // Namespace mismatch causes Apicurio/Confluent deserializer rejection at the consumer (ADR-007 §2.3 risk)
        assertThat(AuditEventRecorded.class.getPackageName())
                .isEqualTo("com.bank.compliance.audit.v2");
    }
}
