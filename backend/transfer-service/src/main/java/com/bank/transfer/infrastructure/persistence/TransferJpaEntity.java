package com.bank.transfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code transfers} table.
 *
 * <p>Intentionally kept separate from the domain {@link com.bank.transfer.domain.model.Transfer}
 * aggregate to preserve hexagonal architecture purity (infrastructure adapts to domain, not
 * the other way around).
 *
 * <p>Mapping rules (per ADR-016):
 * <ul>
 *   <li>Money amount → NUMERIC(19,4)</li>
 *   <li>Enums stored as VARCHAR (status, channel)</li>
 *   <li>Optimistic lock via {@code @Version} on {@code version}</li>
 * </ul>
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
public class TransferJpaEntity {

    @Id
    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Column(name = "reference_number", nullable = false, length = 32)
    private String referenceNumber;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "initiator_user_id", nullable = false)
    private UUID initiatorUserId;

    @Column(name = "initiator_customer_id", nullable = false)
    private UUID initiatorCustomerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "memo", length = 200)
    private String memo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
