# ADR 006 — Tally is rewritten in Java

- **Status:** Accepted
- **Date:** 2026-09-03
- **Deciders:** Iago Ferreira
- **Supersedes:** ADR 002 entirely; the enforcement mechanism of ADR 001

## Context

Tally was built in Rust across five ADRs and roughly 1460 lines of domain code:
`Currency`, `Money`, `Direction`, `AccountId`, `AccountKind`, `Account` and
`Posting`. The maintainer has decided to rebuild it on the JVM.

The project had two stated goals of equal weight: build a serious ledger, and
come away understanding Rust deeply. The second goal is **explicitly replaced** —
the learning target is now Java and the JVM. This is a deliberate change of
purpose, not a migration forced by a technical constraint, and it should not be
recorded as though the Rust work failed. It did not.

## Decision

Replace the Rust implementation in place on `main`. The Rust domain survives in
git history only.

- **Build:** Gradle, Kotlin DSL, wrapper-pinned
- **Language level:** Java 25 (LTS)
- **Structure:** one Gradle module; `tally.domain` and `tally.core` separated by
  package, guarded by ArchUnit
- **Lost lint policy replaced by:** ArchUnit tests and Error Prone

## What carries over

Most of the domain decisions are language-independent and remain live.

| ADR | Status after the move |
|---|---|
| 001 — Money representation | Representation stands (`i64` → `long`). **Enforcement does not.** |
| 002 — Crate and module layout | Superseded. Posture carries over, mechanism does not. |
| 003 — Account identity | Stands. UUIDv7 is no longer free to mint. |
| 004 — Single-currency accounts | Unchanged. Pure domain rule. |
| 005 — Posting construction | Rule stands, mechanism weakens. |

## Consequences

Three guarantees degrade from compile-time to test-time or convention. This is
the real cost of the move and is stated plainly rather than papered over.

1. **No floating point.** Was `float_arithmetic = "deny"`, a crate-wide clippy
   lint. Becomes an ArchUnit test asserting no class in `tally.domain`
   references `float` or `double`, plus Error Prone at compile time.
2. **No silent overflow.** Was `overflow-checks = true` in the release profile,
   so a raw `+` that slipped past review would still panic rather than corrupt
   a balance. Java's `long` wraps silently in **every** build and no profile
   switch changes that. Overflow safety therefore moves inside `Money` itself
   via `Math.addExact` and friends, which throw `ArithmeticException`. Those
   calls are now load-bearing: dropping one is a silent financial bug where in
   Rust it was a caught one.
3. **Construction invariants.** Rust enforced `Posting`'s constructor with
   module privacy, which a sibling module could not reach. Java gets package
   privacy, which anything later added to `tally.domain` can reach. The
   guarantee drops from "the compiler forbids it" to "review must keep it that
   way".

In exchange, the project gains the ecosystem the maintainer works in
professionally. That is the trade, and it was made with the costs visible.

## MVP structure

The ADR 002 successor question is answered: **one Gradle module, not a module
tree.**

```
src/main/java/tally/domain/   pure financial model
src/main/java/tally/core/     composition over the domain
```

Explicitly rejected for the MVP: a `:tally-domain` / `:tally-core` /
`:tally-app` module tree, hexagonal package scaffolding, dependency injection,
and any framework. None of it is earned yet. Principle 9 — complexity must be
earned — carries over unchanged.
