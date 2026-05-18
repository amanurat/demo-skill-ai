package com.bank.transfer.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value object representing a monetary amount with an ISO 4217 currency code.
 *
 * <p>Uses {@link BigDecimal} with 4 decimal places (NUMERIC(19,4) in DB).
 * Money arithmetic never uses {@code float} or {@code double}.
 *
 * <p>Equality is checked via {@link BigDecimal#compareTo(BigDecimal)} (not equals),
 * so {@code 1500.0000} and {@code 1500.00} are considered equal in value.
 */
public final class Money {

    /** Number of decimal places retained for all monetary values. */
    public static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;
    private final Currency currency;

    /**
     * Primary constructor.
     *
     * @param amount   the monetary value; must be non-null and non-negative
     * @param currency the ISO 4217 currency; must be non-null
     * @throws IllegalArgumentException if amount is negative
     */
    public Money(final BigDecimal amount, final Currency currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount must not be negative, was: " + amount);
        }
        this.amount = amount.setScale(SCALE, ROUNDING);
        this.currency = currency;
    }

    /**
     * Convenience factory using a currency code string.
     *
     * @param amount       monetary value
     * @param currencyCode ISO 4217 code, e.g. "THB"
     * @return new Money instance
     */
    public static Money of(final BigDecimal amount, final String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * Convenience factory from a plain string (for deserialization / tests).
     *
     * @param amountStr    string representation, e.g. "1500.0000"
     * @param currencyCode ISO 4217 code
     * @return new Money instance
     */
    public static Money of(final String amountStr, final String currencyCode) {
        return new Money(new BigDecimal(amountStr), Currency.getInstance(currencyCode));
    }

    /**
     * Returns the amount scaled to {@value #SCALE} decimal places.
     *
     * @return the BigDecimal amount
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the ISO 4217 currency.
     *
     * @return currency
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Adds another Money value. Currencies must match.
     *
     * @param other the value to add
     * @return new Money with the sum
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(final Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another Money value. Currencies must match.
     *
     * @param other the value to subtract
     * @return new Money with the difference (may be zero)
     * @throws IllegalArgumentException if currencies differ or result is negative
     */
    public Money subtract(final Money other) {
        requireSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Subtraction would produce negative Money: " + this.amount + " - " + other.amount);
        }
        return new Money(result, this.currency);
    }

    /**
     * Returns true if this amount is greater than {@code other}.
     * Currencies must match.
     *
     * @param other value to compare against
     * @return true when this > other
     */
    public boolean isGreaterThan(final Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Returns true if this amount is greater than zero.
     *
     * @return true when amount > 0
     */
    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Value equality: amount (via compareTo) AND currency must match.
     *
     * @param o object to compare
     * @return true if same value
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money money)) {
            return false;
        }
        return this.amount.compareTo(money.amount) == 0
            && this.currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    /**
     * Returns amount serialized as plain decimal string (ADR-016: decimal string on wire).
     *
     * @return e.g. "1500.0000"
     */
    public String toWireString() {
        return amount.toPlainString();
    }

    private void requireSameCurrency(final Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }
}
