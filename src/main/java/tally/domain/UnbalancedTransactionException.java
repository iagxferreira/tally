package tally.domain;

/**
 * A transaction's debits did not equal its credits.
 *
 * <p>Invariant 5, and the rule the whole system exists to keep. If this can be
 * violated, a balance means nothing and the journal stops being evidence of
 * anything.
 *
 * <p>Carries the imbalance as a typed {@link Money} — the amount by which
 * debits exceed credits, negative if credits exceed debits — so a caller
 * reporting a bad import can say by how much it was wrong without parsing a
 * message.
 *
 * <p>A caller who expects to encounter this routinely, such as an importer
 * validating untrusted input, should use {@link Transaction#imbalanceOf} to
 * inspect before constructing rather than catching this.
 */
public final class UnbalancedTransactionException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient Money imbalance;

    /** @param imbalance debits minus credits; never zero, or there would be no failure */
    public UnbalancedTransactionException(Money imbalance) {
        super("debits and credits differ by " + imbalance);
        this.imbalance = imbalance;
    }

    /** By how much debits exceed credits. Negative when credits exceed debits. */
    public Money imbalance() {
        return imbalance;
    }
}
