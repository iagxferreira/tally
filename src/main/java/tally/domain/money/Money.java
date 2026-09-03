package tally.domain;

import java.math.BigInteger;
import java.util.Objects;

/**
 * An exact amount of a single currency, held as a count of minor units.
 *
 * <p>{@code Money.of(150, USD)} is one dollar fifty. {@code Money.of(150, JPY)}
 * is one hundred and fifty yen. The number alone is meaningless; the currency
 * carries the scale that gives it meaning. See ADR 001 and ADR 008.
 *
 * <h2>Why minor units, and why not BigDecimal</h2>
 *
 * <p>Counting minor units makes the invariant structural: there is no way to
 * write half a cent, because half a cent is not an integer number of cents. A
 * {@code BigDecimal} would happily hold {@code 1.005 USD}, demoting that
 * invariant to a rule someone has to keep revalidating.
 *
 * <p>{@code BigDecimal} carries a second trap for a record: its {@code equals}
 * compares scale as well as value, so {@code 1.50} and {@code 1.5} are unequal
 * while {@code compareTo} calls them the same. A value type that disagrees with
 * itself about equality breaks hash maps, {@code contains}, and every test
 * assertion. {@link BigInteger} has no scale, so the record's generated
 * {@code equals} is simply correct.
 *
 * <p>There is no floating point here and there never will be. Binary floating
 * point cannot represent 0.10 exactly, so a cent goes missing somewhere around
 * the ten-thousandth addition and the ledger stops balancing for reasons nobody
 * can reconstruct.
 *
 * <h2>Why arithmetic mostly cannot fail</h2>
 *
 * <p>{@link BigInteger} is unbounded, so addition, subtraction and negation
 * cannot overflow. That deletes an entire class of failure that a {@code long}
 * representation had to carry: with {@code long}, every arithmetic call had to
 * account for an overflow error, and a raw {@code +} that slipped past review
 * would wrap silently into a plausible, wrong balance.
 *
 * <p>The one remaining way arithmetic can fail is a currency mismatch, which
 * raises {@link CurrencyMismatchException}. It is unchecked because a
 * well-written caller does not add dollars to yen — that is a defect in the
 * calling code, not a condition to recover from. Amounts therefore compose
 * with ordinary chaining: {@code a.add(b).add(c)}.
 *
 * <p>The cost is an allocation per operation and the loss of primitive
 * comparison. That is accepted: correctness before performance, and optimising
 * without a measurement is forbidden by the project's principles.
 *
 * <p>Instances are immutable and safe to share.
 */
public record Money(BigInteger minorUnits, Currency currency) implements Comparable<Money> {

    /** @throws NullPointerException on a null component — a programmer error, not a domain failure */
    public Money {
        Objects.requireNonNull(minorUnits, "minorUnits");
        Objects.requireNonNull(currency, "currency");
    }

    /** An amount of {@code minorUnits} in {@code currency}. */
    public static Money of(BigInteger minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    /**
     * An amount of {@code minorUnits} in {@code currency}.
     *
     * <p>A convenience for the overwhelmingly common case of an amount that
     * fits in a {@code long}. It is only a constructor shortcut: the value is
     * widened immediately and nothing downstream is bounded by {@code long}.
     */
    public static Money of(long minorUnits, Currency currency) {
        return new Money(BigInteger.valueOf(minorUnits), currency);
    }

    /** Zero in the given currency. Zero is currency-specific: there is no universal zero to compare against. */
    public static Money zero(Currency currency) {
        return new Money(BigInteger.ZERO, currency);
    }

    /**
     * This amount plus {@code other}.
     *
     * <p>There is no overflow case: the representation is unbounded.
     *
     * @throws CurrencyMismatchException if the currencies differ
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(minorUnits.add(other.minorUnits), currency);
    }

    /**
     * This amount minus {@code other}.
     *
     * <p>The result may be negative. {@code Money} is a quantity, not a
     * posting: negative amounts are meaningful when deriving a balance, and it
     * is {@code Posting} that requires strict positivity, because there
     * direction is carried by {@code Direction} rather than by a sign.
     *
     * @throws CurrencyMismatchException if the currencies differ
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(minorUnits.subtract(other.minorUnits), currency);
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /**
     * This amount negated.
     *
     * <p>Total, and deliberately so. With a {@code long} representation this
     * had to be fallible, because two's complement has no positive counterpart
     * for {@code Long.MIN_VALUE}. An unbounded representation has no such
     * asymmetry.
     */
    public Money negate() {
        return new Money(minorUnits.negate(), currency);
    }

    /** Whether this amount is greater than zero. */
    public boolean isPositive() {
        return minorUnits.signum() > 0;
    }

    /** Whether this amount is zero. */
    public boolean isZero() {
        return minorUnits.signum() == 0;
    }

    /** Whether this amount is less than zero. */
    public boolean isNegative() {
        return minorUnits.signum() < 0;
    }

    /**
     * Orders two amounts of the same currency.
     *
     * @throws CurrencyMismatchException if the currencies differ. Ordering
     *     dollars against yen is meaningless for the same reason adding them
     *     is, so it fails the same way.
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return minorUnits.compareTo(other.minorUnits);
    }

    /**
     * The amount and its currency code, at the currency's scale — for example
     * {@code "1.50 USD"}, {@code "150 JPY"}, {@code "0.150 KWD"}.
     *
     * <p>This is a debugging aid. It is not localised and is not a money
     * formatter for user interfaces.
     */
    @Override
    public String toString() {
        int scale = currency.scale();
        if (scale == 0) {
            return minorUnits + " " + currency.code();
        }
        String digits = minorUnits.abs().toString();
        String padded = "0".repeat(Math.max(0, scale + 1 - digits.length())) + digits;
        int split = padded.length() - scale;
        String sign = isNegative() ? "-" : "";
        return sign + padded.substring(0, split) + "." + padded.substring(split) + " " + currency.code();
    }
}
