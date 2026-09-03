package tally.domain;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.Objects;
import java.util.UUID;

/**
 * The identity of a {@link Transaction}: a UUID version 7, for the same reasons
 * as {@link AccountId} — mintable without coordination, and sorting in very
 * nearly the order it was minted, which matters more here than it does for
 * accounts because the journal is append-only and read in time order.
 *
 * <p>This duplicates {@link AccountId} almost exactly, and that is deliberate.
 * A shared generic {@code Id<T>} would remove about forty lines and give back a
 * type that says less: the entire purpose of these wrappers is that an account
 * identifier and a transaction identifier are <em>not</em> interchangeable, and
 * a generic parameterised on a phantom type is a more elaborate way of saying
 * what two plain classes already say. Explicit domain concepts over generic
 * abstractions.
 */
public record TransactionId(UUID value) implements Comparable<TransactionId> {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private static final int UUID_VERSION_7 = 7;

    /**
     * @throws NullPointerException if {@code value} is null
     * @throws IllegalArgumentException if {@code value} is not a version 7 UUID
     */
    public TransactionId {
        Objects.requireNonNull(value, "value");
        if (value.version() != UUID_VERSION_7) {
            throw new IllegalArgumentException(
                    "transaction identifiers must be UUID version 7, got version " + value.version());
        }
    }

    /** Mints a new identity. Requires no coordination with anything. */
    public static TransactionId mint() {
        return new TransactionId(GENERATOR.generate());
    }

    /** Rebuilds an identity that already exists — from a journal row, say. */
    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }

    /** Orders by the underlying UUID, which for version 7 is very nearly mint order. */
    @Override
    public int compareTo(TransactionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
