package com.bank.balancedashboard.application.port.out;

import com.bank.balancedashboard.domain.audit.AuditEventRecord;

/**
 * Secondary (outbound) port for emitting audit events.
 * Implemented by {@code KafkaAuditEventPublisher} in the infrastructure layer.
 *
 * <p>Contract (ADR-007 §2.1):
 * <ul>
 *   <li>{@link #publish} MUST NOT throw to the caller under any circumstance.
 *       Kafka unavailability, serialization failures, and all other errors MUST be
 *       logged (WARN) and metered — the dashboard request still returns 200 to the user.</li>
 *   <li>{@link #publish} MUST be called for EVERY request outcome — SUCCESS, FORBIDDEN,
 *       ERROR — including cache hits (BR-014). The cache layer NEVER short-circuits audit.</li>
 *   <li>Implementations MUST ensure the emitted record carries ONLY the fields defined
 *       in {@link AuditEventRecord}. Forbidden fields per Security C-2: balance, accountId,
 *       accountNumber, accounts[], balanceAsOf, currency.</li>
 * </ul>
 */
public interface AuditEventPublisher {

    /**
     * Emits an audit event asynchronously (fire-and-forget — no {@code .get()}).
     *
     * <p>This method MUST NOT throw. Any internal exception is swallowed after logging.
     *
     * @param record the metadata-only audit record (Security C-2 compliant)
     */
    void publish(AuditEventRecord record);
}
