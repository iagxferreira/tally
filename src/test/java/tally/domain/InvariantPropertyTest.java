package tally.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Generated cases for invariants that must hold across arbitrary amounts. */
class InvariantPropertyTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    private static final int CASES = 1_000;

    @Test
    @DisplayName("every generated balanced transaction remains balanced when reversed")
    void reversalPreservesBalance() {
        Random random = new Random(0x54414C4C59L);
        Account cash = Account.open(AccountKind.ASSET, Currency.USD);
        Account revenue = Account.open(AccountKind.REVENUE, Currency.USD);
        Ledger ledger = new Ledger();
        ledger = ledger.registerAll(cash, revenue);

        for (int i = 0; i < CASES; i++) {
            long amount = random.nextLong(1, 1_000_000);
            Transaction transaction = Transaction.transfer(CLOCK, revenue, cash, Money.of(amount, Currency.USD));
            ledger = ledger.post(transaction);
            ledger = ledger.post(Transaction.reverse(CLOCK, transaction));
        }

        assertThat(ledger.balanceOf(cash)).isEqualTo(Money.zero(Currency.USD));
        assertThat(ledger.balanceOf(revenue)).isEqualTo(Money.zero(Currency.USD));
        assertThat(ledger.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("every generated unequal debit and credit is refused")
    void unbalancedTransactionsAreAlwaysRefused() {
        Random random = new Random(0x554E42414C4CL);
        Account cash = Account.open(AccountKind.ASSET, Currency.USD);
        Account revenue = Account.open(AccountKind.REVENUE, Currency.USD);

        for (int i = 0; i < CASES; i++) {
            long debit = random.nextLong(1, 1_000_000);
            long credit = random.nextLong(1, 1_000_000);
            if (debit == credit) {
                credit++;
            }
            long unequalCredit = credit;

            assertThatThrownBy(() -> Transaction.of(
                            CLOCK, cash.debit(Money.of(debit, Currency.USD)),
                            revenue.credit(Money.of(unequalCredit, Currency.USD))))
                    .isInstanceOf(UnbalancedTransactionException.class);
        }
    }
}
