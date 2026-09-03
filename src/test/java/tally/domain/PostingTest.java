package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Posting could not be a record — a record's canonical constructor must be at
 * least as accessible as the record itself, and ADR 005 requires a
 * package-private one. So its equality, hash and string form are hand-written,
 * and are tested here because a record would have supplied them correctly for
 * free and this does not.
 */
class PostingTest {

    private final Account cash = Account.open(AccountKind.ASSET, Currency.USD);

    @Nested
    @DisplayName("construction is gated by the account")
    class Construction {

        @Test
        @DisplayName("no public constructor exists, so nothing outside the package can mint one")
        void hasNoPubliclyAccessibleConstructor() {
            for (Constructor<?> constructor : Posting.class.getDeclaredConstructors()) {
                assertThat(Modifier.isPublic(constructor.getModifiers()))
                        .as("constructor %s must not be public", constructor)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("the class is final, so the gate cannot be subclassed around")
        void isFinal() {
            assertThat(Modifier.isFinal(Posting.class.getModifiers())).isTrue();
        }

        @Test
        void carriesTheAccountIdRatherThanTheAccount() {
            Posting posting = cash.debit(Money.of(100, Currency.USD));

            assertThat(posting.account()).isEqualTo(cash.id()).isInstanceOf(AccountId.class);
        }
    }

    @Nested
    @DisplayName("equality (hand-written, since this cannot be a record)")
    class Equality {

        @Test
        void postingsWithTheSameAccountDirectionAndAmountAreEqual() {
            Posting first = cash.debit(Money.of(100, Currency.USD));
            Posting second = cash.debit(Money.of(100, Currency.USD));

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }

        @Test
        void differsByDirection() {
            assertThat(cash.debit(Money.of(100, Currency.USD)))
                    .isNotEqualTo(cash.credit(Money.of(100, Currency.USD)));
        }

        @Test
        void differsByAmount() {
            assertThat(cash.debit(Money.of(100, Currency.USD)))
                    .isNotEqualTo(cash.debit(Money.of(101, Currency.USD)));
        }

        @Test
        void differsByAccount() {
            Account other = Account.open(AccountKind.ASSET, Currency.USD);

            assertThat(cash.debit(Money.of(100, Currency.USD)))
                    .isNotEqualTo(other.debit(Money.of(100, Currency.USD)));
        }

        @Test
        void isNotEqualToOtherTypesOrNull() {
            Posting posting = cash.debit(Money.of(100, Currency.USD));

            assertThat(posting).isNotEqualTo(null).isNotEqualTo("DEBIT 1.00 USD");
        }

        @Test
        @DisplayName("equality is reflexive, symmetric and consistent with hashCode")
        void obeysTheEqualsContract() {
            Posting first = cash.debit(Money.of(100, Currency.USD));
            Posting second = cash.debit(Money.of(100, Currency.USD));

            // Asserted on the boolean rather than through isEqualTo, because
            // assertThat(x).isEqualTo(x) is a tautology to AssertJ and Error
            // Prone rejects it as a SelfAssertion. Reflexivity is a property of
            // equals, so test equals directly.
            assertThat(first.equals(first)).isTrue();
            assertThat(first.equals(second)).isEqualTo(second.equals(first));
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }
    }

    @Nested
    @DisplayName("balance effect")
    class Effect {

        @ParameterizedTest
        @EnumSource(AccountKind.class)
        @DisplayName("the effect is positive exactly when the direction increases the kind")
        void effectFollowsTheAccountingEquation(AccountKind kind) {
            Account account = Account.open(kind, Currency.USD);
            Money amount = Money.of(100, Currency.USD);

            Money increasing = account.post(kind.increasedBy(), amount).effectOn(kind);
            Money decreasing = account.post(kind.decreasedBy(), amount).effectOn(kind);

            assertThat(increasing).isEqualTo(amount);
            assertThat(decreasing).isEqualTo(Money.of(-100, Currency.USD));
        }

        @ParameterizedTest
        @EnumSource(AccountKind.class)
        @DisplayName("the effect keeps the posting's currency")
        void effectStaysInTheAccountCurrency(AccountKind kind) {
            Account account = Account.open(kind, Currency.KWD);

            Money effect = account.debit(Money.of(100, Currency.KWD)).effectOn(kind);

            assertThat(effect.currency()).isEqualTo(Currency.KWD);
        }

        @Test
        @DisplayName("an amount is never mutated: effectOn returns a new value")
        void doesNotMutateTheAmount() {
            Posting posting = cash.debit(Money.of(100, Currency.USD));

            posting.effectOn(AccountKind.REVENUE);

            assertThat(posting.amount()).isEqualTo(Money.of(100, Currency.USD));
        }
    }

    @Test
    @DisplayName("renders as side, amount and account, for logs and failures")
    void rendersReadably() {
        Posting posting = cash.debit(Money.of(2500, Currency.USD));

        assertThat(posting).hasToString("DEBIT 25.00 USD on " + cash.id());
    }
}
