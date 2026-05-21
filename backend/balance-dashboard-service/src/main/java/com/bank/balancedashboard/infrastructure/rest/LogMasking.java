package com.bank.balancedashboard.infrastructure.rest;

import java.util.UUID;

/**
 * Centralized PII masking utilities for structured logging.
 *
 * <p>Resolves Security Finding F-1 (CWE-532, OWASP A09:2021, PDPA §22):
 * customerId and accountId UUIDs MUST NOT appear unmasked in any log output.
 *
 * <p>Placed in {@code infrastructure.rest} package because logging is an infrastructure
 * cross-cutting concern — domain layer must NOT import this class.
 *
 * <p>Defense-in-depth: This utility is the primary masking control at the call site.
 * The secondary control is the logback-spring.xml regex filter (F-2 fix) which acts
 * as a safety net for any masking gaps.
 *
 * <p>Usage:
 * <pre>{@code
 * log.debug("balance-dashboard customerId={}", LogMasking.maskId(customerId));
 * log.warn("cache.get.failed key={}", LogMasking.maskKey(key));
 * }</pre>
 */
public final class LogMasking {

    private LogMasking() {
        // utility class — no instantiation
    }

    /**
     * Masks a UUID by keeping only the first 8 hex characters and replacing the rest with {@code ****}.
     *
     * <p>Example: {@code 550e8400-e29b-41d4-a716-446655440000} → {@code 550e8400****}
     *
     * <p>Accepts both {@link UUID} and String representations. Returns {@code "***"} for
     * null or strings shorter than 8 characters to avoid ArrayIndexOutOfBoundsException.
     *
     * @param id the UUID to mask
     * @return masked representation safe for logging
     */
    public static String maskId(UUID id) {
        if (id == null) return "***";
        return maskId(id.toString());
    }

    /**
     * Masks a UUID string by keeping only the first 8 hex characters.
     *
     * @param id the UUID string to mask (may be null)
     * @return masked representation safe for logging
     */
    public static String maskId(String id) {
        if (id == null || id.length() < 8) return "***";
        return id.substring(0, 8) + "****";
    }

    /**
     * Masks a Redis cache key by logging only up to the first UUID segment.
     *
     * <p>Cache keys follow the pattern: {@code balance-dashboard:customer:{UUID}}.
     * This method logs everything before the UUID, then appends the first 8 chars of the
     * UUID segment followed by {@code ****}.
     *
     * <p>Example:
     * {@code balance-dashboard:customer:550e8400-e29b-41d4-a716-446655440000}
     * → {@code balance-dashboard:customer:550e8400****}
     *
     * <p>If no UUID-like segment is found (no colon followed by ≥8 chars), falls back to
     * returning the key prefix up to position 8 with {@code ****}.
     *
     * @param key the full Redis cache key (may be null)
     * @return masked key safe for logging
     */
    public static String maskKey(String key) {
        if (key == null) return "***";
        // Find the last ':' delimiter — UUID segment follows it
        int lastColon = key.lastIndexOf(':');
        if (lastColon >= 0 && key.length() > lastColon + 8) {
            String prefix = key.substring(0, lastColon + 1);
            String uuidPart = key.substring(lastColon + 1);
            return prefix + uuidPart.substring(0, 8) + "****";
        }
        // Fallback: no colon found — treat entire key as sensitive
        if (key.length() >= 8) {
            return key.substring(0, 8) + "****";
        }
        return "***";
    }
}
