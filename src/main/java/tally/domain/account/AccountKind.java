package tally.domain;

/**
 * What an account represents, and therefore which direction increases it.
 *
 * <p>The five kinds are the terms of the accounting equation:
 *
 * <pre>
 *     assets = liabilities + equity
 * </pre>
 *
 * <p>extended over a period by the two temporary kinds that feed equity:
 *
 * <pre>
 *     assets + expenses = liabilities + equity + revenue
 * </pre>
 *
 * <h2>Why the sign rule is derived rather than tabulated</h2>
 *
 * <p>It would be easy to write a five-row table saying which direction
 * increases which kind, and it would pass exactly the same tests. It would also
 * be a set of five facts to memorise and get wrong.
 *
 * <p>The rule falls out of the equation instead. Read the extended form above:
 * the kinds on the <em>left</em> are increased by a debit, the kinds on the
 * <em>right</em> by a credit. That is the whole rule, and double-entry works
 * because it keeps both sides equal — every transaction adds the same amount to
 * each side, so the equation survives every posting.
 *
 * <p>So each kind records only which side of the equation it sits on, and the
 * direction that increases it is computed. There is one fact per kind rather
 * than five rules, and the connection to the equation stays visible in the
 * code.
 */
public enum AccountKind {

    /** What the entity owns. Left side. */
    ASSET(EquationSide.LEFT),
    /** What the entity owes. Right side. */
    LIABILITY(EquationSide.RIGHT),
    /** The residual claim of the owners. Right side. */
    EQUITY(EquationSide.RIGHT),
    /** Inflows that increase equity over a period. Right side. */
    REVENUE(EquationSide.RIGHT),
    /** Outflows that decrease equity over a period. Left side. */
    EXPENSE(EquationSide.LEFT);

    /**
     * Which side of {@code assets + expenses = liabilities + equity + revenue}
     * a kind sits on. This is the single fact each kind carries.
     */
    private enum EquationSide {
        LEFT,
        RIGHT
    }

    private final EquationSide side;

    AccountKind(EquationSide side) {
        this.side = side;
    }

    /**
     * The direction that increases an account of this kind.
     *
     * <p>Left-hand kinds are increased by a debit, right-hand kinds by a
     * credit. Nothing else is needed, and nothing is memorised.
     */
    public Direction increasedBy() {
        return switch (side) {
            case LEFT -> Direction.DEBIT;
            case RIGHT -> Direction.CREDIT;
        };
    }

    /** The direction that decreases an account of this kind. */
    public Direction decreasedBy() {
        return increasedBy().opposite();
    }

    /**
     * The signed effect of {@code direction} on an account of this kind:
     * {@code +1} if it increases the balance, {@code -1} if it decreases it.
     *
     * <p>This is what a balance fold multiplies a posting's amount by. A debit
     * of 25 on an asset contributes {@code +25}; the same debit on a revenue
     * account contributes {@code -25}. Both are ordinary bookkeeping.
     */
    public int effectOf(Direction direction) {
        return direction == increasedBy() ? 1 : -1;
    }

}
