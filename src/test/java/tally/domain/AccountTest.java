package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These test ADR 004 (a currency fixed at opening) and ADR 005 (postings are
 * minted through their account), not the accessors.
 */
class AccountTest {

    @Nested
    @DisplayName("opening")
    class Opening {

        @Test
        void mintsADistinctIdentityPerAccount() {
            Account first = Account.open(AccountKind.ASSET, Currency.USD);
            Account second = Account.open(AccountKind.ASSET, Currency.USD);

            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void fixesTheCurrencyAtOpening() {
            Account account = Account.open(AccountKind.LIABILITY, Currency.JPY);

            assertThat(account.currency()).isEqualTo(Currency.JPY);
            assertThat(account.zero()).isEqualTo(Money.zero(Currency.JPY));
        }

        @Test
        @DisplayName("a reopened account equals the original: identity is what equality reads")
        void reopensToAnEqualAccount() {
            Account original = Account.open(AccountKind.EQUITY, Currency.EUR);

            Account rehydrated =
                    Account.reopen(original.id(), AccountKind.EQUITY, Currency.EUR);

            assertThat(rehydrated).isEqualTo(original).hasSameHashCodeAs(original);
        }

        @Test
        @DisplayName("equality is identity, not component-wise: same shape is not the same account")
        void twoAccountsOfTheSameShapeAreNotEqual() {
            Account first = Account.open(AccountKind.ASSET, Currency.USD);
            Account second = Account.open(AccountKind.ASSET, Currency.USD);

            assertThat(first.kind()).isEqualTo(second.kind());
            assertThat(first.currency()).isEqualTo(second.currency());
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("minting postings")
    class MintingPostings {

        private final Account cash = Account.open(AccountKind.ASSET, Currency.USD);

        @Test
        void mintsADebitAgainstItself() {
            Posting posting = cash.debit(Money.of(2500, Currency.USD));

            assertThat(posting.account()).isEqualTo(cash.id());
            assertThat(posting.direction()).isEqualTo(Direction.DEBIT);
            assertThat(posting.amount()).isEqualTo(Money.of(2500, Currency.USD));
        }

        @Test
        void mintsACreditAgainstItself() {
            Posting posting = cash.credit(Money.of(2500, Currency.USD));

            assertThat(posting.direction()).isEqualTo(Direction.CREDIT);
        }

        @Test
        @DisplayName("refuses an amount in any other currency (ADR 004)")
        void refusesAForeignCurrency() {
            assertThatThrownBy(() -> cash.debit(Money.of(2500, Currency.EUR)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("USD")
                    .hasMessageContaining("EUR");
        }

        @Test
        @DisplayName("refuses a negative amount: direction is carried by Direction, not by a sign")
        void refusesANegativeAmount() {
            assertThatThrownBy(() -> cash.debit(Money.of(-1, Currency.USD)))
                    .isInstanceOf(NonPositiveAmountException.class);
        }

        @Test
        @DisplayName("refuses zero: it records no economic fact but would balance vacuously")
        void refusesZero() {
            assertThatThrownBy(() -> cash.credit(Money.zero(Currency.USD)))
                    .isInstanceOf(NonPositiveAmountException.class)
                    .asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.throwable(
                                    NonPositiveAmountException.class))
                    .satisfies(failure ->
                            assertThat(failure.amount()).isEqualTo(Money.zero(Currency.USD)));
        }
    }

    @Nested
    @DisplayName("balance effect")
    class BalanceEffect {

        @Test
        @DisplayName("a debit increases an asset and decreases revenue, and both are ordinary")
        void debitMeansOppositeThingsToDifferentKinds() {
            Account cash = Account.open(AccountKind.ASSET, Currency.USD);
            Account sales = Account.open(AccountKind.REVENUE, Currency.USD);

            Money onAsset = cash.debit(Money.of(100, Currency.USD)).effectOn(cash.kind());
            Money onRevenue = sales.debit(Money.of(100, Currency.USD)).effectOn(sales.kind());

            assertThat(onAsset).isEqualTo(Money.of(100, Currency.USD));
            assertThat(onRevenue).isEqualTo(Money.of(-100, Currency.USD));
        }

        @Test
        @DisplayName("a debit/credit pair on opposite sides of the equation nets to zero effect")
        void aBalancedPairOffsets() {
            Account cash = Account.open(AccountKind.ASSET, Currency.USD);
            Account loan = Account.open(AccountKind.LIABILITY, Currency.USD);

            Money assetEffect = cash.debit(Money.of(500, Currency.USD)).effectOn(cash.kind());
            Money liabilityEffect = loan.credit(Money.of(500, Currency.USD)).effectOn(loan.kind());

            // Both sides of assets = liabilities + equity grow by the same
            // amount, so the equation survives the transaction.
            assertThat(assetEffect).isEqualTo(Money.of(500, Currency.USD));
            assertThat(liabilityEffect).isEqualTo(Money.of(500, Currency.USD));
        }
    }
}
