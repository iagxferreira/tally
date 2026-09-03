---
id: 01M1M2QZB7RMP60Y5P7CX880SS
title: ADR 006 — Tally is rewritten in Java
kind: rfc
relates_to: [01M1JZSF51ZD17X6TC2E4BYWE9, 01M1JZNSRSFDJNTQ1G2T4RXDP6, 01M1JZNSRVD5ZJVVYG7CCR5GH4, 01M1JZNSS9GVH9M87PX34Q1Y91, 01M1JZNSSQ238695ZFJ63XYVYZ, 01M1JZNSSTPVS2RT39A4VBMH0M, 01M1JZSF4EY9ZKAB3TX8VYQ5PD]
context_for: [01M1M2TYB9BP16Z9RYEKJTBW3C]
created: 2026-09-03T16:48:28.519253307Z
updated: 2026-09-03T16:50:25.146455676Z
---

# ADR 006 — Tally is rewritten in Java

**Status:** accepted, 2026-09-03
**Supersedes:** ADR 002 entirely; the *enforcement mechanism* of ADR 001

## Context

Tally was built in Rust across five ADRs and ~1460 lines of domain code
(`Currency`, `Money`, `Direction`, `AccountId`, `AccountKind`, `Account`,
`Posting`). The maintainer has decided to rebuild it on the JVM.

The project had two stated goals of equal weight: build a serious ledger, and
come away understanding Rust deeply. The second goal is explicitly replaced —
the learning target is now Java and the JVM, not Rust. This is a deliberate
change of purpose, not a technical migration forced by a constraint.

## Decision

Replace the Rust implementation in place on `main`. The Rust domain survives in
git history only; `src/`, `Cargo.toml`, `Cargo.lock` and `rust-toolchain.toml`
are removed.

- **Build:** Gradle with the Kotlin DSL
- **Language level:** Java 25 (LTS)
- **Float ban enforcement:** ArchUnit tests *and* Error Prone

## What carries over, and what does not

The domain decisions are mostly language-independent and remain live:

- **ADR 001 (money as `i64` minor units)** — the *representation* carries over
  as `long` minor units. The *enforcement* does not: the crate-wide
  `float_arithmetic = "deny"` clippy lint has no Java equivalent, and neither
  does `overflow-checks = true`. Java's `long` arithmetic wraps silently in all
  builds with no profile to change that, so overflow safety must move into
  `Money` itself via `Math.addExact` / `Math.subtractExact`, which throw
  `ArithmeticException`. Replaced by ArchUnit + Error Prone; see below.
- **ADR 002 (single crate, no workspace)** — superseded outright. Cargo crates
  and Gradle modules are different mechanisms with different failure modes.
  The successor question is whether the domain lives in its own Gradle module
  or in one module guarded by ArchUnit.
- **ADR 003 (UUIDv7 account identity)** — the decision holds. The
  implementation does not come free: `java.util.UUID.randomUUID()` mints v4
  only. Minting v7 needs a library (JUG) or a hand-rolled
  timestamp-plus-random constructor.
- **ADR 004 (accounts denominated in a single currency)** — unchanged. Pure
  domain rule, no Rust in it.
- **ADR 005 (postings minted through their account)** — the rule holds; the
  mechanism changes. Rust enforced it with module privacy on the constructor.
  Java's nearest equivalent is a package-private constructor plus a sealed or
  final `Posting`, which is weaker: anything in the same package can bypass it,
  where Rust's `pub(super)` could not be reached from a sibling module.

## Consequences

Guarantees that were compile-time in Rust become test-time or convention in
Java. The three that degrade:

1. No floating point — was a deny lint, becomes an ArchUnit test.
2. No silent overflow — was `overflow-checks`, becomes explicit `Math.*Exact`
   calls that a reviewer must not drop.
3. Construction invariants — were module privacy, become package privacy.

This is the real cost of the move and it should be stated plainly rather than
papered over. In exchange the project gains the JVM ecosystem the maintainer
works in professionally.

## Addendum — MVP structure (decided same day)

The ADR 002 successor question is answered: **one Gradle module, not a module
tree.** The domain is segregated by package, not by build unit:

```
src/main/java/tally/domain/   pure financial model — no Spring, JDBC, Jackson
src/main/java/tally/core/     everything that composes the domain
```

An ArchUnit rule enforces that `tally.domain` imports nothing from `tally.core`
and nothing from any infrastructure package. This is the same posture ADR 002
took for Rust — independence as a guarded convention rather than a build-level
compile error — and it is chosen for the same reason: there is no
infrastructure to exclude yet, and splitting into Gradle modules later is a
mechanical refactor.

Explicitly rejected for the MVP: hexagonal/ports-and-adapters package layout,
a `:tally-domain` / `:tally-core` / `:tally-app` module tree, dependency
injection, and any framework. None of it is earned yet. Principle 9 —
"distributed-systems complexity must be earned, no architecture cosplay" —
carries over from the Rust working agreement unchanged.