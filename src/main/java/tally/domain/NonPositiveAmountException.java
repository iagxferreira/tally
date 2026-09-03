package tally.domain;

/**
 * A posting was attempted with an amount that is zero or negative.
 *
 * <p>Invariant 4: posting amounts are strictly positive, and direction is
 * carried by {@link Direction} rather than by the sign of the number. If
 * amounts could be negative there would be two spellings of every movement — a
 * debit of {@code -50} and a credit of {@code 50} — and
 * {@code sum(debits) == sum(credits)} would stop discriminating between a
 * balanced transaction and a nonsensical one.
 *
 * <p>Zero is refused for a related reason: a zero posting records no economic
 * fact, but it would let a transaction satisfy the balancing rule vacuously.
 */
public final class NonPositiveAmountException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient Money amount;

    /** @param amount the rejected amount */
    public NonPositiveAmountException(Money amount) {
        super("posting amounts must be strictly positive, got " + amount);
        this.amount = amount;
    }

    /** The rejected amount. */
    public Money amount() {
        return amount;
    }
}
