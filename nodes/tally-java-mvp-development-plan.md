---
id: 01M1M2TYB9BP16Z9RYEKJTBW3C
title: Tally Java MVP — development plan
kind: rfc
depends_on: [01M1M2QZB7RMP60Y5P7CX880SS]
created: 2026-09-03T16:50:05.801773221Z
updated: 2026-09-03T16:50:12.116856198Z
---

# Tally Java MVP — development plan

Written 2026-09-03, immediately after [[ADR 006 — Tally is rewritten in Java]].

## Goal of the MVP

A working double-entry ledger in Java 25 that enforces the financial invariants
by construction, with **no infrastructure at all** — no database, no HTTP, no
broker, no framework, no dependency injection. In-memory only.

This is deliberately the same scope Phase 1 had in Rust, plus the two pieces
Rust never reached: `Transaction` and the `Ledger`.

## Structure

One Gradle module (Kotlin DSL). Two packages, guarded by ArchUnit:

```
tally.domain   pure financial model
tally.core     composition over the domain
```

## The increments

Each is one commit, builds and passes tests on its own, and follows the pairing
cycle. Ordered by dependency, not by preference.

1. **Build skeleton** — Gradle Kotlin DSL, Java 25 toolchain, JUnit 5, ArchUnit,
   Error Prone. Remove `Cargo.toml`, `Cargo.lock`, `rust-toolchain.toml`,
   `src/`, `target/`.
2. **`Currency`** — enum carrying ISO 4217 code and decimal scale. JPY=0,
   USD/EUR/GBP/BRL=2, KWD=3. All three real scales, as before.
3. **`Money`** — `long` minor units plus currency. Every arithmetic operation
   goes through `Math.addExact` / `subtractExact`, surfacing overflow and
   currency mismatch instead of wrapping or converting. Carries ADR 001.
4. **`Direction`** — `DEBIT` / `CREDIT` as a direction, never a sign.
5. **`AccountId`** — UUIDv7 record. Needs a v7 minting decision first (JUG vs
   hand-rolled); see the note appended to ADR 003.
6. **`AccountKind`** — the five kinds, with the debit/credit sign rule *derived
   from the accounting equation* rather than written as a truth table. Sealed
   interface or enum with behaviour.
7. **`Account` + `Posting`** — currency fixed at opening, sign rule applied,
   `Posting` minted only through `Account.post` via a package-private
   constructor. Carries ADR 004 and ADR 005.
8. **`Transaction`** — the first genuinely new domain type. Enforces: at least
   two postings, and per-currency `sum(debits) == sum(credits)` with no
   implicit exchange rate. This is where invariants 2 and 5 live.
9. **`Ledger`** — append-only journal of posted transactions; balances derived
   by folding postings, never stored. Enforces invariant 3 (every posting
   references an account that exists), which is referential and needs the
   ledger as context.
10. **Guard rules** — ArchUnit: `tally.domain` imports nothing from
    `tally.core` or any infrastructure package; no domain class references
    `float` or `double`.
11. **Docs** — rewrite `CLAUDE.md` for Java/JVM pairing, rewrite `README.md`
    Implemented/Experimental/Planned honestly, write ADR 006 into `docs/adr/`.

## Open questions, not yet decided

- **Error handling.** Rust's `Result<T, E>` forced the caller to handle failure.
  Java offers checked exceptions, unchecked exceptions, or a hand-rolled
  `Result`. This needs its own ADR before increment 3, because `Money` is the
  first type that has to fail. The Rust working agreement banned `String`-based
  errors and required typed enums — the Java equivalent is a sealed error
  hierarchy, not exception messages.
- **UUIDv7 minting** — library or hand-rolled.
- **Immutability of collections.** `Transaction` holding a `List<Posting>` must
  defensively copy; Rust's ownership made this free.

## Explicitly out of scope for the MVP

Persistence, HTTP, idempotency, outbox/events, reconciliation, metrics,
concurrency beyond documenting that the in-memory ledger is not thread-safe.
Principle 9 stands: complexity must be earned.
