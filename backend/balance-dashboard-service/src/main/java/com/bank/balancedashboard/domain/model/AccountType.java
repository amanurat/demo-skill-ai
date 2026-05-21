package com.bank.balancedashboard.domain.model;

/**
 * Eligible account types for the balance dashboard (EligibilityPolicy BR-004).
 * Domain enum — ZERO Spring/Kafka/Redis imports permitted in this package.
 *
 * Note: LOAN and CREDIT_CARD exist in account-service AccountInfo but are filtered
 * by EligibilityPolicy before AccountView is created. This enum only carries eligible types.
 */
public enum AccountType {
    SAVINGS,
    CURRENT,
    FIXED_DEPOSIT
}
