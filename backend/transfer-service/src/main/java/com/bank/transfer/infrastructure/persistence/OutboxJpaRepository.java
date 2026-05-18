package com.bank.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxJpaEntity}.
 *
 * <p>In v1 only {@code save()} is used (write path from {@link OutboxEventPublisher}).
 * A future relay poller will use a custom {@code @Lock(PESSIMISTIC_WRITE)} query
 * with {@code SKIP LOCKED} to claim rows for dispatch to Kafka.
 */
@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {
}
