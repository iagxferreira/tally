package tally.domain;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.Objects;
import java.util.UUID;

/**
 * The identity of an {@link Account}: a UUID version 7.
 *
 * <p>Version 7 is chosen for two properties a ledger actually needs. It can be
 * minted <em>without coordination</em> — no database round trip, no sequence,
 * no central allocator — which keeps identity assignment out of the write path
 * and works unchanged if identifiers are ever minted on more than one node. And
 * because its leading 48 bits are a Unix millisecond timestamp, identifiers
 * minted near in time sort near each other, so index inserts stay clustered
 * instead of scattering across the B-tree the way random version 4 values do.
 *
 * <p>See ADR 003. That decision was made for Rust, where
 * {@code uuid = { features = ["v7"] }} supplied it; on the JVM the JDK does not
 * mint version 7 at all — {@link UUID#randomUUID()} is version 4 — so this
 * delegates to the JUG library. JUG is a value-type library: it brings no I/O,
 * no runtime and no framework, so it does not breach the rule that the domain
 * stays free of infrastructure.
 *
 * <p>A wrapper rather than a bare {@link UUID}. Passing raw UUIDs around means
 * nothing stops an {@code AccountId} being handed where a {@code TransactionId}
 * belongs; both are 128 bits and the compiler cannot tell them apart. The
 * allocation is real and accepted.
 */
public record AccountId(UUID value) implements Comparable<AccountId> {

    /**
     * Thread-safe per JUG's contract, so one shared generator is correct and
     * avoids re-seeding a {@link java.security.SecureRandom} per mint.
     */
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private static final int UUID_VERSION_7 = 7;

    /**
     * @throws NullPointerException if {@code value} is null
     * @throws IllegalArgumentException if {@code value} is not a version 7
     *     UUID. Accepting a version 4 here would silently forfeit the ordering
     *     property this type exists for, and nothing downstream would notice —
     *     index locality degrades quietly rather than failing.
     */
    public AccountId {
        Objects.requireNonNull(value, "value");
        if (value.version() != UUID_VERSION_7) {
            throw new IllegalArgumentException(
                    "account identifiers must be UUID version 7, got version " + value.version());
        }
    }

    /** Mints a new identity. Requires no coordination with anything. */
    public static AccountId mint() {
        return new AccountId(GENERATOR.generate());
    }

    /**
     * Rebuilds an identity from a value that already exists — a database row,
     * a request payload. Validates the version like any other construction.
     */
    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    /**
     * Orders by the underlying UUID, which for version 7 is very nearly time
     * order.
     *
     * <p>{@link UUID#compareTo} compares the most significant bits as a
     * <em>signed</em> long, which would be wrong for arbitrary UUIDs. It is
     * correct here: a version 7 UUID's top bit is part of the millisecond
     * timestamp and stays zero until well past the year 10000, so the signed
     * and unsigned orderings agree for every timestamp this code will ever see.
     */
    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
