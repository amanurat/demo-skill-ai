package com.bank.transfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code transfer_outbox} table.
 *
 * <p>Written atomically with the business transaction (same DB tx).
 * A separate {@code @Scheduled} relay poller publishes unpublished rows to Kafka
 * (ADR-014). In v1 the poller is a stub — no Kafka publisher is wired.
 */
@Entity
@Table(name = "transfer_outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxJpaEntity {

    @Id
    @Column(name = "outbox_id", nullable = false, updatable = false)
    private UUID outboxId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "schema_version", nullable = false)
    private short schemaVersion;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;

    @Column(name = "headers", nullable = false, columnDefinition = "TEXT")
    private String headers;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "dispatched", nullable = false)
    private boolean dispatched;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
