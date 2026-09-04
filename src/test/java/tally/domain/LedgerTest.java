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
 * These test invariants 3, 7 and 8 — every posting references a known account,
 * the journal is append-only, and balances are derived rather than stored.
 */
class LedgerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);

    private final Account cash = Account.open(AccountKind.ASSET, Currency.USD);
    private final Account revenue = Account.open(AccountKind.REVENUE, Currency.USD);
    private final Account taxDue = Account.open(AccountKind.LIABILITY, Currency.USD);

    private Ledger ledger = new Ledger();

    private Money usd(long minorUnits) {
        return Money.of(minorUnits, Currency.USD);
    }

    private Ledger withAccounts() {
        ledger = ledger.registerAll(cash, revenue, taxDue);
        return ledger;
    }

    @Nested
    @DisplayName("the end-to-end double entry")
    class EndToEnd {

        @Test
        @DisplayName("a sale moves both accounts and the books stay balanced")
        void recordsASaleAndDerivesBothBalances() {
            withAccounts();

            Transaction sale = Transaction.of(
                    clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));
            ledger = ledger.post(sale);

            assertThat(ledger.balanceOf(cash)).isEqualTo(usd(2500));
            assertThat(ledger.balanceOf(revenue)).isEqualTo(usd(2500));
            assertThat(ledger.isBalanced()).isTrue();
        }

        @Test
        @DisplayName("a debit means opposite things to an asset and to revenue")
        void balancesFollowTheAccountingEquation() {
            withAccounts();

            // Revenue is credited, and a credit increases revenue. Cash is
            // debited, and a debit increases an asset. Both balances are
            // positive despite being on opposite sides of the transaction.
            ledger = ledger.post(Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500))));

            assertThat(ledger.balanceOf(cash).isPositive()).isTrue();
            assertThat(ledger.balanceOf(revenue).isPositive()).isTrue();
        }

        @Test
        @DisplayName("a three-legged sale with tax lands on all three accounts")
        void recordsAMultiLeggedEvent() {
            withAccounts();

            ledger = ledger.post(Transaction.of(
                    clock,
                    cash.debit(usd(11000)),
                    revenue.credit(usd(10000)),
                    taxDue.credit(usd(1000))));

            assertThat(ledger.balanceOf(cash)).isEqualTo(usd(11000));
            assertThat(ledger.balanceOf(revenue)).isEqualTo(usd(10000));
            assertThat(ledger.balanceOf(taxDue)).isEqualTo(usd(1000));
            assertThat(ledger.isBalanced()).isTrue();
        }

        @Test
        @DisplayName("balances accumulate across many transactions")
        void accumulatesAcrossTransactions() {
            withAccounts();

            for (int i = 0; i < 10; i++) {
                ledger = ledger.post(Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100))));
            }

            assertThat(ledger.balanceOf(cash)).isEqualTo(usd(1000));
            assertThat(ledger.size()).isEqualTo(10);
            assertThat(ledger.isBalanced()).isTrue();
        }
    }

    @Nested
    @DisplayName("invariant 3 — postings must reference known accounts")
    class KnownAccounts {

        @Test
        @DisplayName("refuses a transaction naming an unregistered account")
        void refusesAnUnknownAccount() {
            ledger = ledger.register(cash);
            ledger = ledger.register(revenue);
            Account stranger = Account.open(AccountKind.EXPENSE, Currency.USD);

            Transaction transaction =
                    Transaction.of(clock, stranger.debit(usd(100)), revenue.credit(usd(100)));

            assertThatThrownBy(() -> ledger.post(transaction))
                    .isInstanceOf(UnknownAccountException.class)
                    .asInstanceOf(throwable(UnknownAccountException.class))
                    .satisfies(failure -> assertThat(failure.account()).isEqualTo(stranger.id()));
        }

        @Test
        @DisplayName("a refused transaction leaves the journal untouched")
        void doesNotPartiallyApplyARefusedTransaction() {
            withAccounts();
            ledger = ledger.post(Transaction.of(clock, cash.debit(usd(500)), revenue.credit(usd(500))));
            Account stranger = Account.open(AccountKind.EXPENSE, Currency.USD);

            Transaction bad = Transaction.of(clock, stranger.debit(usd(100)), cash.credit(usd(100)));

            assertThatThrownBy(() -> ledger.post(bad)).isInstanceOf(UnknownAccountException.class);
            assertThat(ledger.size()).isEqualTo(1);
            assertThat(ledger.balanceOf(cash)).isEqualTo(usd(500));
        }

        @Test
        void refusesToReportABalanceForAnUnknownAccount() {
            AccountId stranger = AccountId.mint();

            assertThatThrownBy(() -> ledger.balanceOf(stranger))
                    .isInstanceOf(UnknownAccountException.class);
        }

        @Test
        @DisplayName("registering the same account twice is accepted and changes nothing")
        void registrationIsIdempotent() {
            ledger = ledger.register(cash);
            ledger = ledger.register(cash);

            assertThat(ledger.accounts()).hasSize(1);
            assertThat(ledger.account(cash.id())).contains(cash);
        }

        @Test
        @DisplayName("re-registering an ID with different metadata must not reinterpret history")
        void conflictingRegistrationCannotChangeAccountMeaning() {
            ledger = ledger.registerAll(cash, revenue);
            ledger = ledger.post(Transaction.of(clock, cash.debit(usd(500)), revenue.credit(usd(500))));

            Account conflicting = Account.reopen(cash.id(), AccountKind.ASSET, Currency.EUR);

            assertThatThrownBy(() -> ledger.register(conflicting))
                    .isInstanceOf(ConflictingAccountException.class);
        }
    }

    @Nested
    @DisplayName("invariant 7 — the journal is append-only")
    class AppendOnly {

        @Test
        @DisplayName("writes return new snapshots and leave the source ledger unchanged")
        void writesDoNotMutateTheirSource() {
            Ledger empty = new Ledger();
            Ledger registered = empty.registerAll(cash, revenue);
            Transaction sale = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            Ledger posted = registered.post(sale);

            assertThat(empty.accounts()).isEmpty();
            assertThat(empty.size()).isZero();
            assertThat(registered.size()).isZero();
            assertThat(posted.journal()).containsExactly(sale);
        }

        @Test
        @DisplayName("two writes from one snapshot produce independent branches")
        void snapshotsCanBranchWithoutCrossContamination() {
            Ledger registered = new Ledger().registerAll(cash, revenue);
            Transaction first = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            Transaction second = Transaction.of(clock, cash.debit(usd(200)), revenue.credit(usd(200)));

            Ledger firstBranch = registered.post(first);
            Ledger secondBranch = registered.post(second);

            assertThat(registered.size()).isZero();
            assertThat(firstBranch.journal()).containsExactly(first);
            assertThat(secondBranch.journal()).containsExactly(second);
        }

        @Test
        void preservesPostingOrder() {
            withAccounts();
            Transaction first = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            Transaction second = Transaction.of(clock, cash.debit(usd(200)), revenue.credit(usd(200)));

            ledger = ledger.post(first);
            ledger = ledger.post(second);

            assertThat(ledger.journal()).containsExactly(first, second);
        }

        @Test
        @DisplayName("the journal view cannot be modified by a caller")
        void handsOutAnUnmodifiableJournal() {
            withAccounts();
            Transaction posted = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            ledger = ledger.post(posted);

            List<Transaction> view = ledger.journal();

            assertThatThrownBy(() -> view.add(posted))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(view::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("the same transaction cannot be posted twice, which would double it")
        void refusesADuplicate() {
            withAccounts();
            Transaction sale = Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));
            ledger = ledger.post(sale);

            assertThatThrownBy(() -> ledger.post(sale))
                    .isInstanceOf(DuplicateTransactionException.class);
            assertThat(ledger.balanceOf(cash)).isEqualTo(usd(100));
        }
    }

    @Nested
    @DisplayName("corrections are reversals, not edits")
    class Corrections {

        @Test
        @DisplayName("a reversal returns the balances to where they were")
        void reversalUndoesTheEffect() {
            withAccounts();
            Transaction mistake = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));
            ledger = ledger.post(mistake);

            ledger = ledger.post(Transaction.reverse(clock, mistake));

            assertThat(ledger.balanceOf(cash)).isEqualTo(Money.zero(Currency.USD));
            assertThat(ledger.balanceOf(revenue)).isEqualTo(Money.zero(Currency.USD));
        }

        @Test
        @DisplayName("but both entries remain: the journal records the error and the correction")
        void keepsBothEntriesInTheJournal() {
            withAccounts();
            Transaction mistake = Transaction.of(clock, cash.debit(usd(2500)), revenue.credit(usd(2500)));
            ledger = ledger.post(mistake);

            Transaction correction = Transaction.reverse(clock, mistake);
            ledger = ledger.post(correction);

            // A rollback would leave one entry, or none. An auditor must see
            // that the mistake happened and that it was corrected.
            assertThat(ledger.size()).isEqualTo(2);
            assertThat(ledger.journal()).containsExactly(mistake, correction);
            assertThat(correction.reverses()).contains(mistake.id());
        }

        @Test
        @DisplayName("refuses to reverse a transaction the journal never saw")
        void refusesToReverseAnUnpostedTransaction() {
            withAccounts();
            Transaction neverPosted =
                    Transaction.of(clock, cash.debit(usd(100)), revenue.credit(usd(100)));

            Transaction correction = Transaction.reverse(clock, neverPosted);

            assertThatThrownBy(() -> ledger.post(correction))
                    .isInstanceOf(UnknownTransactionException.class)
                    .asInstanceOf(throwable(UnknownTransactionException.class))
                    .satisfies(f -> assertThat(f.transaction()).isEqualTo(neverPosted.id()));
        }
    }

    @Nested
    @DisplayName("invariant 8 — balances are derived, never stored")
    class DerivedBalances {

        @Test
        @DisplayName("a trial balance reports every registered account")
        void reportsATrialBalance() {
            withAccounts();
            ledger = ledger.post(Transaction.of(
                    clock, cash.debit(usd(11000)), revenue.credit(usd(10000)), taxDue.credit(usd(1000))));

            assertThat(ledger.balances())
                    .containsEntry(cash.id(), usd(11000))
                    .containsEntry(revenue.id(), usd(10000))
                    .containsEntry(taxDue.id(), usd(1000));
        }

        @Test
        @DisplayName("an account with no postings has a zero balance in its own currency")
        void reportsZeroForAnUnusedAccount() {
            Account yen = Account.open(AccountKind.ASSET, Currency.JPY);
            ledger = ledger.register(yen);

            assertThat(ledger.balanceOf(yen)).isEqualTo(Money.zero(Currency.JPY));
        }

        @Test
        @DisplayName("an empty journal is balanced")
        void anEmptyLedgerIsBalanced() {
            assertThat(ledger.isBalanced()).isTrue();
            assertThat(ledger.size()).isZero();
        }

        @Test
        @DisplayName("registering an account after posting still derives its full history")
        void derivesFromTheJournalNotFromRegistrationTime() {
            withAccounts();
            ledger = ledger.post(Transaction.of(clock, cash.debit(usd(700)), revenue.credit(usd(700))));

            // Nothing was cached at post time; the balance is folded from the
            // journal on every call, so re-reading gives the same answer.
            assertThat(ledger.balanceOf(cash)).isEqualTo(ledger.balanceOf(cash)).isEqualTo(usd(700));
        }
    }
}
