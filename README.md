# Tally

**Tally is a double-entry financial ledger written in Java, built to explore
correctness, consistency, concurrency, and failure handling in financial
systems.**

> ### Status: early development
>
> Tally is **not production-ready**, and no claim of production-readiness will
> be made here until there is evidence to support it. At the time of writing
> the in-memory domain is complete: Tally can open accounts, record balanced
> transactions in an append-only journal, derive balances from it, and correct
> mistakes by reversal. **There is no persistence, no transaction API and no durable concurrency
> support** — the ledger lives in memory, is not thread-safe, and disappears
> when the process does. The status section below is the source of truth;
> please read it before assuming any capability is present.
>
> Tally was originally written in Rust. It was rebuilt in Java in September
> 2026; the Rust implementation remains in git history.

---

## Why this exists

Most systems that move money treat the ledger as a `balance` column and an
`UPDATE` statement. That works until it doesn't, and when it doesn't, there is
no record of why.

Tally takes the opposite position: the ledger is an **append-only log of
immutable, self-consistent economic facts**, and every balance is derived from
it. This is not a novel idea — it is how bookkeeping has worked since Pacioli
codified it in 1494 — but it has real engineering consequences, and working
through those consequences carefully is the point of the project.

The interesting questions are things like: what happens when two transactions
post concurrently against the same account? Where exactly can a retried payment
duplicate itself? What does "exactly once" actually mean when a broker is
involved? How do you detect that your ledger and your payment processor have
silently diverged?

Java is used because the JVM is where this kind of system usually lives, and
because its type system — sealed hierarchies, records, exhaustive pattern
matching — can encode a useful share of the financial invariants such that
violating them fails to compile rather than failing in production.

---

## Status

### Implemented

- Single Gradle module, Java 25, toolchain pinned in `mise.toml` and again in
  the Gradle toolchain block
- `tally.domain` and `tally.core` separated by package. The domain has no
  infrastructure dependencies
- `Currency` — closed enum carrying its ISO 4217 code and decimal scale (all
  three real-world scales: JPY = 0, USD/EUR/GBP/BRL = 2, KWD = 3)
- `Money` — exact amounts as `BigInteger` **minor units** plus currency.
  Unbounded, so arithmetic cannot overflow; refuses to mix currencies and never
  converts implicitly
- `Direction` — `DEBIT`/`CREDIT` as a side, never a sign on the amount
- `AccountKind` — the five kinds, with the debit/credit sign rule *derived from
  the accounting equation* rather than tabulated
- `AccountId` — UUIDv7 record, so identifiers are minted without coordination
  and still sort near each other in an index. Rejects any non-v7 value
- `Account` — identity, kind and a currency fixed at opening; the only way to
  mint a `Posting`
- `Posting` — one leg of a movement, always strictly positive and always in its
  account's currency
- `Transaction` — a set of postings that balance. Enforces at least two
  postings and `sum(debits) == sum(credits)`, with `transfer`, `split` and
  `reverse` factories over the same model
- `TransactionId` — UUIDv7, like `AccountId`
- `Ledger` — an append-only journal of posted transactions. Derives balances by
  folding postings, refuses transactions naming unknown accounts, refuses
  duplicates, and refuses a reversal of something never posted
- `DomainException` — sealed hierarchy, so a handler switching over domain
  failures is checked for exhaustiveness and needs no `default` branch
- Quarkus HTTP API with OpenAPI JSON and Swagger UI
- `POST /accounts` — opens and registers an account in the process-local ledger

155 tests. Compilation runs with `-Xlint:all -Werror` and Error Prone.

Invariant 1 — no floating point — is currently upheld by review rather than by
the build. Java has no equivalent of the crate-wide lint the Rust version used,
and no replacement has been adopted yet.

### Experimental

Nothing.

### Not yet built

Everything outside the in-memory domain: persistence, transaction and journal
API behavior, concurrent posting, idempotency, events, reconciliation. See the
phase table below.

The `Ledger` is **not thread-safe**, and deliberately so — the consistency
guarantees of concurrent posting need a real storage model to answer, and
guessing at a locking scheme before Phase 2 would be solving the problem before
understanding it.

