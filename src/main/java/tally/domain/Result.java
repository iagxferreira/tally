package tally.domain;

import java.util.NoSuchElementException;

/**
 * The outcome of a domain operation that is allowed to fail: either a value or
 * a typed error.
 *
 * <p>This exists because the signature of a fallible operation should say so.
 * {@code Money.add} can fail on a currency mismatch, and a caller that forgets
 * is a financial bug, not an inconvenience — so the failure is a value the
 * caller must take apart rather than an exception they may not have thought
 * about. See ADR 007.
 *
 * <p>Because this interface is {@code sealed}, a {@code switch} over
 * {@link Ok} and {@link Err} is checked for exhaustiveness by the compiler:
 *
 * <pre>{@code
 * String describe(Result<Money, MoneyError> result) {
 *     return switch (result) {
 *         case Result.Ok<Money, MoneyError>(var money) -> money.toString();
 *         case Result.Err<Money, MoneyError>(var error) -> render(error);
 *     };
 * }
 * }</pre>
 *
 * <p>This is deliberately the <em>only</em> generic machinery the domain has.
 * There is no {@code map} or {@code flatMap}: chaining combinators would buy
 * brevity at the cost of the kind of generic abstraction the project's
 * principles warn against, and nothing has yet demonstrated the need. A second
 * piece of generic machinery requires its own decision record.
 *
 * @param <T> the value produced when the operation succeeds
 * @param <E> the error describing why it failed
 */
public sealed interface Result<T, E> {

    /** A successful outcome carrying its value. */
    record Ok<T, E>(T value) implements Result<T, E> {}

    /** A failed outcome carrying the error that caused it. */
    record Err<T, E>(E error) implements Result<T, E> {}

    /** Wraps a successful value. */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /** Wraps a failure. */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    /** Whether this outcome succeeded. */
    default boolean isOk() {
        return this instanceof Ok<T, E>;
    }

    /**
     * The value, or a thrown {@link NoSuchElementException} if this failed.
     *
     * <p>This is an assertion that the operation could not have failed, and it
     * belongs in tests and in call sites that have already established the
     * inputs are valid. Reaching for it to avoid handling an error is how the
     * guarantee in ADR 007 gets thrown away.
     *
     * @throws NoSuchElementException if this is an {@link Err}
     */
    default T orElseThrow() {
        return switch (this) {
            case Ok<T, E>(var value) -> value;
            case Err<T, E>(var error) ->
                throw new NoSuchElementException("expected a value but the operation failed: " + error);
        };
    }
}
