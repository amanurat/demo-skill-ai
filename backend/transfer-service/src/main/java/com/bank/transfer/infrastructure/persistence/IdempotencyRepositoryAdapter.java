package com.bank.transfer.infrastructure.persistence;

import com.bank.transfer.application.port.out.IdempotencyRecord;
import com.bank.transfer.application.port.out.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing {@link IdempotencyRepository} via Spring Data JPA.
 *
 * <p>All methods must be called within an active transaction (the transaction
 * boundary is at the use-case layer per ADR-013).
 */
@Component
@RequiredArgsConstructor
public class IdempotencyRepositoryAdapter implements IdempotencyRepository {

    private final IdempotencyJpaRepository jpaRepository;

    /** {@inheritDoc} */
    @Override
    public Optional<IdempotencyRecord> findValid(
            final String keyHash,
            final UUID ownerCustomerId) {
        return jpaRepository.findValid(keyHash, ownerCustomerId, Instant.now())
            .map(this::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inserts with {@code resultStatus=PENDING} and {@code cachedResponseBody='{}'}.
     * The caller is responsible for calling {@link #updateResult} after business logic completes.
     */
    @Override
    public void insertPending(
            final String keyHash,
            final UUID ownerCustomerId,
            final String requestChecksum,
            final String correlationId,
            final Duration ttl) {
        IdempotencyJpaEntity entity = new IdempotencyJpaEntity();
        entity.setKeyHash(keyHash);
        entity.setOwnerCustomerId(ownerCustomerId);
        entity.setRequestChecksum(requestChecksum);
        entity.setResultStatus("PENDING");
        entity.setCachedResponseCode(0);
        entity.setCachedResponseBody("{}");
        entity.setCorrelationId(correlationId);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(ttl));
        jpaRepository.save(entity);
    }

    /** {@inheritDoc} */
    @Override
    public void updateResult(
            final String keyHash,
            final UUID ownerCustomerId,
            final UUID transferId,
            final String resultStatus,
            final int cachedResponseCode,
            final String cachedResponseBody) {
        IdempotencyJpaEntity entity = jpaRepository
            .findById(new IdempotencyJpaEntity.PK(keyHash, ownerCustomerId))
            .orElseThrow(() -> new IllegalStateException(
                "Idempotency record not found for update: keyHash=" + keyHash));
        entity.setTransferId(transferId);
        entity.setResultStatus(resultStatus);
        entity.setCachedResponseCode(cachedResponseCode);
        entity.setCachedResponseBody(cachedResponseBody);
        jpaRepository.save(entity);
    }

    // --- Mapping helpers ---

    private IdempotencyRecord toDomain(final IdempotencyJpaEntity entity) {
        return new IdempotencyRecord(
            entity.getKeyHash(),
            entity.getOwnerCustomerId(),
            entity.getTransferId(),
            entity.getRequestChecksum(),
            entity.getResultStatus(),
            entity.getCachedResponseCode(),
            entity.getCachedResponseBody(),
            entity.getExpiresAt()
        );
    }
}
