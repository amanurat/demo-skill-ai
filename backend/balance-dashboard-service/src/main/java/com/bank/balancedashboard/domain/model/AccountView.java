package com.bank.balancedashboard.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-account row as presented to the customer.
 *
 * <p>Immutable record (no setters by construction — Java records are final fields).
 * Domain record — ZERO Spring/Kafka/Redis imports permitted in this package.
 *
 * <p>Key contracts (enforced by AccountViewTest):
 * <ul>
 *   <li>{@code balance} is {@link BigDecimal} — NEVER double/float (ADR banking gotcha)</li>
 *   <li>{@code accountNumberMasked} matches {@code ^\*+\d{4}$} (BR-007)</li>
 *   <li>{@code rank} is 1-based (ADR-004)</li>
 *   <li>{@code isStale} is server-computed: {@code now() - balanceAsOf > 60s} (BR-013)</li>
 * </ul>
 *
 * <p>Serialization: {@code balance} MUST be written as a JSON string (format {@code ^-?\d+\.\d{2}$}).
 * The REST controller uses a custom serializer / {@code @JsonSerialize} to enforce this.
 *
 * <p>OpenAPI field contract — {@code BalanceDashboardResponse.accounts[]} item schema.
 */
public record AccountView(
        int       rank,
        UUID      accountId,
        String    accountNumberMasked,
        AccountType accountType,
        BigDecimal balance,
        String    currency,
        Instant   balanceAsOf,
        boolean   isStale,
        String    displayLabel
) {

    /**
     * Compact canonical constructor — validates immutability invariants.
     *
     * @throws IllegalArgumentException if accountNumberMasked does not match ^\*+\d{4}$
     * @throws NullPointerException if any required non-null field is null
     */
    public AccountView {
        // balance MUST be BigDecimal — enforced by type system
        if (balance == null) {
            throw new NullPointerException("balance must not be null");
        }
        if (accountId == null) {
            throw new NullPointerException("accountId must not be null");
        }
        if (accountNumberMasked == null || !accountNumberMasked.matches("^\\*+\\d{4}$")) {
            throw new IllegalArgumentException(
                    "accountNumberMasked must match ^\\*+\\d{4}$, got: " + accountNumberMasked);
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got: " + rank);
        }
    }
}
