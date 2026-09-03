package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DirectionTest {

    @Test
    void debitAndCreditAreOpposites() {
        assertThat(Direction.DEBIT.opposite()).isEqualTo(Direction.CREDIT);
        assertThat(Direction.CREDIT.opposite()).isEqualTo(Direction.DEBIT);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    @DisplayName("opposite is an involution: flipping twice returns the original side")
    void oppositeAppliedTwiceIsIdentity(Direction direction) {
        assertThat(direction.opposite().opposite()).isEqualTo(direction);
    }

    @Test
    @DisplayName("there are exactly two sides, and no third state to represent")
    void thereAreExactlyTwoSides() {
        assertThat(Direction.values()).containsExactly(Direction.DEBIT, Direction.CREDIT);
    }
}
