---
id: 01M1JZSF51ZD17X6TC2E4BYWE9
title: Tally
kind: note
created: 2026-09-03T06:37:37.313118126Z
updated: 2026-09-03T06:37:37.313118126Z
origin: /home/iago/workspace/tally/README.md
originProject: tally
documentName: README
---

# Tally

**Tally is a double-entry financial ledger written in Rust, built to explore correctness, consistency, concurrency, and failure handling in financial systems.**

> ### Status: early development
>
> Tally is **not production-ready**, and no claim of production-readiness will be
> made here until there is evidence to support it. At the time of writing the
> domain has money, accounts and postings; transactions and the ledger itself do
> not exist. The status table below is the source of truth — please read it
> before assuming any capability is present.

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

Rust is used because its ownership model, type system, and explicit error
handling make it possible to encode financial invariants such that violating
them is a compile error rather than a runtime incident.

---

## Status

### Implemented

- Single crate (edition 2024), pinned toolchain, CI-ready lint policy
- Lint policy that **denies floating-point arithmetic** crate-wide, and enables
  integer overflow checks in release builds
- A `domain` module holding the pure financial model, with no infrastructure
  dependencies
- `Currency` — closed enum carrying its ISO 4217 code and decimal scale
  (all three real-world scales represented: JPY = 0, USD/EUR/GBP/BRL = 2, KWD = 3)
- `Money` — exact amounts as `i64` minor units + currency, with fallible
  arithmetic that surfaces overflow and currency mismatch instead of wrapping
  or converting implicitly
- `Direction` — `Debit`/`Credit` as a direction, never a sign on the amount
- `AccountId` — UUIDv7 newtype, so identifiers can be minted without
  coordination and still sort near each other in an index
  ([ADR 003](docs/adr/003-account-identity.md))
- `AccountKind` — the five kinds, with the debit/credit sign rule derived from
  the accounting equation rather than written out as a truth table
- `Account` — identity, kind and a currency fixed at opening; applies the sign
  rule and rejects amounts in any other currency
  ([ADR 004](docs/adr/004-single-currency-accounts.md))
- `Posting` — one leg of a movement, minted only through `Account::post`, so a
  posting is always strictly positive and always in its account's currency
  ([ADR 005](docs/adr/005-posting-construction.md))

### Experimental

Nothing yet.

### Planned

The project is built in phases, and later phases are deliberately not started
until earlier ones are solid.

| Phase | Scope |
|---|---|
| 1 | Domain: accounts, postings, transactions, double-entry validation, in-memory ledger, property tests |
| 2 | Persistence: PostgreSQL, isolation levels, concurrent posting, immutable journal |
| 3 | API: async HTTP over Tokio, with the domain kept free of HTTP types |
| 4 | Idempotency: safe retries of financial operations |
| 5 | Events: transactional outbox, delivery semantics stated precisely |
| 6 | Reconciliation: detecting divergence against an external processor |
| 7 | Production engineering: tracing, metrics, health checks, failure injection |
| 8 | Performance: measured, never assumed |

---

## Financial invariants

These matter more than any API surface.

1. Amounts are exact. Floating point is never used to represent money.
2. A transaction contains at least two postings.
3. Every posting references an account that exists.
4. Posting amounts are strictly positive — direction is carried by
   `Debit`/`Credit`, never by the sign of the number.
5. For each currency within a transaction, `sum(debits) == sum(credits)`.
   Multi-currency transactions balance **per currency**; there is no implicit
   exchange rate.
6. A posted transaction is immutable. Corrections are reversing entries, not
   edits.
7. The journal is append-only.
8. Balances are derived from postings, not stored as independent truth.

Invariants 1, 2, 4 and 5 are intrinsic and are enforced by construction.
Invariant 3 is referential and requires the ledger as context.

---

## Design principles

- Correctness before performance; optimisations require measurements.
- Invalid financial states should be difficult or impossible to represent.
- Domain logic does not depend on HTTP, PostgreSQL, Kafka, or any framework.
- Consistency guarantees are documented, not assumed.
- Distributed-systems complexity must be earned. No technology is introduced to
  make the architecture look sophisticated.
- Idiomatic Rust over clever Rust.
- Tests verify invariants, not implementation details.

---

## Layout

```
src/lib.rs        crate root
src/domain.rs     the pure financial domain
src/domain/       currency, money, direction, account, posting
                  (transactions and the ledger to follow)
docs/adr/         decision records
```

Tally is deliberately a single crate. Splitting the domain into its own crate
would make its independence from infrastructure a compile error rather than a
convention, but there is no infrastructure to exclude yet, and the split is a
cheap mechanical refactor when there is. See
[ADR 002](docs/adr/002-crate-and-module-layout.md).

## Getting started

Requires the toolchain pinned in `rust-toolchain.toml` (rustup will fetch it
automatically).

```sh
cargo test --all
cargo clippy --all-targets --all-features
cargo fmt --all --check
```

---

## Documentation

- `docs/architecture.md` — system structure and boundaries *(not written yet)*
- `docs/concepts/` — ledger and distributed-systems concepts *(not written yet)*
- `docs/adr/` — architecture decision records:
  - [001 — Money representation](docs/adr/001-money-representation.md)
  - [002 — Crate and module layout](docs/adr/002-crate-and-module-layout.md)
  - [003 — Account identity](docs/adr/003-account-identity.md)
  - [004 — Single-currency accounts](docs/adr/004-single-currency-accounts.md)
  - [005 — Posting construction](docs/adr/005-posting-construction.md)

ADRs are written only for decisions actually made. There are no placeholder
records for future phases.

---

## Licence

Dual-licensed under MIT or Apache-2.0, at your option.