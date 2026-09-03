package tally.domain;

/**
 * Why a {@link Money} operation was refused.
 *
 * <p>Each case carries the values that caused it, so a caller can report the
 * fault without the domain formatting prose. Domain errors are matchable
 * values, never strings — see ADR 007.
 *
 * <p>There is exactly one case, and that is the point: since {@link Money}
 * holds an unbounded {@link java.math.BigInteger}, arithmetic cannot overflow,
 * so mixing currencies is the only way arithmetic can fail. The interface stays
 * sealed rather than collapsing into a single exception type because adding a
 * second failure later should break every incomplete {@code switch}.
 */
public sealed interface MoneyError {

    /**
     * Two amounts in different currencies were combined.
     *
     * <p>There is no implicit exchange rate anywhere in Tally. Converting
     * requires a rate, a rate has a source and a timestamp, and inventing one
     * silently is how a ledger stops reconciling.
     */
    record CurrencyMismatch(Currency left, Currency right) implements MoneyError {}
}
