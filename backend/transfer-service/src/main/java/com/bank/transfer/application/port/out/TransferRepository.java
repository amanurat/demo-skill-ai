package com.bank.transfer.application.port.out;

import com.bank.transfer.domain.model.Transfer;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and loading {@link Transfer} aggregate roots.
 *
 * <p>The infrastructure layer (JPA adapter) implements this interface.
 * The domain and application layers depend only on this interface — never on JPA directly.
 */
public interface TransferRepository {

    /**
     * Saves a new or updated transfer.
     *
     * @param transfer the aggregate to persist
     * @return the saved transfer (may have updated version or generated fields)
     */
    Transfer save(Transfer transfer);

    /**
     * Loads a transfer by its primary key.
     *
     * @param transferId the transfer UUID
     * @return an Optional containing the transfer, or empty if not found
     */
    Optional<Transfer> findById(UUID transferId);
}
