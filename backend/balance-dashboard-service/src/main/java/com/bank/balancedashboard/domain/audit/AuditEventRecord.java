package com.bank.balancedashboard.domain.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit-event payload — ONLY the metadata fields permitted by
 * Security C-2 (PDPA §22 data minimization). Maps 1:1 to Avro v2
 * AuditEventRecorded (com.bank.compliance.audit.v2).
 *
 * <p>FORBIDDEN fields (Security C-2 — enforced by KafkaAuditEventPublisherContractTest):
 * <ul>
 *   <li>balance — per-account or aggregated BigDecimal value</li>
 *   <li>accountId — internal UUID of any account</li>
 *   <li>accountNumber — full OR masked account number string</li>
 *   <li>accounts — the response accounts array or any subset of it</li>
 *   <li>balanceAsOf — per-account ledger timestamp</li>
 *   <li>currency — ISO 4217 per-account code</li>
 * </ul>
 *
 * <p>Domain record — ZERO Spring/Kafka/Redis imports permitted in this file.
 *
 * <p>ADR-007 §2.2 — type signature is the contract.
 */
public record AuditEventRecord(
        String  eventType,     // "BALANCE_INQUIRY"
        UUID    actorId,       // JWT sub — the acting customer
        Channel channel,       // MOBILE_BANKING | WEB | API
        String  correlationId, // OTel trace ID (lowercase UUID)
        Instant timestamp,     // event emit time, UTC
        Result  result,        // SUCCESS | FORBIDDEN | ERROR | FAILURE
        String  purpose,       // "balance-inquiry" — PDPA purpose limitation
        Boolean cacheHit,      // true if served from Redis; null for non-SUCCESS paths
        Integer accountCount   // aggregate count; 0 for empty/FORBIDDEN/ERROR
) {

    /**
     * Factory: successful balance inquiry (cache HIT or MISS).
     * Called from BalanceDashboardService after building response, regardless of cache path.
     * BR-014: NEVER short-circuited by cache.
     */
    public static AuditEventRecord success(UUID actorId,
                                           String correlationId,
                                           Channel channel,
                                           boolean cacheHit,
                                           int accountCount) {
        return new AuditEventRecord(
                "BALANCE_INQUIRY",
                actorId,
                channel,
                correlationId,
                Instant.now(),
                Result.SUCCESS,
                "balance-inquiry",
                cacheHit,
                accountCount
        );
    }

    /**
     * Factory: IDOR header mismatch detected by IborCheckFilter.
     * ADR-006 §2.5 call site 2 — emitted before returning HTTP 403.
     * actorId is JWT sub (the authenticated actor), NOT the tampered header value.
     */
    public static AuditEventRecord forbidden(UUID actorId,
                                             String correlationId,
                                             Channel channel) {
        return new AuditEventRecord(
                "BALANCE_INQUIRY",
                actorId,
                channel,
                correlationId,
                Instant.now(),
                Result.FORBIDDEN,
                "balance-inquiry",
                null,
                0
        );
    }

    /**
     * Factory: upstream failure (CB-open, timeout, retries exhausted, HTTP 5xx).
     * ADR-007 §2.5 call site 3 — emitted before re-throwing UpstreamUnavailableException.
     */
    public static AuditEventRecord error(UUID actorId,
                                         String correlationId,
                                         Channel channel) {
        return new AuditEventRecord(
                "BALANCE_INQUIRY",
                actorId,
                channel,
                correlationId,
                Instant.now(),
                Result.ERROR,
                "balance-inquiry",
                null,
                0
        );
    }
}
