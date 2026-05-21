package com.bank.balancedashboard.domain.policy;

import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Sorts a list of eligible accounts by balance DESC with accountId ASC tie-break,
 * assigns 1-based rank, and returns the ranked list.
 *
 * <p>Ranking rule (ADR-004 + BA BR-002):
 * <ol>
 *   <li>Primary: {@code balance} DESC (BigDecimal compareTo — exact, no float rounding)</li>
 *   <li>Tie-break: {@code accountId} ASC (UUID.compareTo — deterministic across requests)</li>
 * </ol>
 *
 * <p>FE MUST NOT re-sort — the server-returned order is authoritative (ADR-004 SA).
 * The {@code rank} field on each returned {@link AccountView} reflects the 1-based position.
 *
 * <p>Domain class — ZERO Spring/Kafka/Redis imports permitted in this package.
 */
public class Ranker {

    private static final Comparator<AccountView> RANK_ORDER =
            Comparator.comparing(AccountView::balance, Comparator.reverseOrder())
                    .thenComparing(AccountView::accountId);

    /**
     * Ranks the given list of eligible AccountViews.
     *
     * <p>The input list is not mutated. The returned list is a new list with
     * re-assigned 1-based {@code rank} values reflecting the sorted order.
     *
     * @param accounts eligible accounts (already filtered by EligibilityPolicy)
     * @return new list sorted by balance DESC, accountId ASC, with 1-based rank
     */
    public List<AccountView> rank(List<AccountView> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }

        List<AccountView> sorted = new ArrayList<>(accounts);
        sorted.sort(RANK_ORDER);

        List<AccountView> ranked = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            AccountView original = sorted.get(i);
            // Rebuild record with new 1-based rank
            ranked.add(new AccountView(
                    i + 1,                          // rank (1-based)
                    original.accountId(),
                    original.accountNumberMasked(),
                    original.accountType(),
                    original.balance(),
                    original.currency(),
                    original.balanceAsOf(),
                    original.isStale(),
                    original.displayLabel()
            ));
        }
        return List.copyOf(ranked);
    }
}
