package tally.domain;

/**
 * A reversal was posted for a transaction the journal has no record of.
 *
 * <p>A correction has to correct something. Accepting a reversal of a
 * transaction that was never posted would put a correcting entry in the journal
 * for an error the journal does not contain, and every balance it touched would
 * move for no recorded reason.
 *
 * <p>Referential, like {@link UnknownAccountException}: a {@link Transaction}
 * knows which identifier it reverses, but only the ledger knows what has
 * actually been posted.
 */
public final class UnknownTransactionException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient TransactionId transaction;

    /** @param transaction the identifier that is not in the journal */
    public UnknownTransactionException(TransactionId transaction) {
        super("no transaction in the journal with id " + transaction);
        this.transaction = transaction;
    }

    /** The unknown identifier. */
    public TransactionId transaction() {
        return transaction;
    }
}
