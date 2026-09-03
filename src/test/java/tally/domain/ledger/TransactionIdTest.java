package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionIdTest {

    @Test
    void mintsDistinctIdentities() {
        assertThat(TransactionId.mint()).isNotEqualTo(TransactionId.mint());
    }

    @Test
    void mintsVersionSeven() {
        assertThat(TransactionId.mint().value().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("identities sort in mint order, which is the order the journal is read in")
    void sortsInMintOrder() {
        List<TransactionId> minted = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            minted.add(TransactionId.mint());
        }

        assertThat(minted).isSorted();
    }

    @Test
    void rejectsVersionFour() {
        UUID random = UUID.randomUUID();

        assertThatThrownBy(() -> TransactionId.of(random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version 7");
    }

    @Test
    @DisplayName("is not interchangeable with an account identifier")
    void isADistinctTypeFromAccountId() {
        TransactionId transaction = TransactionId.mint();
        AccountId account = AccountId.mint();

        // Both wrap a UUID; neither is assignable to the other. That
        // distinction is the entire reason both types exist.
        assertThat((Object) transaction).isNotInstanceOf(AccountId.class);
        assertThat((Object) account).isNotInstanceOf(TransactionId.class);
    }

    @Test
    void roundTripsThroughItsValue() {
        TransactionId original = TransactionId.mint();

        assertThat(TransactionId.of(original.value())).isEqualTo(original);
    }
}
