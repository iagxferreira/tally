package tally.domain;

import java.util.Objects;

/**
 * An exact amount of a single currency, held as a count of minor units.
 *
 * <p>{@code Money.of(150, USD)} is one dollar fifty. {@code Money.of(150, JPY)}
 * is one hundred and fifty yen. The number alone is meaningless; the currency
 * carries the scale that gives it meaning. See ADR 001.
 *
 * <p>There is no floating point here and there never will be. Binary floating
 * point cannot represent 0.10 exactly, so a cent goes missing somewhere around
 * the ten-thousandth addition, and the ledger stops balancing for reasons
 * nobody can reconstruct.
 *
 * <h2>Overflow</h2>
 *
 * <p>Every operation goes through {@link Math#addExact} and its relatives.
 * This is load-bearing rather than decorative: Java's {@code long} wraps
 * silently on overflow in <em>every</em> build, and unlike the Rust original
 * there is no profile setting that turns wrapping into a panic. A raw
 * {@code +} that slips past review here is a silent financial bug. The
 * {@link ArithmeticException} those methods throw is a JDK implementation
 * detail and is converted to {@link MoneyError.Overflow} at this boundary — it
 * must not escape the domain.
 *
 * <p>Instances are immutable and safe to share.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    /** @throws NullPointerException if {@code currency} is null — a programmer error, not a domain failure */
    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    /** An amount of {@code minorUnits} in {@code currency}. */
    public static Money of(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    /** Zero in the given currency. Zero is currency-specific: there is no universal zero to compare against. */
    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /**
     * This amount plus {@code other}.
     *
     * @return the sum, or {@link MoneyError.CurrencyMismatch} if the currencies
     *     differ, or {@link MoneyError.Overflow} if the result does not fit
     */
    public Result<Money, MoneyError> add(Money other) {
        if (currency != other.currency) {
            return Result.err(new MoneyError.CurrencyMismatch(currency, other.currency));
        }
        try {
            return Result.ok(new Money(Math.addExact(minorUnits, other.minorUnits), currency));
        } catch (ArithmeticException overflow) {
            return Result.err(new MoneyError.Overflow(minorUnits, other.minorUnits));
        }
    }

    /**
     * This amount minus {@code other}.
     *
     * <p>The result may be negative. {@code Money} is a quantity, not a
     * posting: negative amounts are meaningful when deriving a balance, and it
     * is {@code Posting} that requires strict positivity, because there
     * direction is carried by {@code Direction} rather than by a sign.
     */
    public Result<Money, MoneyError> subtract(Money other) {
        if (currency != other.currency) {
            return Result.err(new MoneyError.CurrencyMismatch(currency, other.currency));
        }
        try {
            return Result.ok(new Money(Math.subtractExact(minorUnits, other.minorUnits), currency));
        } catch (ArithmeticException overflow) {
            return Result.err(new MoneyError.Overflow(minorUnits, other.minorUnits));
        }
    }

    /**
     * This amount negated.
     *
     * @return the negation, or {@link MoneyError.Overflow} for
     *     {@link Long#MIN_VALUE}, which has no positive counterpart in two's
     *     complement
     */
    public Result<Money, MoneyError> negate() {
        try {
            return Result.ok(new Money(Math.negateExact(minorUnits), currency));
        } catch (ArithmeticException overflow) {
            return Result.err(new MoneyError.Overflow(minorUnits, 0L));
        }
    }

    /** Whether this amount is greater than zero. */
    public boolean isPositive() {
        return minorUnits > 0L;
    }

    /** Whether this amount is zero. */
    public boolean isZero() {
        return minorUnits == 0L;
    }

    /** Whether this amount is less than zero. */
    public boolean isNegative() {
        return minorUnits < 0L;
    }

    /**
     * Orders two amounts of the same currency.
     *
     * @throws IllegalArgumentException if the currencies differ. Unlike
     *     arithmetic, ordering has no meaningful failure value to return, and
     *     comparing dollars to yen is a programmer error rather than a domain
     *     outcome. {@link Comparable} also fixes the signature, so a
     *     {@code Result} is not available here.
     */
    @Override
    public int compareTo(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "cannot order " + currency.code() + " against " + other.currency.code());
        }
        return Long.compare(minorUnits, other.minorUnits);
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
        // Not Math.abs: abs(Long.MIN_VALUE) is still negative, because two's
        // complement has no positive counterpart for it.
        String digits = Long.toString(minorUnits);
        if (digits.startsWith("-")) {
            digits = digits.substring(1);
        }
        String padded = "0".repeat(Math.max(0, scale + 1 - digits.length())) + digits;
        int split = padded.length() - scale;
        String sign = minorUnits < 0L ? "-" : "";
        return sign + padded.substring(0, split) + "." + padded.substring(split) + " " + currency.code();
    }
}
