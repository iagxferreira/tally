package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * These test the two properties ADR 003 chose version 7 for — mintable without
 * coordination, and sorting in time order — rather than the fact that a UUID
 * comes back.
 */
class AccountIdTest {

    @Test
    void mintsDistinctIdentities() {
        assertThat(AccountId.mint()).isNotEqualTo(AccountId.mint());
    }

    @Test
    @DisplayName("minted identities are version 7, not the JDK's version 4")
    void mintsVersionSeven() {
        assertThat(AccountId.mint().value().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("the variant is RFC 4122, so the value is a well-formed UUID")
    void mintsTheRfcVariant() {
        assertThat(AccountId.mint().value().variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("identities minted in sequence sort in that order, which is the point of v7")
    void sortsInMintOrder() {
        List<AccountId> minted = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            minted.add(AccountId.mint());
        }

        assertThat(minted).isSorted();
    }

    @Test
    @DisplayName("the leading bits are a millisecond timestamp near now")
    void encodesTheCurrentTimeInTheLeadingBits() {
        long before = System.currentTimeMillis();
        AccountId id = AccountId.mint();
        long after = System.currentTimeMillis();

        // The top 48 bits of a v7 UUID are the Unix epoch millisecond.
        long timestamp = id.value().getMostSignificantBits() >>> 16;

        assertThat(timestamp).isBetween(before, after);
    }

    @Test
    @DisplayName("a version 4 UUID is rejected: accepting one would silently lose the ordering")
    void rejectsVersionFour() {
        UUID random = UUID.randomUUID();

        assertThatThrownBy(() -> AccountId.of(random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version 7")
                .hasMessageContaining("version 4");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> AccountId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("an identity rebuilt from its value equals the original")
    void roundTripsThroughItsValue() {
        AccountId original = AccountId.mint();

        AccountId rebuilt = AccountId.of(original.value());

        assertThat(rebuilt).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(rebuilt).hasToString(original.value().toString());
    }

    @Test
    @DisplayName("identity is a distinct type, not an interchangeable UUID")
    void isNotABareUuid() {
        AccountId id = AccountId.mint();

        // The wrapper is the point: a raw UUID would be assignable anywhere
        // another 128-bit identifier is expected, and the compiler could not
        // tell an account from a transaction.
        assertThat((Object) id).isNotInstanceOf(UUID.class);
        assertThat(id.value()).isInstanceOf(UUID.class);
    }
}
