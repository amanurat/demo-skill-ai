package com.bank.transfer.domain.exception;

import java.util.UUID;

/**
 * Thrown when a transfer with the requested id does not exist.
 * Maps to HTTP 404 with code {@code TRANSFER_NOT_FOUND}.
 */
public final class TransferNotFoundException extends DomainException {

    /** Error code per ADR-013 taxonomy. */
    public static final String CODE = "TRANSFER_NOT_FOUND";

    /**
     * Constructs a TransferNotFoundException for a specific transfer id.
     *
     * @param transferId the UUID that was not found
     */
    public TransferNotFoundException(final UUID transferId) {
        super("No transfer exists with id " + transferId, CODE);
    }
}
