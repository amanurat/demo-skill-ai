package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Channel;
import com.bank.balancedashboard.domain.audit.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuditEventRecord} domain value object.
 * Security C-2 enforcement: forbidden field names verified by reflection.
 * ZERO Spring context.
 */
class AuditEventRecordTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String CORRELATION_ID = "7f4e2b91-3a8c-4d65-9f12-1c2b3d4e5f60";

    @Test
    @DisplayName("(1) success() factory populates all 9 fields correctly")
    void successFactory_populatesAllNineFields() {
        AuditEventRecord rec = AuditEventRecord.success(
                ACTOR_ID, CORRELATION_ID, Channel.MOBILE_BANKING, true, 5);

        assertThat(rec.eventType()).isEqualTo("BALANCE_INQUIRY");
        assertThat(rec.actorId()).isEqualTo(ACTOR_ID);
        assertThat(rec.channel()).isEqualTo(Channel.MOBILE_BANKING);
        assertThat(rec.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(rec.timestamp()).isNotNull();
        assertThat(rec.result()).isEqualTo(Result.SUCCESS);
        assertThat(rec.purpose()).isEqualTo("balance-inquiry");
        assertThat(rec.cacheHit()).isTrue();
        assertThat(rec.accountCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("(2) forbidden() factory populates correct subset — cacheHit=null, accountCount=0")
    void forbiddenFactory_populatesCorrectSubset() {
        AuditEventRecord rec = AuditEventRecord.forbidden(ACTOR_ID, CORRELATION_ID, Channel.WEB);

        assertThat(rec.result()).isEqualTo(Result.FORBIDDEN);
        assertThat(rec.cacheHit()).isNull();
        assertThat(rec.accountCount()).isEqualTo(0);
        assertThat(rec.actorId()).isEqualTo(ACTOR_ID);
        assertThat(rec.purpose()).isEqualTo("balance-inquiry");
    }

    @Test
    @DisplayName("(3) error() factory populates correct subset — cacheHit=null, accountCount=0")
    void errorFactory_populatesCorrectSubset() {
        AuditEventRecord rec = AuditEventRecord.error(ACTOR_ID, CORRELATION_ID, Channel.API);

        assertThat(rec.result()).isEqualTo(Result.ERROR);
        assertThat(rec.cacheHit()).isNull();
        assertThat(rec.accountCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("(4) Security C-2: record component names contain NO forbidden field names")
    void recordComponents_neverContainForbiddenFieldNames() {
        // Security C-2 enforcement — same check as KafkaAuditEventPublisherContractTest §4
        Set<String> componentNames = Arrays.stream(AuditEventRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        // FORBIDDEN: balance, accountId, accountNumber, accounts, balanceAsOf, currency
        assertThat(componentNames).doesNotContain(
                "balance",
                "accountId",
                "accountNumber",
                "accounts",
                "balanceAsOf",
                "currency"
        );

        // PERMITTED: exactly the 9 metadata fields
        assertThat(componentNames).containsExactlyInAnyOrder(
                "eventType", "actorId", "channel", "correlationId",
                "timestamp", "result", "purpose", "cacheHit", "accountCount"
        );
    }

    @Test
    @DisplayName("(5) All Channel enum values compile")
    void channelEnum_allValuesCompile() {
        assertThat(Channel.values()).containsExactlyInAnyOrder(
                Channel.MOBILE_BANKING, Channel.WEB, Channel.API);
    }

    @Test
    @DisplayName("(6) All Result enum values compile")
    void resultEnum_allValuesCompile() {
        assertThat(Result.values()).containsExactlyInAnyOrder(
                Result.SUCCESS, Result.FAILURE, Result.FORBIDDEN, Result.ERROR);
    }
}
