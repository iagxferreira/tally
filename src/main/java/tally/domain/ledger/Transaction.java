package tally.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A set of postings that together record one economic event, and that balance.
 *
 * <p>This is where double-entry actually happens. Everything below it —
 * {@link Money}, {@link Account}, {@link Posting} — exists so that this type
 * can enforce invariant 5: {@code sum(debits) == sum(credits)}. If that can be
 * violated, a balance means nothing and the journal stops being evidence.
 *
 * <h2>Why a set of postings rather than a from/to transfer</h2>
 *
 * <p>Real events are routinely more than two-legged. A sale with tax is a debit
 * to cash, a credit to revenue and a credit to tax payable — three legs, one
 * event. A {@code from → to} pair cannot express that without inventing a
 * second event that never happened. See ADR 010; {@link #transfer} and
 * {@link #split} are conveniences over this model, not alternatives to it.
 *
 * <h2>Single currency, for now</h2>
 *
 * <p>Every posting in a transaction must share one currency. The eventual rule
 * is that multi-currency transactions balance <em>per currency</em>, but the
 * MVP does not support them, and refusing what it cannot do is better than
 * appearing to allow it.
 *
 * <h2>Time</h2>
 *
 * <p>The occurrence time is taken from an injected {@link Clock} rather than
 * read from {@link Instant#now()}. A domain that reads a global clock cannot be
 * tested deterministically, and ambient dependencies are what the layering
 * rules exist to keep out. Tests pass {@link Clock#fixed}.
 *
 * <p>This records <em>one</em> time. Real ledgers often distinguish when an
 * economic event occurred from when the ledger booked it — a payment made on
 * Friday and imported on Monday has two legitimate dates. The MVP deliberately
 * carries only one, and conflating them is a known limitation rather than an
 * oversight.
 *
 * <p>Instances are immutable, as invariant 6 requires: a posted transaction is
 * never edited, and a correction is a {@linkplain #reverse reversal}.
 */
public final class Transaction {

    private final TransactionId id;
    private final Instant occurredAt;
    private final List<Posting> postings;
    private final TransactionId reverses;

    private Transaction(
            TransactionId id, Instant occurredAt, List<Posting> postings, TransactionId reverses) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.postings = postings;
        this.reverses = reverses;
    }

    /**
     * Builds a transaction from its postings.
     *
     * @throws MalformedTransactionException if there are fewer than two
     *     postings, or if they span more than one currency
     * @throws UnbalancedTransactionException if debits do not equal credits
     */
    public static Transaction of(Clock clock, Collection<Posting> postings) {
        Objects.requireNonNull(clock, "clock");
        return build(clock, postings, null);
    }

    /** Builds a transaction from its postings. */
    public static Transaction of(Clock clock, Posting... postings) {
        return of(clock, List.of(postings));
    }

    /**
     * The 1:1 case: move {@code amount} out of one account and into another.
     *
     * <p>Reads as {@code from → to}, and is exactly two postings underneath. It
     * is a constructor convenience, not a second model — there is no code path
     * where a transfer is anything other than a debit and a credit.
     *
     * <p>Which side each account takes is not a judgement about "out" and "in":
     * the debit lands on {@code to} and the credit on {@code from}, and what
     * that does to each balance depends on the account's {@link AccountKind}.
     */
    public static Transaction transfer(Clock clock, Account from, Account to, Money amount) {
        return of(clock, to.debit(amount), from.credit(amount));
    }

    /**
     * One leg against several: a sale with tax, a settlement with a fee
     * withheld, payroll split into net, tax and pension.
     *
     * <p>The balance check already requires the source to equal the sum of the
     * destinations, so this adds a name and an intent rather than a new rule.
     *
     * @param source the single-sided account
     * @param sourceDirection which side {@code source} takes; destinations take the other
     * @param destinations the accounts sharing the opposite side, and their amounts
     */
    public static Transaction split(
            Clock clock,
            Account source,
            Direction sourceDirection,
            Money sourceAmount,
            List<SplitLeg> destinations) {
        List<Posting> postings = Stream.concat(
                        Stream.of(source.post(sourceDirection, sourceAmount)),
                        destinations.stream()
                                .map(leg -> leg.account()
                                        .post(sourceDirection.opposite(), leg.amount())))
                .toList();
        return of(clock, postings);
    }

    /** One destination of a {@link #split}: an account and its share. */
    public record SplitLeg(Account account, Money amount) {}

    /**
     * The correcting entry for a transaction already posted: every posting
     * flipped to its opposite side, amounts unchanged.
     *
     * <p>Not a rollback. Nothing is removed and nothing is edited — invariant 6
     * and invariant 7. The original stays in the journal forever and this joins
     * it, so an auditor sees both the mistake and the correction. A ledger that
     * could make an entry disappear would not be evidence of anything.
     *
     * <p>The result carries {@link #reverses()} naming the original, because a
     * correction nobody can trace back is not much of a correction.
     */
    public static Transaction reverse(Clock clock, Transaction original) {
        Objects.requireNonNull(original, "original");
        List<Posting> flipped = original.postings.stream().map(Posting::flip).toList();
        return build(clock, flipped, original.id);
    }

    /**
     * By how much a set of postings fails to balance: debits minus credits,
     * negative when credits exceed debits, empty when they balance.
     *
     * <p>Lets a caller holding untrusted input — a batch importer, a request
     * handler — ask the question without constructing and catching. Construction
     * still refuses to produce an invalid transaction; this only offers a way to
     * look first.
     *
     * @throws MalformedTransactionException if the postings span more than one
     *     currency, since an imbalance across currencies is not one number
     */
    public static Optional<Money> imbalanceOf(Collection<Posting> postings) {
        if (postings.isEmpty()) {
            return Optional.empty();
        }
        Money imbalance = netOf(postings);
        return imbalance.isZero() ? Optional.empty() : Optional.of(imbalance);
    }

    private static Transaction build(
            Clock clock, Collection<Posting> postings, TransactionId reverses) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(postings, "postings");
        if (postings.size() < 2) {
            throw MalformedTransactionException.tooFewPostings(postings.size());
        }
        // Defensive copy. Rust's ownership made this free; here a caller
        // retaining the collection could otherwise mutate a posted transaction,
        // which invariant 6 forbids.
        List<Posting> copied = List.copyOf(postings);
        Money net = netOf(copied);
        if (!net.isZero()) {
            throw new UnbalancedTransactionException(net);
        }
        return new Transaction(TransactionId.mint(), clock.instant(), copied, reverses);
    }

    /**
     * Debits minus credits.
     *
     * <p>Each posting is mapped to its signed contribution and the results are
     * summed. Splitting the currency check out first means this reduction is a
     * plain fold with nothing conditional in it, and {@link Money#add} is
     * associative over a single currency, which is what {@code reduce} requires
     * to be correct.
     */
    private static Money netOf(Collection<Posting> postings) {
        Currency currency = requireSingleCurrency(postings);
        return postings.stream()
                .map(posting -> switch (posting.direction()) {
                    case DEBIT -> posting.amount();
                    case CREDIT -> posting.amount().negate();
                })
                .reduce(Money.zero(currency), Money::add);
    }

    /**
     * The currency every posting shares, or a refusal naming the first two that
     * differ.
     *
     * @throws MalformedTransactionException if the postings span more than one
     *     currency
     */
    private static Currency requireSingleCurrency(Collection<Posting> postings) {
        Currency first = postings.iterator().next().amount().currency();
        postings.stream()
                .map(posting -> posting.amount().currency())
                .filter(currency -> currency != first)
                .findFirst()
                .ifPresent(other -> {
                    throw MalformedTransactionException.mixedCurrencies(first, other);
                });
        return first;
    }

    /** This transaction's identity. */
    public TransactionId id() {
        return id;
    }

    /** When the economic event occurred, as reported by the clock it was built with. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** The postings, in the order given. Immutable. */
    public List<Posting> postings() {
        return postings;
    }

    /** The currency every posting shares. */
    public Currency currency() {
        // getFirst rather than get(0): SequencedCollection says what is meant.
        return postings.getFirst().amount().currency();
    }

    /**
     * The transaction this one reverses, if it is a correcting entry.
     *
     * <p>The presence of this link is what makes a transaction a reversal.
     * There is no separate type tag: a tag would say less — it would not name
     * which transaction was corrected — and could drift out of agreement with
     * the postings.
     */
    public Optional<TransactionId> reverses() {
        return Optional.ofNullable(reverses);
    }

    /** The total moved: the sum of the debits, which equals the sum of the credits. */
    public Money total() {
        return postings.stream()
                .filter(posting -> posting.direction() == Direction.DEBIT)
                .map(Posting::amount)
                .reduce(Money.zero(currency()), Money::add);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Transaction transaction && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Transaction " + id + " at " + occurredAt + " " + postings;
    }
}
