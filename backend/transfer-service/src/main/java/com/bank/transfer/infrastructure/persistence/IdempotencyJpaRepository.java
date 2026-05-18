package com.bank.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IdempotencyJpaEntity}.
 */
@Repository
public interface IdempotencyJpaRepository
    extends JpaRepository<IdempotencyJpaEntity, IdempotencyJpaEntity.PK> {

    /**
     * Finds a non-expired idempotency record by composite key.
     *
     * @param keyHash         SHA-256 hex of the Idempotency-Key
     * @param ownerCustomerId customer UUID
     * @param now             current timestamp used to filter expired records
     * @return the record if present and not expired
     */
    @Query("SELECT e FROM IdempotencyJpaEntity e "
        + "WHERE e.keyHash = :keyHash "
        + "AND e.ownerCustomerId = :ownerCustomerId "
        + "AND e.expiresAt > :now")
    Optional<IdempotencyJpaEntity> findValid(
        @Param("keyHash") String keyHash,
        @Param("ownerCustomerId") UUID ownerCustomerId,
        @Param("now") Instant now
    );
}
