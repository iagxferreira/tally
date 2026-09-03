package tally.domain;

/**
 * A transaction was structurally impossible before its balance could even be
 * considered.
 *
 * <p>Two cases, both invariants in their own right:
 *
 * <ul>
 *   <li>Fewer than two postings (invariant 2). A single posting cannot balance
 *       against anything, and an empty transaction records no economic fact
 *       while balancing vacuously.
 *   <li>Postings in more than one currency. The MVP restricts a transaction to
 *       a single currency; see the multi-currency scope decision.
 * </ul>
 */
public final class MalformedTransactionException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** @param message what was structurally wrong */
    public MalformedTransactionException(String message) {
        super(message);
    }

    /** Fewer than two postings. */
    static MalformedTransactionException tooFewPostings(int count) {
        return new MalformedTransactionException(
                "a transaction needs at least two postings, got " + count);
    }

    /** Postings spanning more than one currency. */
    static MalformedTransactionException mixedCurrencies(Currency first, Currency other) {
        return new MalformedTransactionException(
                "a transaction must use a single currency, got "
                        + first.code() + " and " + other.code());
    }
}
