package tally.domain;

/**
 * A transaction referenced an account the ledger does not know.
 *
 * <p>Invariant 3, and the only invariant that <em>cannot</em> be enforced by
 * construction. A posting carries an {@link AccountId}, not an
 * {@link Account} — deliberately, since postings are journal entries that
 * outlive any object graph — so nothing about a posting in isolation can say
 * whether its account exists. Only the ledger holds the context to answer that,
 * which is why this check lives there and nowhere else.
 */
public final class UnknownAccountException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient AccountId account;

    /** @param account the identifier that is not registered with this ledger */
    public UnknownAccountException(AccountId account) {
        super("no account registered with id " + account);
        this.account = account;
    }

    /** The unknown identifier. */
    public AccountId account() {
        return account;
    }
}
