package tally.domain;

/**
 * Why a {@link Money} operation was refused.
 *
 * <p>Each case carries the values that caused it, so a caller can report the
 * fault without the domain formatting prose. Domain errors are matchable
 * values, never strings — see ADR 007.
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

    /**
     * The result did not fit in 64 bits.
     *
     * <p>Reported rather than wrapped. A wrapped balance is the worst failure a
     * ledger has: it is silent, it is plausible, and it is wrong.
     */
    record Overflow(long left, long right) implements MoneyError {}
}
