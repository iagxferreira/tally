package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * These test the accounting equation the sign rule is derived from, not the
 * five individual answers. A lookup table would satisfy the per-kind cases
 * below; only the equation tests would catch it drifting out of agreement with
 * the rule it is supposed to encode.
 */
class AccountKindTest {

    @Test
    @DisplayName("left-hand kinds of the equation are increased by a debit")
    void leftHandKindsIncreaseOnDebit() {
        assertThat(AccountKind.ASSET.increasedBy()).isEqualTo(Direction.DEBIT);
        assertThat(AccountKind.EXPENSE.increasedBy()).isEqualTo(Direction.DEBIT);
    }

    @Test
    @DisplayName("right-hand kinds of the equation are increased by a credit")
    void rightHandKindsIncreaseOnCredit() {
        assertThat(AccountKind.LIABILITY.increasedBy()).isEqualTo(Direction.CREDIT);
        assertThat(AccountKind.EQUITY.increasedBy()).isEqualTo(Direction.CREDIT);
        assertThat(AccountKind.REVENUE.increasedBy()).isEqualTo(Direction.CREDIT);
    }

    @ParameterizedTest
    @EnumSource(AccountKind.class)
    @DisplayName("every kind is increased by one direction and decreased by the other")
    void increaseAndDecreaseAreOpposites(AccountKind kind) {
        assertThat(kind.decreasedBy()).isEqualTo(kind.increasedBy().opposite());
        assertThat(kind.increasedBy()).isNotEqualTo(kind.decreasedBy());
    }

    @ParameterizedTest
    @EnumSource(AccountKind.class)
    @DisplayName("the signed effect is +1 for the increasing direction and -1 for the other")
    void effectMatchesTheIncreasingDirection(AccountKind kind) {
        assertThat(kind.effectOf(kind.increasedBy())).isEqualTo(1);
        assertThat(kind.effectOf(kind.decreasedBy())).isEqualTo(-1);
    }

    @Test
    @DisplayName("the equation balances: a debit on one side offsets a credit on the other")
    void aDebitAndCreditPairKeepsTheEquationBalanced() {
        // The defining property of double entry. Debiting an asset and
        // crediting a liability by the same amount grows both sides of
        // assets = liabilities + equity equally, so the equation survives.
        assertThat(AccountKind.ASSET.effectOf(Direction.DEBIT)).isEqualTo(1);
        assertThat(AccountKind.LIABILITY.effectOf(Direction.CREDIT)).isEqualTo(1);

        // And the classic counterpart: debiting an expense while crediting an
        // asset moves value across the left side without changing the total.
        assertThat(AccountKind.EXPENSE.effectOf(Direction.DEBIT)).isEqualTo(1);
        assertThat(AccountKind.ASSET.effectOf(Direction.CREDIT)).isEqualTo(-1);
    }

    @Test
    @DisplayName("exactly two kinds sit on the left of the equation")
    void theEquationHasTwoLeftHandKinds() {
        long debitIncreased = Arrays.stream(AccountKind.values())
                .filter(kind -> kind.increasedBy() == Direction.DEBIT)
                .count();

        assertThat(debitIncreased).isEqualTo(2);
        assertThat(AccountKind.values()).hasSize(5);
    }

    @Test
    @DisplayName("a debit means opposite things to an asset and to revenue, and both are ordinary")
    void debitDoesNotMeanIncrease() {
        assertThat(AccountKind.ASSET.effectOf(Direction.DEBIT)).isEqualTo(1);
        assertThat(AccountKind.REVENUE.effectOf(Direction.DEBIT)).isEqualTo(-1);
    }
}
