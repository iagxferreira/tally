/**
 * The pure financial domain: money, accounts, postings, transactions.
 *
 * <p>Nothing in this package may depend on infrastructure — no HTTP, no JDBC,
 * no serialisation framework, no dependency injection — and nothing here may
 * import from {@code tally.core}. That independence is a guarded convention,
 * not a build-level compile error, because Tally is a single Gradle module.
 * An ArchUnit rule enforces it; see ADR 002 and ADR 006.
 *
 * <p>Amounts in this package never use {@code float} or {@code double}.
 */
package tally.domain;
