package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * These test the handling property the sealed hierarchy exists to give: that a
 * failure can be matched exhaustively, and that what a handler needs is carried
 * as typed fields rather than parsed out of a message.
 */
class DomainExceptionTest {

    /**
     * A handler written the way callers are meant to write one: an exhaustive
     * switch with <em>no default branch</em>. When a new failure joins
     * {@code permits}, this stops compiling — which is the whole point.
     */
    private static String describe(DomainException failure) {
        return switch (failure) {
            case CurrencyMismatchException mismatch ->
                mismatch.left().code() + "!=" + mismatch.right().code();
            case NonPositiveAmountException nonPositive ->
                "non-positive " + nonPositive.amount();
        };
    }

    @Test
    @DisplayName("a handler matches exhaustively without a catch-all default")
    void handlesEveryFailureWithoutADefaultBranch() {
        DomainException failure = new CurrencyMismatchException(Currency.USD, Currency.JPY);

        assertThat(describe(failure)).isEqualTo("USD!=JPY");
    }

    @Test
    @DisplayName("a new failure type is handled because the compiler demanded it")
    void handlesTheSecondFailureType() {
        // This case did not exist when describe() was written. Adding
        // NonPositiveAmountException to DomainException's permits clause broke
        // the switch above until it was accounted for, which is the property
        // the sealed hierarchy exists to provide.
        DomainException failure = new NonPositiveAmountException(Money.zero(Currency.USD));

        assertThat(describe(failure)).isEqualTo("non-positive 0.00 USD");
    }

    @Test
    @DisplayName("a handler reads typed fields, never the message text")
    void carriesTheCausingValuesAsFields() {
        CurrencyMismatchException failure =
                new CurrencyMismatchException(Currency.KWD, Currency.BRL);

        assertThat(failure.left()).isEqualTo(Currency.KWD);
        assertThat(failure.right()).isEqualTo(Currency.BRL);
    }

    @Test
    @DisplayName("one catch at a boundary covers every domain refusal")
    void isCatchableAsASingleFamily() {
        assertThatThrownBy(() -> Money.of(1, Currency.USD).add(Money.of(1, Currency.EUR)))
                .isInstanceOf(DomainException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("unchecked, so a caller that cannot fail is not forced into ceremony")
    void isUnchecked() {
        assertThat(RuntimeException.class).isAssignableFrom(CurrencyMismatchException.class);
    }
}
