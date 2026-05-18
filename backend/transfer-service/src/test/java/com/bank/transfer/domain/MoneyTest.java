package com.bank.transfer.domain;

import com.bank.transfer.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Money} value object.
 *
 * <p>Coverage targets: ≥ 95% (money-handling critical path per coding standards).
 */
@DisplayName("Money value object")
class MoneyTest {

    private static final Currency THB = Currency.getInstance("THB");
    private static final Currency USD = Currency.getInstance("USD");

    // --- Construction ---

    @Test
    @DisplayName("should_scale_amount_to_four_decimal_places_on_construction")
    void should_scale_amount_to_four_decimal_places_on_construction() {
        Money money = Money.of(new BigDecimal("1500"), "THB");
        assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("1500.0000"));
        assertThat(money.getAmount().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("should_reject_negative_amount")
    void should_reject_negative_amount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1"), "THB"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be negative");
    }

    @Test
    @DisplayName("should_allow_zero_amount")
    void should_allow_zero_amount() {
        Money zero = Money.of(BigDecimal.ZERO, "THB");
        assertThat(zero.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should_reject_null_amount")
    void should_reject_null_amount() {
        assertThatThrownBy(() -> new Money(null, THB))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should_reject_null_currency")
    void should_reject_null_currency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
            .isInstanceOf(NullPointerException.class);
    }

    // --- Equality: compareTo, not equals ---

    @Test
    @DisplayName("should_treat_1500_and_1500_0000_as_equal_by_value")
    void should_treat_1500_and_1500_0000_as_equal_by_value() {
        Money a = Money.of(new BigDecimal("1500"), "THB");
        Money b = Money.of(new BigDecimal("1500.0000"), "THB");
        // equals() uses compareTo internally
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("should_not_be_equal_when_amounts_differ")
    void should_not_be_equal_when_amounts_differ() {
        Money a = Money.of(new BigDecimal("100"), "THB");
        Money b = Money.of(new BigDecimal("200"), "THB");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("should_not_be_equal_when_currencies_differ")
    void should_not_be_equal_when_currencies_differ() {
        Money thb = new Money(new BigDecimal("100"), THB);
        Money usd = new Money(new BigDecimal("100"), USD);
        assertThat(thb).isNotEqualTo(usd);
    }

    // --- Addition ---

    @Test
    @DisplayName("should_add_two_same_currency_amounts")
    void should_add_two_same_currency_amounts() {
        Money a = Money.of("1000.0000", "THB");
        Money b = Money.of("500.0000", "THB");
        Money sum = a.add(b);
        assertThat(sum.getAmount()).isEqualByComparingTo(new BigDecimal("1500.0000"));
        assertThat(sum.getCurrency()).isEqualTo(THB);
    }

    @Test
    @DisplayName("should_reject_addition_of_different_currencies")
    void should_reject_addition_of_different_currencies() {
        Money thb = new Money(new BigDecimal("100"), THB);
        Money usd = new Money(new BigDecimal("100"), USD);
        assertThatThrownBy(() -> thb.add(usd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency mismatch");
    }

    // --- Subtraction ---

    @Test
    @DisplayName("should_subtract_smaller_from_larger_amount")
    void should_subtract_smaller_from_larger_amount() {
        Money balance = Money.of("10000000.0000", "THB");
        Money transfer = Money.of("1500.0000", "THB");
        Money result = balance.subtract(transfer);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("9998500.0000"));
    }

    @Test
    @DisplayName("should_reject_subtraction_that_produces_negative_result")
    void should_reject_subtraction_that_produces_negative_result() {
        Money small = Money.of("100.0000", "THB");
        Money large = Money.of("200.0000", "THB");
        assertThatThrownBy(() -> small.subtract(large))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative Money");
    }

    // --- Comparison ---

    @Test
    @DisplayName("should_return_true_when_amount_is_greater_than_other")
    void should_return_true_when_amount_is_greater_than_other() {
        Money large = Money.of("200.0000", "THB");
        Money small = Money.of("100.0000", "THB");
        assertThat(large.isGreaterThan(small)).isTrue();
    }

    @Test
    @DisplayName("should_return_false_when_amount_equals_other")
    void should_return_false_when_amount_equals_other() {
        Money a = Money.of("100.0000", "THB");
        Money b = Money.of("100.0000", "THB");
        assertThat(a.isGreaterThan(b)).isFalse();
    }

    @Test
    @DisplayName("should_return_false_for_isPositive_when_amount_is_zero")
    void should_return_false_for_isPositive_when_amount_is_zero() {
        assertThat(Money.of(BigDecimal.ZERO, "THB").isPositive()).isFalse();
    }

    @Test
    @DisplayName("should_return_true_for_isPositive_when_amount_is_greater_than_zero")
    void should_return_true_for_isPositive_when_amount_is_greater_than_zero() {
        assertThat(Money.of(new BigDecimal("0.0001"), "THB").isPositive()).isTrue();
    }

    // --- Wire string ---

    @Test
    @DisplayName("should_serialize_amount_to_plain_decimal_string")
    void should_serialize_amount_to_plain_decimal_string() {
        Money money = Money.of("1500.0000", "THB");
        assertThat(money.toWireString()).isEqualTo("1500.0000");
    }

    @Test
    @DisplayName("should_include_currency_in_toString")
    void should_include_currency_in_toString() {
        Money money = Money.of("100.0000", "THB");
        assertThat(money.toString()).contains("THB");
    }
}