### Planned

The project is built in phases, and later phases are deliberately not started
until earlier ones are solid.

| Phase | Scope |
|---|---|
| 1 | ✅ Domain: accounts, postings, transactions, double-entry validation, in-memory ledger |
| 2 | Persistence: PostgreSQL, isolation levels, concurrent posting, immutable journal |
| 3 | [in progress] API: HTTP, with the domain kept free of HTTP types |
| 4 | Idempotency: safe retries of financial operations |
| 5 | Events: transactional outbox, delivery semantics stated precisely |
| 6 | Reconciliation: detecting divergence against an external processor |
| 7 | Production engineering: tracing, metrics, health checks, failure injection |
| 8 | Performance: measured, never assumed |

Phase 1 is complete. Phase 3 has its HTTP foundation and account-creation slice;
the remaining API behavior is not implemented.

---

## Financial invariants

These matter more than any API surface.

1. Amounts are exact. Floating point is never used to represent money.
2. A transaction contains at least two postings.
3. Every posting references an account that exists.
4. Posting amounts are strictly positive — direction is carried by
   `DEBIT`/`CREDIT`, never by the sign of the number.
5. Within a transaction, `sum(debits) == sum(credits)`. **The MVP restricts a
   transaction to a single currency and refuses a mix.** The intended rule is
   that multi-currency transactions balance *per currency*, with no implicit
   exchange rate — that is not implemented, and mixed-currency postings are
   rejected rather than silently mishandled.
6. A posted transaction is immutable. Corrections are reversing entries, not
   edits.
7. The journal is append-only.
8. Balances are derived from postings, not stored as independent truth.

**All eight are enforced.** 1, 2, 4 and 5 by construction — an invalid `Money`,
`Posting` or `Transaction` cannot be built. 6 and 7 by the `Ledger`, which never
edits or removes an entry and corrects by reversal. 8 because no balance is
stored anywhere; every balance is folded from the journal on demand. 3 is
referential and cannot be enforced by construction at all, so the `Ledger`
checks it — which is why accounts are registered with the ledger rather than
minted by it, since a ledger that made its own accounts would satisfy this
invariant vacuously.

---

## Design principles

- Correctness before performance; optimisations require measurements.
- Invalid financial states should be difficult or impossible to represent.
- Domain logic does not depend on HTTP, PostgreSQL, Kafka, or any framework.
- Consistency guarantees are documented, not assumed.
- Distributed-systems complexity must be earned. No technology is introduced to
  make the architecture look sophisticated.
- Idiomatic Java over clever Java.
- Tests verify invariants, not implementation details.

---

## Layout

```
src/main/java/tally/domain/   the pure financial model
src/main/java/tally/core/     composition over the domain (empty so far)
src/test/java/tally/domain/   tests
build.gradle.kts              one module, no module tree
mise.toml                     pinned JDK and Gradle
```

Tally is deliberately **one Gradle module**. Splitting the domain into its own
module would make its independence from infrastructure a compile error rather
than a convention, but there is no infrastructure to exclude yet, and the split
is a cheap mechanical refactor when there is. The boundary is currently upheld
by convention and review rather than by tooling: with `tally.core` still empty,
a rule forbidding the domain from importing it would have nothing to forbid.

## Getting started

The toolchain is pinned with [mise](https://mise.jdx.dev); `mise install` will
fetch the JDK and Gradle named in `mise.toml`. Note that a distribution's
default `java` package is often a headless JRE with no compiler — pinning
avoids that.

```sh
./gradlew build      # compile, run tests, run static analysis
./gradlew test       # tests only
```

---

## Documentation

Architecture decision records live in the maintainer's
[MindGraph](https://mindgraph.dev) vault rather than in this repository, as
linked notes that record what each decision supersedes and what depends on it.
They are not currently published here, so this README is the only design
documentation a reader of this repository has.

Decisions recorded so far cover money representation, module layout, account
identity, single-currency accounts, posting construction, the move from Rust to
Java, error handling, and the `BigInteger` representation.

ADRs are written only for decisions actually made. There are no placeholder
records for future phases.

---

## Licence

Dual-licensed under MIT or Apache-2.0, at your option.
