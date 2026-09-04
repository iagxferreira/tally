package tally.domain;

/**
 * An account identifier was registered with metadata different from its
 * existing definition.
 *
 * <p>Account kind and currency determine how postings are interpreted. They
 * are therefore immutable identity metadata from the ledger's perspective;
 * replacing either would change the meaning of historic journal entries.
 */
public final class ConflictingAccountException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient AccountId account;
    private final transient Account existing;
    private final transient Account conflicting;

    /**
     * @param existing the definition already registered
     * @param conflicting the definition that was refused
     */
    public ConflictingAccountException(Account existing, Account conflicting) {
        super("account " + existing.id() + " is already registered with different metadata");
        this.account = existing.id();
        this.existing = existing;
        this.conflicting = conflicting;
    }

    /** The identifier registered with conflicting metadata. */
    public AccountId account() {
        return account;
    }

    /** The definition already held by the ledger. */
    public Account existing() {
        return existing;
    }

    /** The definition that registration refused. */
    public Account conflicting() {
        return conflicting;
    }
}
