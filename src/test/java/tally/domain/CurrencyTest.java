package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The scale is the part worth testing. It is not presentation: it decides what
 * one minor unit means, so a wrong scale silently multiplies or divides every
 * amount in that currency by a power of ten.
 */
class CurrencyTest {

    @ParameterizedTest
    @EnumSource(Currency.class)
    @DisplayName("the code matches the enum constant, so neither can drift from the other")
    void codeMatchesTheConstantName(Currency currency) {
        assertThat(currency.code()).isEqualTo(currency.name());
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    @DisplayName("every code is three uppercase letters, as ISO 4217 requires")
    void codesAreThreeUppercaseLetters(Currency currency) {
        assertThat(currency.code()).hasSize(3).matches("[A-Z]{3}");
    }

    @Test
    @DisplayName("scales match ISO 4217: a wrong one silently rescales every amount")
    void scalesMatchIso4217() {
        assertThat(Currency.USD.scale()).isEqualTo(2);
        assertThat(Currency.EUR.scale()).isEqualTo(2);
        assertThat(Currency.GBP.scale()).isEqualTo(2);
        assertThat(Currency.BRL.scale()).isEqualTo(2);
        assertThat(Currency.JPY.scale()).isZero();
        assertThat(Currency.KWD.scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("all three real-world scales are represented, so nothing can assume two decimals")
    void coversEveryRealWorldScale() {
        assertThat(Arrays.stream(Currency.values()).map(Currency::scale).distinct().sorted().toList())
                .containsExactly(0, 2, 3);
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    @DisplayName("no currency has a negative scale, which would make minor units meaningless")
    void scalesAreNonNegative(Currency currency) {
        assertThat(currency.scale()).isNotNegative();
    }

    @Test
    @DisplayName("the set is closed, so a switch over currencies is compiler-checked")
    void isAClosedSet() {
        assertThat(Currency.values()).hasSize(6);
        assertThat(Currency.class.isEnum()).isTrue();
    }

    @Test
    @DisplayName("the scale is what gives a minor-unit count its meaning")
    void scaleDeterminesWhatAnAmountMeans() {
        // The same number, three currencies, three different amounts of money.
        assertThat(Money.of(150, Currency.JPY)).hasToString("150 JPY");
        assertThat(Money.of(150, Currency.USD)).hasToString("1.50 USD");
        assertThat(Money.of(150, Currency.KWD)).hasToString("0.150 KWD");
    }
}
