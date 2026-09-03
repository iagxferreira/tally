package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These test the property ADR 007 exists to protect — that a failure cannot be
 * quietly skipped — rather than the shape of the combinators.
 */
class ResultTest {

    private static final MoneyError MISMATCH =
            new MoneyError.CurrencyMismatch(Currency.USD, Currency.EUR);

    @Nested
    class Map {

        @Test
        void transformsASuccess() {
            Result<Integer, MoneyError> result = Result.<String, MoneyError>ok("150").map(Integer::parseInt);

            assertThat(result).isEqualTo(Result.ok(150));
        }

        @Test
        @DisplayName("a failure passes through untouched and the mapper never runs")
        void leavesAFailureAlone() {
            AtomicInteger calls = new AtomicInteger();

            Result<Integer, MoneyError> result = Result.<String, MoneyError>err(MISMATCH)
                    .map(value -> {
                        calls.incrementAndGet();
                        return Integer.parseInt(value);
                    });

            assertThat(result).isEqualTo(Result.err(MISMATCH));
            assertThat(calls).hasValue(0);
        }
    }

    @Nested
    class FlatMap {

        @Test
        void chainsFallibleOperations() {
            Result<Money, MoneyError> result = Money.of(100, Currency.USD)
                    .add(Money.of(50, Currency.USD))
                    .flatMap(sum -> sum.add(Money.of(25, Currency.USD)));

            assertThat(result).isEqualTo(Result.ok(Money.of(175, Currency.USD)));
        }

        @Test
        @DisplayName("short-circuits: nothing after the first failure runs")
        void shortCircuitsOnTheFirstFailure() {
            AtomicInteger calls = new AtomicInteger();

            Result<Money, MoneyError> result = Money.of(100, Currency.USD)
                    .add(Money.of(50, Currency.EUR))
                    .flatMap(sum -> {
                        calls.incrementAndGet();
                        return sum.add(Money.of(25, Currency.USD));
                    });

            assertThat(result).isEqualTo(
                    Result.err(new MoneyError.CurrencyMismatch(Currency.USD, Currency.EUR)));
            assertThat(calls).hasValue(0);
        }

        @Test
        @DisplayName("the motivating case: folding a list of amounts without unwrapping mid-loop")
        void foldsASequenceOfAmounts() {
            List<Money> amounts = List.of(
                    Money.of(100, Currency.USD),
                    Money.of(250, Currency.USD),
                    Money.of(75, Currency.USD));

            Result<Money, MoneyError> total = Result.ok(Money.zero(Currency.USD));
            for (Money amount : amounts) {
                total = total.flatMap(running -> running.add(amount));
            }

            assertThat(total).isEqualTo(Result.ok(Money.of(425, Currency.USD)));
        }

        @Test
        @DisplayName("one bad currency in a fold fails the whole fold")
        void aSingleMismatchFailsTheFold() {
            List<Money> amounts = List.of(
                    Money.of(100, Currency.USD),
                    Money.of(250, Currency.EUR),
                    Money.of(75, Currency.USD));

            Result<Money, MoneyError> total = Result.ok(Money.zero(Currency.USD));
            for (Money amount : amounts) {
                total = total.flatMap(running -> running.add(amount));
            }

            assertThat(total.isOk()).isFalse();
        }
    }

    @Nested
    class MapError {

        @Test
        @DisplayName("translates an error across an aggregate boundary")
        void transformsAFailure() {
            Result<Money, String> result = Money.of(100, Currency.USD)
                    .add(Money.of(100, Currency.JPY))
                    .mapError(error -> switch (error) {
                        case MoneyError.CurrencyMismatch(var left, var right) ->
                            left.code() + "/" + right.code();
                    });

            assertThat(result).isEqualTo(Result.err("USD/JPY"));
        }

        @Test
        void leavesASuccessAlone() {
            Result<Money, String> result = Money.of(100, Currency.USD)
                    .add(Money.of(50, Currency.USD))
                    .mapError(error -> "unreachable");

            assertThat(result).isEqualTo(Result.ok(Money.of(150, Currency.USD)));
        }
    }

    @Nested
    class Fold {

        @Test
        void collapsesBothCasesToOneValue() {
            Result<Money, MoneyError> ok = Money.of(100, Currency.USD).add(Money.of(50, Currency.USD));
            Result<Money, MoneyError> err = Money.of(100, Currency.USD).add(Money.of(50, Currency.EUR));

            assertThat(ok.fold(Money::toString, error -> "refused")).isEqualTo("1.50 USD");
            assertThat(err.fold(Money::toString, error -> "refused")).isEqualTo("refused");
        }
    }

    @Nested
    class OrElseThrow {

        @Test
        void returnsTheValueOfASuccess() {
            assertThat(Result.<String, MoneyError>ok("value").orElseThrow()).isEqualTo("value");
        }

        @Test
        @DisplayName("throws with the error in the message, so a bad assertion is diagnosable")
        void throwsOnAFailure() {
            assertThatThrownBy(() -> Result.<String, MoneyError>err(MISMATCH).orElseThrow())
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("CurrencyMismatch");
        }
    }

    @Nested
    class Exhaustiveness {

        @Test
        @DisplayName("a switch over a sealed Result needs no default branch")
        void switchIsExhaustiveWithoutADefault() {
            Result<Money, MoneyError> result = Money.of(100, Currency.USD).add(Money.of(50, Currency.USD));

            String rendered = switch (result) {
                case Result.Ok<Money, MoneyError>(var money) -> money.toString();
                case Result.Err<Money, MoneyError>(var error) -> error.toString();
            };

            assertThat(rendered).isEqualTo("1.50 USD");
        }
    }
}
