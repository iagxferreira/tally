package tally.core;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.List;
import tally.domain.Account;
import tally.domain.AccountKind;
import tally.domain.AccountId;
import tally.domain.Currency;
import tally.domain.Ledger;
import tally.domain.Money;
import tally.domain.Posting;
import tally.domain.Transaction;

/** Application boundary for the in-memory ledger. */
@ApplicationScoped
public final class LedgerService {

    private Ledger ledger = new Ledger();

    /** Opens and registers an account in the current ledger snapshot. */
    public synchronized Account openAccount(AccountKind kind, Currency currency) {
        Account account = Account.open(kind, currency);
        ledger = ledger.register(account);
        return account;
    }

    /** Resolves, validates and posts a transaction in one ledger update. */
    public synchronized Transaction postTransaction(PostTransactionRequest request) {
        List<Posting> postings = request.postings().stream().map(this::posting).toList();
        Transaction transaction = Transaction.of(Clock.systemUTC(), postings);
        ledger = ledger.post(transaction);
        return transaction;
    }

    /** Returns the immutable journal snapshot in posting order. */
    public synchronized List<Transaction> journal() {
        return ledger.journal();
    }

    /** Returns the balance derived from the current journal. */
    public synchronized Money balance(AccountId accountId) {
        return ledger.balanceOf(accountId);
    }

    private Posting posting(TransactionPostingRequest request) {
        AccountId accountId = AccountId.of(request.accountId());
        Account account = ledger.account(accountId).orElseThrow(() -> new tally.domain.UnknownAccountException(accountId));
        return account.post(request.direction(), Money.of(request.minorUnits(), request.currency()));
    }
}
