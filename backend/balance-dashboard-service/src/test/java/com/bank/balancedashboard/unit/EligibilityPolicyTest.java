package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.policy.EligibilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EligibilityPolicy}.
 * ZERO Spring context — pure domain logic.
 * Coverage target: >= 95% (critical path per task-plan Layer 6).
 */
class EligibilityPolicyTest {

    private EligibilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = EligibilityPolicy.withDefaults();
    }

    @Test
    @DisplayName("(1) ACTIVE SAVINGS -> eligible")
    void activeSavings_isEligible() {
        assertThat(policy.isEligible("ACTIVE", AccountType.SAVINGS)).isTrue();
    }

    @Test
    @DisplayName("(2) ACTIVE CURRENT -> eligible")
    void activeCurrent_isEligible() {
        assertThat(policy.isEligible("ACTIVE", AccountType.CURRENT)).isTrue();
    }

    @Test
    @DisplayName("(3) ACTIVE FIXED_DEPOSIT -> eligible")
    void activeFixedDeposit_isEligible() {
        assertThat(policy.isEligible("ACTIVE", AccountType.FIXED_DEPOSIT)).isTrue();
    }

    @Test
    @DisplayName("(4) DORMANT SAVINGS -> excluded (BR-003)")
    void dormantSavings_notEligible() {
        assertThat(policy.isEligible("DORMANT", AccountType.SAVINGS)).isFalse();
    }

    @Test
    @DisplayName("(5) CLOSED SAVINGS -> excluded")
    void closedSavings_notEligible() {
        assertThat(policy.isEligible("CLOSED", AccountType.SAVINGS)).isFalse();
    }

    @Test
    @DisplayName("(6) FROZEN SAVINGS -> excluded")
    void frozenSavings_notEligible() {
        assertThat(policy.isEligible("FROZEN", AccountType.SAVINGS)).isFalse();
    }

    @Test
    @DisplayName("(7) INACTIVE SAVINGS -> excluded")
    void inactiveSavings_notEligible() {
        assertThat(policy.isEligible("INACTIVE", AccountType.SAVINGS)).isFalse();
    }

    @Test
    @DisplayName("(8) ACTIVE LOAN -> excluded by type (BR-004) — LOAN not in AccountType enum")
    void activeLoan_notInEligibleTypes_notEligible() {
        // LOAN is not an AccountType enum value in BDS (only SAVINGS/CURRENT/FIXED_DEPOSIT).
        // Adapter filters LOAN before creating AccountView. Verify policy never sees it:
        assertThat(policy.eligibleTypes()).doesNotContain(
                // We verify none of the eligible types is "LOAN" conceptually
                // by confirming eligible types only has the 3 permitted types
        );
        assertThat(policy.eligibleTypes()).containsExactlyInAnyOrder(
                AccountType.SAVINGS, AccountType.CURRENT, AccountType.FIXED_DEPOSIT);
    }

    @Test
    @DisplayName("(9) Config flip: FIXED_DEPOSIT excluded when not in eligible-types")
    void configFlip_excludesFixedDeposit_assumptionTL001Fallback() {
        // ASSUMPTION-TL-001 fallback: set eligible-types=SAVINGS,CURRENT (no FIXED_DEPOSIT)
        EligibilityPolicy restrictedPolicy = new EligibilityPolicy(
                EnumSet.of(AccountType.SAVINGS, AccountType.CURRENT)
        );

        assertThat(restrictedPolicy.isEligible("ACTIVE", AccountType.SAVINGS)).isTrue();
        assertThat(restrictedPolicy.isEligible("ACTIVE", AccountType.CURRENT)).isTrue();
        assertThat(restrictedPolicy.isEligible("ACTIVE", AccountType.FIXED_DEPOSIT)).isFalse();
    }

    @Test
    @DisplayName("(10) fromString factory parses comma-separated types correctly")
    void fromString_parsesCommaSeparatedTypes() {
        EligibilityPolicy parsed = EligibilityPolicy.fromString("SAVINGS,CURRENT");
        assertThat(parsed.isEligible("ACTIVE", AccountType.SAVINGS)).isTrue();
        assertThat(parsed.isEligible("ACTIVE", AccountType.CURRENT)).isTrue();
        assertThat(parsed.isEligible("ACTIVE", AccountType.FIXED_DEPOSIT)).isFalse();
    }

    @Test
    @DisplayName("(11) fromString with defaults fallback when empty string")
    void fromString_emptyString_usesDefaults() {
        EligibilityPolicy defaultPolicy = EligibilityPolicy.fromString("");
        assertThat(defaultPolicy.eligibleTypes()).containsExactlyInAnyOrder(
                AccountType.SAVINGS, AccountType.CURRENT, AccountType.FIXED_DEPOSIT);
    }
}
