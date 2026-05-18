package com.bank.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TransferJpaEntity}.
 *
 * <p>Only native JPA operations are used here. Domain-level queries are
 * expressed via the {@link com.bank.transfer.application.port.out.TransferRepository}
 * port, implemented by {@link TransferRepositoryAdapter}.
 */
@Repository
public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, UUID> {
}
