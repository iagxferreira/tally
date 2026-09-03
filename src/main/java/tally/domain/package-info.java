/**
 * The pure financial domain: money, accounts, postings, transactions, and the
 * ledger that holds them.
 *
 * <p>Nothing in this package may depend on infrastructure — no HTTP, no JDBC,
 * no serialisation framework, no dependency injection. That independence is
 * upheld by review rather than by the build.
 *
 * <p>Amounts here never use {@code float} or {@code double}.
 *
 * <p>The domain is one flat package, deliberately. Two invariants depend on it:
 * {@code Posting}'s constructor and {@code flip()} are package-private so only
 * {@code Account} and {@code Transaction} can mint a posting, and
 * {@code DomainException} is {@code sealed}, which outside a named module
 * requires its permitted subclasses to share its package.
 *
 * <p>Keeping it flat is also ADR 005's stated mitigation for package privacy
 * being weaker than Rust's module privacy: the guarantee rests on this package
 * staying small enough to read in full. When it stops being so, the answer is
 * to extract a real module, not to subdivide.
 */
package tally.domain;
