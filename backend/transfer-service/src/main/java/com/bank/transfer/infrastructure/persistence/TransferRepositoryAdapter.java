package com.bank.transfer.infrastructure.persistence;

import com.bank.transfer.application.port.out.TransferRepository;
import com.bank.transfer.domain.model.AccountId;
import com.bank.transfer.domain.model.Money;
import com.bank.transfer.domain.model.Transfer;
import com.bank.transfer.domain.model.TransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing {@link TransferRepository} via Spring Data JPA.
 *
 * <p>Performs bidirectional mapping between the domain {@link Transfer} aggregate
 * and the {@link TransferJpaEntity} JPA entity. No domain types leak into JPA.
 */
@Component
@RequiredArgsConstructor
public class TransferRepositoryAdapter implements TransferRepository {

    private final TransferJpaRepository jpaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>If the entity does not exist yet (new transfer), sets {@code createdAt}
     * to now. Always updates {@code updatedAt}.
     */
    @Override
    public Transfer save(final Transfer transfer) {
        TransferJpaEntity entity = toEntity(transfer);
        TransferJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Transfer> findById(final UUID transferId) {
        return jpaRepository.findById(transferId).map(this::toDomain);
    }

    // --- Mapping helpers ---

    private TransferJpaEntity toEntity(final Transfer transfer) {
        // Check if already exists to preserve createdAt
        TransferJpaEntity entity = jpaRepository.findById(transfer.getTransferId())
            .orElseGet(TransferJpaEntity::new);

        entity.setTransferId(transfer.getTransferId());
        entity.setReferenceNumber(transfer.getReferenceNumber());
        entity.setSourceAccountId(transfer.getSourceAccountId().getId());
        entity.setDestinationAccountId(transfer.getDestinationAccountId().getId());
        entity.setInitiatorUserId(transfer.getInitiatorUserId());
        entity.setInitiatorCustomerId(transfer.getInitiatorCustomerId());
        entity.setAmount(transfer.getAmount().getAmount());
        entity.setCurrency(transfer.getAmount().getCurrency().getCurrencyCode());
        entity.setMemo(transfer.getMemo());
        entity.setStatus(transfer.getStatus().name());
        entity.setChannel(transfer.getChannel());
        entity.setFailureReason(transfer.getFailureReason());
        entity.setCorrelationId(transfer.getCorrelationId());
        entity.setCompletedAt(transfer.getCompletedAt());

        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        return entity;
    }

    private Transfer toDomain(final TransferJpaEntity entity) {
        return Transfer.reconstitute(
            entity.getTransferId(),
            entity.getReferenceNumber(),
            new AccountId(entity.getSourceAccountId()),
            new AccountId(entity.getDestinationAccountId()),
            entity.getInitiatorUserId(),
            entity.getInitiatorCustomerId(),
            new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
            entity.getMemo(),
            entity.getChannel(),
            entity.getCorrelationId(),
            TransferStatus.valueOf(entity.getStatus()),
            entity.getFailureReason(),
            entity.getCompletedAt(),
            entity.getVersion()
        );
    }
}
