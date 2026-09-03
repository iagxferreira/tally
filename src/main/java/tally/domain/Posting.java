package tally.domain;

import java.util.Objects;

/**
 * One leg of a movement: an account, a side, and a strictly positive amount.
 *
 * <p>A posting is minted only through its {@link Account} — see ADR 005. There
 * is no construction path that lacks an account, so a posting whose currency
 * differs from its account's is not merely invalid, it is unconstructible, and
 * the check cannot be skipped by a caller in a hurry.
 *
 * <h2>Why this is a class and not a record</h2>
 *
 * <p>It was written as a record first. That does not compile:
 *
 * <pre>
 * error: invalid canonical constructor in record Posting
 *     (attempting to assign stronger access privileges; was public)
 * </pre>
 *
 * <p>A record's canonical constructor must be <em>at least as accessible as the
 * record itself</em>. Records are transparent carriers of their state, so the
 * language refuses to let one be publicly readable but not publicly
 * constructible — which is exactly what ADR 005 requires. The rule wins and the
 * record goes, at the cost of hand-written {@code equals}, {@code hashCode} and
 * {@code toString}.
 *
 * <p>Note how much weaker this is than the Rust original, which used module
 * privacy that a sibling module could not reach. Package privacy stops other
 * packages, but anything later added to {@code tally.domain} can call this
 * constructor directly. Keep this package small enough to read.
 *
 * <p>A posting holds its account's {@link AccountId} rather than the
 * {@link Account} itself. Postings are journal entries — immutable facts that
 * outlive any in-memory object graph — and referencing an identifier keeps them
 * that way. It is also what makes invariant 3 a real check rather than a
 * tautology: the ledger must confirm the account exists.
 */
public final class Posting {

    private final AccountId account;
    private final Direction direction;
    private final Money amount;

    /**
     * Package-private. Use {@link Account#debit(Money)} or
     * {@link Account#credit(Money)}.
     *
     * <p>The currency check lives in {@link Account}, which is the only caller
     * and the only holder of the currency to check against.
     *
     * @throws NonPositiveAmountException if {@code amount} is zero or negative
     */
    Posting(AccountId account, Direction direction, Money amount) {
        this.account = Objects.requireNonNull(account, "account");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.amount = Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new NonPositiveAmountException(amount);
        }
    }

    /** The account this leg falls against. */
    public AccountId account() {
        return account;
    }

    /** Which side of the ledger this leg falls on. */
    public Direction direction() {
        return direction;
    }

    /** The amount, always strictly positive and always in its account's currency. */
    public Money amount() {
        return amount;
    }

    /**
     * The signed contribution this posting makes to the balance of an account
     * of the given kind.
     *
     * <p>A debit of 25 contributes {@code +25} to an asset and {@code -25} to
     * revenue. The sign comes from {@link AccountKind}, derived from the
     * accounting equation rather than tabulated.
     */
    public Money effectOn(AccountKind kind) {
        return kind.effectOf(direction) > 0 ? amount : amount.negate();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Posting posting
                && account.equals(posting.account)
                && direction == posting.direction
                && amount.equals(posting.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, direction, amount);
    }

    @Override
    public String toString() {
        return direction + " " + amount + " on " + account;
    }
}
