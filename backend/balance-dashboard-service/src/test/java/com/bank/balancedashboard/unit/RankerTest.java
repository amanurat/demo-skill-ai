package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.policy.Ranker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Ranker}.
 * ZERO Spring context — pure domain logic.
 * Coverage target: >= 95% (critical path per task-plan Layer 6).
 */
class RankerTest {

    private Ranker ranker;

    @BeforeEach
    void setUp() {
        ranker = new Ranker();
    }

    @Test
    @DisplayName("(1) 3 distinct balances -> sorted by balance DESC")
    void threeDistinctBalances_sortedDescending() {
        // Given
        AccountView high   = accountView(UUID.randomUUID(), "300.00");
        AccountView medium = accountView(UUID.randomUUID(), "200.00");
        AccountView low    = accountView(UUID.randomUUID(), "100.00");
        List<AccountView> input = List.of(low, high, medium); // intentionally unordered

        // When
        List<AccountView> result = ranker.rank(input);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).balance()).isEqualByComparingTo("300.00");
        assertThat(result.get(1).balance()).isEqualByComparingTo("200.00");
        assertThat(result.get(2).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("(2) Equal balances -> tie-break by accountId ASC")
    void equalBalances_tieBreakAccountIdAsc() {
        // Given: two accounts with equal balance — UUID ordering determines rank
        UUID idA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID idB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        AccountView accountA = accountView(idA, "100.00");
        AccountView accountB = accountView(idB, "100.00");

        // When
        List<AccountView> result = ranker.rank(List.of(accountB, accountA));

        // Then: idA < idB lexicographically → A comes first (ASC tie-break)
        assertThat(result.get(0).accountId()).isEqualTo(idA);
        assertThat(result.get(1).accountId()).isEqualTo(idB);
    }

    @Test
    @DisplayName("(3) All equal balances -> stable order by accountId ASC")
    void allEqualBalances_stableByAccountIdAsc() {
        // Given: three accounts with equal balance
        UUID id1 = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("22222222-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("33333333-0000-0000-0000-000000000003");

        List<AccountView> input = List.of(
                accountView(id3, "50.00"),
                accountView(id1, "50.00"),
                accountView(id2, "50.00")
        );

        // When
        List<AccountView> result = ranker.rank(input);

        // Then: sorted by accountId ASC
        assertThat(result.get(0).accountId()).isEqualTo(id1);
        assertThat(result.get(1).accountId()).isEqualTo(id2);
        assertThat(result.get(2).accountId()).isEqualTo(id3);
    }

    @Test
    @DisplayName("(4) Single account -> rank 1")
    void singleAccount_rankIsOne() {
        AccountView account = accountView(UUID.randomUUID(), "5000.00");

        List<AccountView> result = ranker.rank(List.of(account));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("(5) Empty list -> empty result")
    void emptyList_returnsEmptyList() {
        List<AccountView> result = ranker.rank(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("(6) rank field is 1-based integer")
    void rankField_isOneBased() {
        List<AccountView> input = List.of(
                accountView(UUID.randomUUID(), "300.00"),
                accountView(UUID.randomUUID(), "200.00"),
                accountView(UUID.randomUUID(), "100.00")
        );

        List<AccountView> result = ranker.rank(input);

        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(1).rank()).isEqualTo(2);
        assertThat(result.get(2).rank()).isEqualTo(3);
    }

    // ===== Helpers =====

    private AccountView accountView(UUID id, String balance) {
        return new AccountView(
                1, // placeholder rank (Ranker will reassign)
                id,
                "****1234",
                AccountType.SAVINGS,
                new BigDecimal(balance),
                "THB",
                Instant.now(),
                false,
                "account.type.savings"
        );
    }
}
