package tally.core;

import jakarta.enterprise.context.ApplicationScoped;
import tally.domain.Account;
import tally.domain.AccountKind;
import tally.domain.Currency;
import tally.domain.Ledger;

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
}
