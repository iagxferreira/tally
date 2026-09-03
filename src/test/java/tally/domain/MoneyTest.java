package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These test the invariants ADR 001 and ADR 007 exist to protect, not the
 * shape of the implementation: exactness, refusal to mix currencies, and
 * overflow surfacing as a value rather than wrapping.
 */
class MoneyTest {

    @Nested
    @DisplayName("addition")
    class Addition {

        @Test
        void addsAmountsOfTheSameCurrency() {
            Money sum = Money.of(150, Currency.USD).add(Money.of(275, Currency.USD)).orElseThrow();

            assertThat(sum).isEqualTo(Money.of(425, Currency.USD));
        }

        @Test
        void refusesToAddDifferentCurrencies() {
            Result<Money, MoneyError> result = Money.of(100, Currency.USD).add(Money.of(100, Currency.EUR));

            assertThat(result).isEqualTo(
                    Result.err(new MoneyError.CurrencyMismatch(Currency.USD, Currency.EUR)));
        }

        @Test
        @DisplayName("reports overflow instead of wrapping past Long.MAX_VALUE")
        void reportsOverflowRatherThanWrapping() {
            Money max = Money.of(Long.MAX_VALUE, Currency.USD);

            Result<Money, MoneyError> result = max.add(Money.of(1, Currency.USD));

            assertThat(result).isEqualTo(
                    Result.err(new MoneyError.Overflow(Long.MAX_VALUE, 1L)));
        }

        @Test
        void addingToTheLimitWithoutExceedingItSucceeds() {
            Money almost = Money.of(Long.MAX_VALUE - 1, Currency.USD);

            assertThat(almost.add(Money.of(1, Currency.USD)).orElseThrow())
                    .isEqualTo(Money.of(Long.MAX_VALUE, Currency.USD));
        }
    }

    @Nested
    @DisplayName("subtraction")
    class Subtraction {

        @Test
        void producesNegativeAmounts() {
            Money result = Money.of(100, Currency.USD).subtract(Money.of(250, Currency.USD)).orElseThrow();

            assertThat(result).isEqualTo(Money.of(-150, Currency.USD));
            assertThat(result.isNegative()).isTrue();
        }

        @Test
        void refusesToSubtractDifferentCurrencies() {
            Result<Money, MoneyError> result =
                    Money.of(100, Currency.JPY).subtract(Money.of(100, Currency.KWD));

            assertThat(result).isEqualTo(
                    Result.err(new MoneyError.CurrencyMismatch(Currency.JPY, Currency.KWD)));
        }

        @Test
        void reportsUnderflowPastLongMinValue() {
            Money min = Money.of(Long.MIN_VALUE, Currency.USD);

            assertThat(min.subtract(Money.of(1, Currency.USD)))
                    .isEqualTo(Result.err(new MoneyError.Overflow(Long.MIN_VALUE, 1L)));
        }
    }

    @Nested
    @DisplayName("negation")
    class Negation {

        @Test
        void negatesOrdinaryAmounts() {
            assertThat(Money.of(150, Currency.USD).negate().orElseThrow())
                    .isEqualTo(Money.of(-150, Currency.USD));
        }

        @Test
        @DisplayName("Long.MIN_VALUE has no positive counterpart in two's complement")
        void reportsOverflowNegatingLongMinValue() {
            Result<Money, MoneyError> result = Money.of(Long.MIN_VALUE, Currency.USD).negate();

            assertThat(result).isEqualTo(
                    Result.err(new MoneyError.Overflow(Long.MIN_VALUE, 0L)));
        }
    }

    @Nested
    @DisplayName("scale")
    class Scale {

        @Test
        @DisplayName("the same number means a different amount in each currency")
        void formatsAtEachCurrencyScale() {
            assertThat(Money.of(150, Currency.USD)).hasToString("1.50 USD");
            assertThat(Money.of(150, Currency.JPY)).hasToString("150 JPY");
            assertThat(Money.of(150, Currency.KWD)).hasToString("0.150 KWD");
        }

        @Test
        void padsAmountsSmallerThanOneMajorUnit() {
            assertThat(Money.of(5, Currency.USD)).hasToString("0.05 USD");
            assertThat(Money.of(5, Currency.KWD)).hasToString("0.005 KWD");
            assertThat(Money.zero(Currency.USD)).hasToString("0.00 USD");
        }

        @Test
        void formatsNegativeAmounts() {
            assertThat(Money.of(-5, Currency.USD)).hasToString("-0.05 USD");
            assertThat(Money.of(-150, Currency.JPY)).hasToString("-150 JPY");
        }

        @Test
        @DisplayName("Long.MIN_VALUE formats without tripping over Math.abs")
        void formatsTheSmallestAmount() {
            assertThat(Money.of(Long.MIN_VALUE, Currency.USD))
                    .hasToString("-92233720368547758.08 USD");
        }
    }

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        void ordersAmountsOfTheSameCurrency() {
            assertThat(Money.of(100, Currency.USD)).isLessThan(Money.of(200, Currency.USD));
            assertThat(Money.of(200, Currency.USD)).isGreaterThan(Money.of(100, Currency.USD));
            assertThat(Money.of(100, Currency.USD)).isEqualByComparingTo(Money.of(100, Currency.USD));
        }

        @Test
        @DisplayName("comparing across currencies is a programmer error, not a domain outcome")
        void refusesToOrderDifferentCurrencies() {
            assertThatThrownBy(() -> Money.of(100, Currency.USD).compareTo(Money.of(100, Currency.JPY)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("JPY");
        }
    }

    @Nested
    @DisplayName("zero")
    class Zero {

        @Test
        @DisplayName("zero is currency-specific; there is no universal zero")
        void zeroIsPerCurrency() {
            assertThat(Money.zero(Currency.USD).isZero()).isTrue();
            assertThat(Money.zero(Currency.USD)).isNotEqualTo(Money.zero(Currency.EUR));
        }

        @Test
        void zeroIsNeitherPositiveNorNegative() {
            Money zero = Money.zero(Currency.USD);

            assertThat(zero.isPositive()).isFalse();
            assertThat(zero.isNegative()).isFalse();
        }
    }
}
