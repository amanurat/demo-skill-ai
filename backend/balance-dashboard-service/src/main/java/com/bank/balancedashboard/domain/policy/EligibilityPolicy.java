package com.bank.balancedashboard.domain.policy;

import com.bank.balancedashboard.domain.model.AccountType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines which account types are eligible for the balance dashboard.
 *
 * <p>Eligible rule (BA BR-003 + BR-004 + OPEN-003 + OPEN-004):
 * <ul>
 *   <li>status = ACTIVE only (DORMANT, CLOSED, FROZEN, INACTIVE excluded)</li>
 *   <li>accountType IN (SAVINGS, CURRENT, FIXED_DEPOSIT) only (LOAN, CREDIT_CARD excluded)</li>
 * </ul>
 *
 * <p>ASSUMPTION-TL-001 fallback: eligible types are configurable via
 * {@code balance-dashboard.eligible-types} property. If FIXED_DEPOSIT turns out
 * to expose principal-only balance (not principal + accrued interest), PM can flip
 * config to exclude FIXED_DEPOSIT without any code change. See implementation-notes §11.
 *
 * <p>Domain component — ZERO Spring/Kafka/Redis imports permitted in this package.
 * The {@code eligibleTypes} set is injected at construction time (from application-layer
 * config — the Spring {@code @Value} annotation lives in the infrastructure wiring,
 * NOT in this class). This keeps the domain pure while still supporting the config flip.
 */
public class EligibilityPolicy {

    /** Default eligible types per BA BR-004 + OPEN-004. */
    public static final Set<AccountType> DEFAULT_ELIGIBLE_TYPES =
            EnumSet.of(AccountType.SAVINGS, AccountType.CURRENT, AccountType.FIXED_DEPOSIT);

    /** Active status string constant — must match account-service AccountInfo.status values. */
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final Set<AccountType> eligibleTypes;

    /**
     * Constructs with the given eligible types set.
     * Use {@link #withDefaults()} for production default (SAVINGS, CURRENT, FIXED_DEPOSIT).
     */
    public EligibilityPolicy(Set<AccountType> eligibleTypes) {
        this.eligibleTypes = EnumSet.copyOf(eligibleTypes);
    }

    /**
     * Factory: creates policy with default eligible types.
     * Used when no configuration override is provided.
     */
    public static EligibilityPolicy withDefaults() {
        return new EligibilityPolicy(DEFAULT_ELIGIBLE_TYPES);
    }

    /**
     * Factory: creates policy from a comma-separated string of AccountType names.
     * Used by Spring @Value injection in the infrastructure config layer.
     *
     * @param typesString comma-separated AccountType names, e.g. "SAVINGS,CURRENT,FIXED_DEPOSIT"
     */
    public static EligibilityPolicy fromString(String typesString) {
        if (typesString == null || typesString.isBlank()) {
            return withDefaults();
        }
        Set<AccountType> types = Arrays.stream(typesString.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(AccountType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccountType.class)));
        if (types.isEmpty()) {
            return withDefaults();
        }
        return new EligibilityPolicy(types);
    }

    /**
     * Tests whether an account is eligible based on its status and type.
     *
     * @param status      account status string from AccountInfo (e.g., "ACTIVE", "DORMANT")
     * @param accountType eligible account type
     * @return true if account should be included in the balance dashboard
     */
    public boolean isEligible(String status, AccountType accountType) {
        return ACTIVE_STATUS.equals(status) && eligibleTypes.contains(accountType);
    }

    /**
     * Returns the configured set of eligible types (defensive copy).
     */
    public Set<AccountType> eligibleTypes() {
        return EnumSet.copyOf(eligibleTypes);
    }
}
