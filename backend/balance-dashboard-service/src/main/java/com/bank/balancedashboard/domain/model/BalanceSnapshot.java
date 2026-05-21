package com.bank.balancedashboard.domain.model;

import java.util.List;

/**
 * The unranked aggregate of all eligible accounts fetched from account-service.
 *
 * <p>Domain record — ZERO Spring/Kafka/Redis imports permitted in this package.
 * Produced by {@code AccountPort.fetchAccounts()} (already eligibility-filtered and
 * mapped by AccountClientAdapter). Consumed by {@code Ranker.rank()}.
 *
 * @param accounts non-null list of eligible accounts (may be empty — empty state AC-001-E1)
 */
public record BalanceSnapshot(List<AccountView> accounts) {

    /**
     * Compact constructor — ensures accounts is never null.
     *
     * @throws NullPointerException if accounts is null
     */
    public BalanceSnapshot {
        if (accounts == null) {
            throw new NullPointerException("accounts list must not be null");
        }
        // Defensive copy to ensure immutability
        accounts = List.copyOf(accounts);
    }
}
