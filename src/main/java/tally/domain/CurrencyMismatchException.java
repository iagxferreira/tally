package tally.domain;

/**
 * Two amounts in different currencies were combined or compared.
 *
 * <p>There is no implicit exchange rate anywhere in Tally. Converting requires
 * a rate, a rate has a source and a timestamp, and inventing one silently is
 * how a ledger stops reconciling.
 *
 * <p>This is unchecked because it marks a defect in the calling code rather
 * than a condition anyone recovers from. Nothing sensible catches it and
 * retries: the fix is that the caller should not have mixed the currencies.
 */
public final class CurrencyMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient Currency left;
    private final transient Currency right;

    /**
     * @param left the currency of the amount being operated on
     * @param right the currency of the amount it was combined with
     */
    public CurrencyMismatchException(Currency left, Currency right) {
        super("cannot combine " + left.code() + " with " + right.code());
        this.left = left;
        this.right = right;
    }

    /** The currency of the amount being operated on. */
    public Currency left() {
        return left;
    }

    /** The currency it was combined with. */
    public Currency right() {
        return right;
    }
}
