package tally.domain;

import java.util.Objects;

/**
 * An account in the ledger: an identity, a kind, and a currency fixed when it
 * is opened.
 *
 * <p>The currency is fixed at opening and never changes — see ADR 004. An
 * account denominated in more than one currency has no meaningful balance:
 * "1,500" would be an unanswerable question until you knew which part was
 * dollars and which was yen, and any answer would require an exchange rate the
 * ledger has no business inventing.
 *
 * <h2>Why this is not a record</h2>
 *
 * <p>An account is an <em>entity</em>, not a value. Two accounts are the same
 * account when they have the same {@link AccountId}, regardless of anything
 * else; two {@link Money} values are the same when every component matches.
 * A record would generate component-wise equality, which is the value-object
 * answer, and would quietly make identity mean nothing.
 *
 * <p>The distinction has teeth once accounts are persisted: an account
 * rehydrated from a database and one held in memory must compare equal, and
 * they will if identity is what equality reads.
 *
 * <p>Instances are immutable. An account does not hold a balance — balances are
 * derived by folding postings, never stored as independent truth (invariant 8),
 * which is why nothing here mutates.
 */
public final class Account {

    private final AccountId id;
    private final AccountKind kind;
    private final Currency currency;

    private Account(AccountId id, AccountKind kind, Currency currency) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /**
     * Opens a new account, minting its identity.
     *
     * <p>No identifier is supplied, because identity is minted rather than
     * assigned — see ADR 003. There is no coordination and no round trip.
     */
    public static Account open(AccountKind kind, Currency currency) {
        return new Account(AccountId.mint(), kind, currency);
    }

    /**
     * Rebuilds an account that already exists — from a database row, say.
     *
     * <p>Separate from {@link #open} on purpose: opening an account and
     * reconstituting one are different operations, and collapsing them into a
     * single constructor that sometimes mints and sometimes accepts an
     * identifier makes it easy to reconstitute by accident.
     */
    public static Account reopen(AccountId id, AccountKind kind, Currency currency) {
        return new Account(id, kind, currency);
    }

    /** This account's identity. */
    public AccountId id() {
        return id;
    }

    /** What this account represents, and therefore which direction increases it. */
    public AccountKind kind() {
        return kind;
    }

    /** The currency this account is denominated in, fixed at opening. */
    public Currency currency() {
        return currency;
    }

    /**
     * Mints a debit posting against this account.
     *
     * @throws CurrencyMismatchException if {@code amount} is in another currency
     * @throws NonPositiveAmountException if {@code amount} is zero or negative
     */
    public Posting debit(Money amount) {
        return post(Direction.DEBIT, amount);
    }

    /**
     * Mints a credit posting against this account.
     *
     * @throws CurrencyMismatchException if {@code amount} is in another currency
     * @throws NonPositiveAmountException if {@code amount} is zero or negative
     */
    public Posting credit(Money amount) {
        return post(Direction.CREDIT, amount);
    }

    /**
     * Mints a posting on either side.
     *
     * <p>This is the only path by which a {@link Posting} can be created, which
     * is what makes "always in the account's currency" structural rather than
     * validated. The currency check cannot be skipped because there is no
     * construction path that lacks an account to check against.
     *
     * @throws CurrencyMismatchException if {@code amount} is in another currency
     * @throws NonPositiveAmountException if {@code amount} is zero or negative
     */
    public Posting post(Direction direction, Money amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.currency() != currency) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }
        return new Posting(id, direction, amount);
    }

    /** Zero in this account's currency — the starting point of a balance fold. */
    public Money zero() {
        return Money.zero(currency);
    }

    /** Two accounts are the same account when they share an identity. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Account account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return kind + " account " + id + " (" + currency.code() + ")";
    }
}
