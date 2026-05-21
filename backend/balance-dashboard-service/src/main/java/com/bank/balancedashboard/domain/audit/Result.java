package com.bank.balancedashboard.domain.audit;

/**
 * Outcome of a balance inquiry attempt recorded in the audit trail.
 * Domain enum — ZERO Spring/Kafka/Redis imports permitted in this package.
 *
 * Maps 1:1 to Avro v2 AuditEventRecorded.result enum values.
 */
public enum Result {
    /** Request completed successfully; accounts returned to authorized customer. */
    SUCCESS,

    /** Request processing failed due to upstream unavailability or internal error. */
    FAILURE,

    /**
     * IDOR attempt detected — X-Customer-Id header did not match JWT sub,
     * or token lacks required scope. Audit FORBIDDEN before returning 403.
     */
    FORBIDDEN,

    /**
     * Upstream service (account-service) unavailable after all Resilience4j
     * attempts exhausted. 503 returned to caller.
     */
    ERROR
}
