package com.bank.transfer.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object wrapping a bank account UUID identifier.
 *
 * <p>Prevents primitive obsession: an {@code AccountId} is never confused
 * with a raw {@code UUID} that might be a transfer id or customer id.
 */
public final class AccountId {

    private final UUID id;

    /**
     * Constructs an AccountId from a UUID.
     *
     * @param id must not be null
     */
    public AccountId(final UUID id) {
        this.id = Objects.requireNonNull(id, "AccountId must not be null");
    }

    /**
     * Parses an AccountId from a UUID string.
     *
     * @param uuidString string representation of the UUID
     * @return AccountId
     */
    public static AccountId of(final String uuidString) {
        return new AccountId(UUID.fromString(uuidString));
    }

    /**
     * Returns the underlying UUID.
     *
     * @return the UUID value
     */
    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountId accountId)) {
            return false;
        }
        return Objects.equals(id, accountId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Mask to last 4 chars for safe logging (per ADR-015 / NFR compliance_pdpa)
        String uuidStr = id.toString();
        return "****-" + uuidStr.substring(uuidStr.length() - 4);
    }

    /**
     * Returns the full UUID string (for persistence / wire only — do not log directly).
     *
     * @return UUID string representation
     */
    public String toFullString() {
        return id.toString();
    }
}
