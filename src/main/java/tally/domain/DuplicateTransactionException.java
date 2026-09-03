package tally.domain;

/**
 * The same transaction was posted to the journal twice.
 *
 * <p>Not idempotency — that is a Phase 4 concern about safely retrying an
 * operation whose outcome is unknown, and it needs a caller-supplied key. This
 * is the narrower structural fact that a journal must not contain the same
 * entry twice: posting one transaction object twice would double every amount
 * in it and quietly corrupt the balances derived from it.
 */
public final class DuplicateTransactionException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient TransactionId transaction;

    /** @param transaction the identifier already present in the journal */
    public DuplicateTransactionException(TransactionId transaction) {
        super("transaction " + transaction + " is already in the journal");
        this.transaction = transaction;
    }

    /** The already-posted identifier. */
    public TransactionId transaction() {
        return transaction;
    }
}
