package tally.domain;

/**
 * Which side of the ledger a movement falls on.
 *
 * <p>Direction is carried here and nowhere else. A posting's amount is always
 * strictly positive; there is no such thing as a debit of minus fifty. If
 * amounts could be negative there would be two spellings of every movement — a
 * debit of {@code -50} and a credit of {@code 50} — and
 * {@code sum(debits) == sum(credits)} would stop discriminating between a
 * balanced transaction and a nonsensical one.
 *
 * <p>Debit and credit are not "money in" and "money out". They are sides. What
 * a debit <em>does</em> to a balance depends on the kind of account it lands
 * on, which is {@link AccountKind}'s job: a debit increases an asset and
 * decreases a liability, and both are ordinary.
 */
public enum Direction {
    /** The left side. Increases assets and expenses; decreases the rest. */
    DEBIT,
    /** The right side. Increases liabilities, equity and revenue; decreases the rest. */
    CREDIT;

    /**
     * The other side. Every posting has a counterpart facing this way.
     *
     * <p>A {@code switch} rather than a ternary: it is exhaustive over the enum
     * and needs no {@code default}, so a third constant could not be added
     * without this failing to compile. A ternary would silently treat any new
     * constant as {@code CREDIT}.
     */
    public Direction opposite() {
        return switch (this) {
            case DEBIT -> CREDIT;
            case CREDIT -> DEBIT;
        };
    }
}
