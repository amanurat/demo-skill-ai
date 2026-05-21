package com.bank.balancedashboard.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;

/**
 * Jackson configuration for balance-dashboard-service.
 *
 * <p>Critical settings:
 * <ul>
 *   <li>WRITE_BIGDECIMAL_AS_PLAIN=true — prevents scientific notation for large balances</li>
 *   <li>No float coercion — BigDecimal.class is never coerced to float/double</li>
 *   <li>JavaTimeModule — Instant serialized as ISO 8601 string (not epoch millis)</li>
 *   <li>WRITE_DATES_AS_TIMESTAMPS=false — Instant as string (OpenAPI date-time format)</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Instant as ISO 8601 string (not epoch millis) — OpenAPI date-time format
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // BigDecimal serialized as plain string (no scientific notation)
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                // Fail gracefully on unknown fields (forward compatibility)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Null fields excluded from JSON output (clean responses)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
