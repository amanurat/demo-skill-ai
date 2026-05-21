package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.infrastructure.rest.LogMasking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LogMasking} — verifies F-1 fix (CWE-532, OWASP A09:2021, PDPA §22).
 *
 * <p>These tests assert that neither maskId nor maskKey ever exposes a full UUID
 * in their output, confirming PII masking at the call site.
 */
@DisplayName("LogMasking — PII masking for structured logs")
class LogMaskingTest {

    /** Regex that matches a full UUID: 8-4-4-4-12 lowercase hex. */
    private static final Pattern FULL_UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    // -----------------------------------------------------------------------
    // maskId(UUID) tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("maskId(UUID) does NOT output a full UUID string")
    void maskId_uuid_doesNotContainFullUuid() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String masked = LogMasking.maskId(id);
        assertThat(FULL_UUID_PATTERN.matcher(masked).find())
                .as("maskId(UUID) must NOT contain a full UUID — found: " + masked)
                .isFalse();
    }

    @Test
    @DisplayName("maskId(UUID) keeps first 8 hex chars and appends ****")
    void maskId_uuid_keepsFirstEightChars() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String masked = LogMasking.maskId(id);
        assertThat(masked).startsWith("550e8400");
        assertThat(masked).endsWith("****");
    }

    @Test
    @DisplayName("maskId(UUID) handles null UUID gracefully")
    void maskId_uuid_null_returnsStar() {
        UUID nullId = null;
        String masked = LogMasking.maskId(nullId);
        assertThat(masked).isEqualTo("***");
        assertThat(FULL_UUID_PATTERN.matcher(masked).find()).isFalse();
    }

    // -----------------------------------------------------------------------
    // maskId(String) tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("maskId(String) does NOT output a full UUID string")
    void maskId_string_doesNotContainFullUuid() {
        String id = "550e8400-e29b-41d4-a716-446655440000";
        String masked = LogMasking.maskId(id);
        assertThat(FULL_UUID_PATTERN.matcher(masked).find())
                .as("maskId(String) must NOT contain a full UUID — found: " + masked)
                .isFalse();
    }

    @Test
    @DisplayName("maskId(String) handles null gracefully")
    void maskId_string_null_returnsStar() {
        assertThat(LogMasking.maskId((String) null)).isEqualTo("***");
    }

    @Test
    @DisplayName("maskId(String) handles string shorter than 8 chars gracefully")
    void maskId_string_tooShort_returnsStar() {
        assertThat(LogMasking.maskId("short")).isEqualTo("***");
    }

    // -----------------------------------------------------------------------
    // maskKey(String) tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("maskKey does NOT contain full UUID portion in output")
    void maskKey_doesNotContainFullUuid() {
        String key = "balance-dashboard:customer:550e8400-e29b-41d4-a716-446655440000";
        String masked = LogMasking.maskKey(key);
        assertThat(FULL_UUID_PATTERN.matcher(masked).find())
                .as("maskKey must NOT contain a full UUID — found: " + masked)
                .isFalse();
    }

    @Test
    @DisplayName("maskKey preserves prefix segment before UUID")
    void maskKey_preservesPrefixSegment() {
        String key = "balance-dashboard:customer:550e8400-e29b-41d4-a716-446655440000";
        String masked = LogMasking.maskKey(key);
        assertThat(masked).startsWith("balance-dashboard:customer:");
        assertThat(masked).contains("550e8400");
        assertThat(masked).endsWith("****");
    }

    @Test
    @DisplayName("maskKey handles null gracefully")
    void maskKey_null_returnsStar() {
        assertThat(LogMasking.maskKey(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("maskKey with alternate key prefix also masks UUID")
    void maskKey_alternatePrefix_doesNotContainFullUuid() {
        // bd:dash: shorthand prefix variant
        String key = "bd:dash:a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String masked = LogMasking.maskKey(key);
        assertThat(FULL_UUID_PATTERN.matcher(masked).find())
                .as("maskKey with alternate prefix must NOT contain a full UUID — found: " + masked)
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Regression: multiple calls produce consistent output
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("maskId is idempotent — same UUID always produces same masked output")
    void maskId_idempotent() {
        UUID id = UUID.randomUUID();
        String first = LogMasking.maskId(id);
        String second = LogMasking.maskId(id);
        assertThat(first).isEqualTo(second);
    }
}
