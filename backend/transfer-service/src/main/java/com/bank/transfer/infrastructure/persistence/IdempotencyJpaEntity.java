package com.bank.transfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for the {@code transfer_idempotency} table.
 *
 * <p>Composite primary key: ({@code keyHash}, {@code ownerCustomerId}).
 * The {@code cachedResponseBody} is stored as TEXT (Postgres JSONB at DB level).
 */
@Entity
@Table(name = "transfer_idempotency")
@IdClass(IdempotencyJpaEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyJpaEntity {

    @Id
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Id
    @Column(name = "owner_customer_id", nullable = false)
    private UUID ownerCustomerId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "request_checksum", nullable = false, length = 64)
    private String requestChecksum;

    @Column(name = "result_status", nullable = false, length = 32)
    private String resultStatus;

    @Column(name = "cached_response_code", nullable = false)
    private int cachedResponseCode;

    /**
     * Stored as JSONB in PostgreSQL. Mapped as TEXT for portability with H2 in tests.
     * The column type annotation is handled at DB schema level by Flyway.
     */
    @Column(name = "cached_response_body", nullable = false, columnDefinition = "TEXT")
    private String cachedResponseBody;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Composite primary key class for {@link IdempotencyJpaEntity}.
     */
    public static class PK implements Serializable {

        private String keyHash;
        private UUID ownerCustomerId;

        public PK() {}

        public PK(final String keyHash, final UUID ownerCustomerId) {
            this.keyHash = keyHash;
            this.ownerCustomerId = ownerCustomerId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PK pk)) {
                return false;
            }
            return Objects.equals(keyHash, pk.keyHash)
                && Objects.equals(ownerCustomerId, pk.ownerCustomerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyHash, ownerCustomerId);
        }
    }
}
