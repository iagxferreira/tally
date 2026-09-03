package tally.domain;

import java.util.NoSuchElementException;
import java.util.function.Function;

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
 * <p>This is deliberately the <em>only</em> generic machinery the domain has,
 * and its combinators are deliberately few: {@link #map}, {@link #flatMap},
 * {@link #mapError} and {@link #fold}, each justified by a call site that
 * exists. There is no {@code or}, {@code orElse}, {@code filter},
 * {@code recover} or {@code peek} — that is the road to a general-purpose
 * functional library, and the project's principles prefer explicit domain
 * concepts over generic abstraction. Adding a fifth needs a reason at the time.
 * See ADR 007.
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
     * Transforms the value if this succeeded, leaving a failure untouched.
     *
     * <p>For a mapping that cannot itself fail. If it can, use
     * {@link #flatMap} instead — otherwise you end up with a
     * {@code Result} nested inside a {@code Result}.
     *
     * <p>The bounds are {@code ? super T} and {@code ? extends U} rather than
     * a plain {@code Function<T, U>} so that a function accepting a supertype
     * of {@code T}, or producing a subtype of {@code U}, is still accepted.
     */
    default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
        return switch (this) {
            case Ok<T, E>(var value) -> new Ok<>(mapper.apply(value));
            // The error is carried across unchanged, but the Err must be
            // rebuilt: Err<T, E> and Err<U, E> are unrelated types, because
            // Java's generics are invariant.
            case Err<T, E>(var error) -> new Err<>(error);
        };
    }

    /**
     * Chains an operation that can itself fail, short-circuiting on the first
     * failure.
     *
     * <p>This is what makes a sequence of fallible steps readable. Summing
     * postings means calling {@code Money.add} repeatedly, each call able to
     * fail; without this, that fold is a loop with an unwrap in the middle,
     * which is precisely the shape that drops errors.
     *
     * <p>Java has no {@code ?} operator, so this is the closest available
     * equivalent to Rust's error propagation.
     */
    default <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> mapper) {
        return switch (this) {
            case Ok<T, E>(var value) -> mapper.apply(value);
            case Err<T, E>(var error) -> new Err<>(error);
        };
    }

    /**
     * Transforms the error if this failed, leaving a success untouched.
     *
     * <p>For translating an error across an aggregate boundary — a
     * {@code MoneyError} raised while summing postings becomes the
     * transaction-level error the caller actually cares about, without the
     * caller having to know that money arithmetic was involved.
     */
    default <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
        return switch (this) {
            case Ok<T, E>(var value) -> new Ok<>(value);
            case Err<T, E>(var error) -> new Err<>(mapper.apply(error));
        };
    }

    /**
     * Collapses both cases into a single value.
     *
     * <p>For terminal consumption where an exhaustive {@code switch} would be
     * noise — rendering, logging, or mapping an outcome to a response. Both
     * branches must be handled, so this keeps the property that a failure
     * cannot be forgotten.
     */
    default <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
        return switch (this) {
            case Ok<T, E>(var value) -> onOk.apply(value);
            case Err<T, E>(var error) -> onErr.apply(error);
        };
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
