package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * These test invariants 2, 5 and 6 — at least two postings, debits equal
 * credits, and a posted transaction is corrected by reversal rather than edited.
 */
class TransactionTest {

    private static final Instant NOON = Instant.parse("2026-09-03T12:00:00Z");
    private final Clock clock = Clock.fixed(NOON, ZoneOffset.UTC);

    private final Account cash = Account.open(AccountKind.ASSET, Currency.USD);
    private final Account revenue = Account.open(AccountKind.REVENUE, Currency.USD);
    private final Account taxDue = Account.open(AccountKind.LIABILITY, Currency.USD);

    private Money usd(long minorUnits) {
        return Money.of(minorUnits, Currency.USD);
    }

    @Nested
    @DisplayName("the balance invariant")
    class Balance {

        @Test
        void acceptsABalancedPair() {
            Transaction sale = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));

            assertThat(sale.postings()).hasSize(2);
            assertThat(sale.total()).isEqualTo(usd(2500));
        }

        @Test
        @DisplayName("accepts a three-legged event: a sale with tax")
        void acceptsAMultiLeggedEvent() {
            Transaction sale = Transaction.of(
                    clock,
                    cash.debit(usd(11000)),
                    revenue.credit(usd(10000)),
                    taxDue.credit(usd(1000)));

            assertThat(sale.postings()).hasSize(3);
            assertThat(sale.total()).isEqualTo(usd(11000));
        }

        @Test
        @DisplayName("refuses an imbalance and reports it as a typed amount")
        void refusesAnImbalance() {
            assertThatThrownBy(() ->
                    Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2400))))
                    .isInstanceOf(UnbalancedTransactionException.class)
                    .asInstanceOf(throwable(UnbalancedTransactionException.class))
                    .satisfies(failure -> assertThat(failure.imbalance()).isEqualTo(usd(100)));
        }

        @Test
        @DisplayName("the imbalance is negative when credits exceed debits")
        void reportsANegativeImbalance() {
            assertThatThrownBy(() ->
                    Transaction.of(clock, cash.debit(usd(2400)), revenue.credit(usd(2500))))
                    .asInstanceOf(throwable(UnbalancedTransactionException.class))
                    .satisfies(failure -> assertThat(failure.imbalance()).isEqualTo(usd(-100)));
        }
    }

    @Nested
    @DisplayName("structure")
    class Structure {

        @Test
        @DisplayName("refuses a single posting: it cannot balance against anything")
        void refusesASinglePosting() {
            assertThatThrownBy(() -> Transaction.of(clock, cash.debit(usd(100))))
                    .isInstanceOf(MalformedTransactionException.class)
                    .hasMessageContaining("at least two postings");
        }

        @Test
        @DisplayName("refuses an empty transaction, which would balance vacuously")
        void refusesNoPostings() {
            assertThatThrownBy(() -> Transaction.of(clock))
                    .isInstanceOf(MalformedTransactionException.class);
        }

        @Test
        @DisplayName("refuses a mix of currencies in the MVP")
        void refusesMixedCurrencies() {
            Account euros = Account.open(AccountKind.ASSET, Currency.EUR);

            assertThatThrownBy(() ->
                    Transaction.of(clock, cash.debit(usd(100)), euros.credit(Money.of(100, Currency.EUR))))
                    .isInstanceOf(MalformedTransactionException.class)
                    .hasMessageContaining("single currency");
        }

        @Test
        @DisplayName("postings are copied, so a caller cannot mutate a posted transaction")
        void copiesItsPostings() {
            List<Posting> mutable = new java.util.ArrayList<>(
                    List.of(cash.debit(usd(100)), revenue.credit(usd(100))));

            Transaction posted = Transaction.of(clock, mutable);
            mutable.clear();

            assertThat(posted.postings()).hasSize(2);
            assertThatThrownBy(() -> posted.postings().add(cash.debit(usd(1))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("inspecting before constructing")
    class Inspecting {

        @Test
        @DisplayName("an importer can check untrusted input without catching")
        void reportsImbalanceWithoutThrowing() {
            List<Posting> bad = List.of(cash.debit(usd(2500)), revenue.credit(usd(2400)));

            assertThat(Transaction.imbalanceOf(bad)).contains(usd(100));
        }

        @Test
        void reportsNoImbalanceForABalancedSet() {
            List<Posting> good = List.of(cash.debit(usd(2500)), revenue.credit(usd(2500)));

            assertThat(Transaction.imbalanceOf(good)).isEmpty();
        }
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        void isTwoPostingsUnderneath() {
            Transaction moved = Transaction.transfer(clock, revenue, cash, usd(2500));

            assertThat(moved.postings()).hasSize(2);
            assertThat(moved.total()).isEqualTo(usd(2500));
        }

        @Test
        @DisplayName("debits the destination and credits the source")
        void putsEachAccountOnTheRightSide() {
            Transaction moved = Transaction.transfer(clock, revenue, cash, usd(2500));

            assertThat(moved.postings())
                    .anySatisfy(posting -> {
                        assertThat(posting.account()).isEqualTo(cash.id());
                        assertThat(posting.direction()).isEqualTo(Direction.DEBIT);
                    })
                    .anySatisfy(posting -> {
                        assertThat(posting.account()).isEqualTo(revenue.id());
                        assertThat(posting.direction()).isEqualTo(Direction.CREDIT);
                    });
        }
    }

    @Nested
    @DisplayName("split")
    class Split {

        @Test
        @DisplayName("one debit against several credits: a sale with tax")
        void splitsOneLegAcrossSeveral() {
            Transaction sale = Transaction.split(
                    clock,
                    cash,
                    Direction.DEBIT,
                    usd(11000),
                    List.of(
                            new Transaction.SplitLeg(revenue, usd(10000)),
                            new Transaction.SplitLeg(taxDue, usd(1000))));

            assertThat(sale.postings()).hasSize(3);
            assertThat(sale.total()).isEqualTo(usd(11000));
        }

        @Test
        @DisplayName("refuses when the parts do not sum to the whole")
        void refusesAnUnbalancedSplit() {
            assertThatThrownBy(() -> Transaction.split(
                    clock,
                    cash,
                    Direction.DEBIT,
                    usd(11000),
                    List.of(new Transaction.SplitLeg(revenue, usd(10000)))))
                    .isInstanceOf(UnbalancedTransactionException.class);
        }
    }

    @Nested
    @DisplayName("reversal")
    class Reversal {

        @Test
        @DisplayName("flips every posting and keeps the amounts")
        void flipsEveryPosting() {
            Transaction sale = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));

            Transaction correction = Transaction.reverse(clock, sale);

            assertThat(correction.postings())
                    .anySatisfy(posting -> {
                        assertThat(posting.account()).isEqualTo(cash.id());
                        assertThat(posting.direction()).isEqualTo(Direction.CREDIT);
                        assertThat(posting.amount()).isEqualTo(usd(2500));
                    })
                    .anySatisfy(posting -> {
                        assertThat(posting.account()).isEqualTo(revenue.id());
                        assertThat(posting.direction()).isEqualTo(Direction.DEBIT);
                    });
        }

        @Test
        @DisplayName("a reversal balances, because flipping every leg preserves the equality")
        void isItselfBalanced() {
            Transaction sale = Transaction.of(
                    clock, cash.debit(usd(11000)), revenue.credit(usd(10000)), taxDue.credit(usd(1000)));

            Transaction correction = Transaction.reverse(clock, sale);

            assertThat(correction.postings()).hasSize(3);
            assertThat(correction.total()).isEqualTo(usd(11000));
        }

        @Test
        @DisplayName("names the transaction it corrects, so the pair can be traced")
        void linksToTheOriginal() {
            Transaction sale = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));

            Transaction correction = Transaction.reverse(clock, sale);

            assertThat(correction.reverses()).contains(sale.id());
            assertThat(sale.reverses()).isEmpty();
        }

        @Test
        @DisplayName("nothing is removed or edited: the original is untouched")
        void leavesTheOriginalIntact() {
            Transaction sale = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));
            List<Posting> before = List.copyOf(sale.postings());

            Transaction correction = Transaction.reverse(clock, sale);

            assertThat(sale.postings()).isEqualTo(before);
            assertThat(correction.id()).isNotEqualTo(sale.id());
        }
    }

    @Nested
    @DisplayName("identity and time")
    class IdentityAndTime {

        @Test
        void takesItsTimeFromTheInjectedClock() {
            Transaction sale = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));

            assertThat(sale.occurredAt()).isEqualTo(NOON);
        }

        @Test
        @DisplayName("two transactions with identical postings are still distinct")
        void identityIsNotDerivedFromContent() {
            Transaction first = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            Transaction second = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));

            assertThat(first.id()).isNotEqualTo(second.id());
            assertThat(first).isNotEqualTo(second);
        }
    }
}
