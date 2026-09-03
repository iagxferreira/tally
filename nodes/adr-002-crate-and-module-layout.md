---
id: 01M1JZNSRVD5ZJVVYG7CCR5GH4
title: ADR 002 — Crate and module layout
kind: rfc
aliases: [002-crate-and-module-layout]
originProject: tally
origin: /home/iago/workspace/tally/docs/adr/002-crate-and-module-layout.md
created: 2026-09-03T06:35:37.115804798Z
updated: 2026-09-03T16:49:11.854704970Z
documentName: 002-crate-and-module-layout
---

# ADR 002 — Crate and module layout

- **Status:** Accepted
- **Date:** 2026-08-26
- **Deciders:** Iago Ferreira

## Context

Principle 6 states that domain logic must not depend on HTTP, PostgreSQL,
Kafka, or frameworks, and principle 7 that infrastructure should sit behind
explicit boundaries. The question is what mechanism enforces those boundaries.

Tally originally shipped as a Cargo workspace with `crates/tally-core`, with
`crates/tally-storage` and `crates/tally-api` planned. At the time of this
decision the workspace contains exactly one crate and roughly 300 lines of
domain code, and there is no infrastructure dependency anywhere in the tree.

## The mechanism a crate boundary provides

This is the substance of the decision, and it is specific to Rust.

**Module privacy does not enforce dependency direction.** Within a crate, any
module can reach any other module's `pub(crate)` items, and — unlike crates —
**Rust modules are permitted to form cycles**. A single crate therefore allows
`domain` and `api` to reference each other, and the compiler will not object.

Crates are different. The inter-crate dependency graph is a DAG enforced by
Cargo, and a crate can only import what its own `Cargo.toml` declares. If
`tally-core` does not depend on `sqlx`, then `use sqlx::PgPool;` inside the
domain is `error[E0432]: unresolved import`. The architectural rule stops being
a review comment and becomes a compile error.

Secondary benefits: crates are the unit of parallel and incremental
compilation, and a published `tally-core` would let a consumer depend on the
ledger model without pulling in Tokio or Axum.

## Decision

**Tally is a single crate.** The pure domain lives in a `domain` module rather
than a separate crate:

```
src/lib.rs        crate root, re-exports the domain
src/domain.rs     module root for the pure domain
src/domain/       currency.rs, money.rs, ...
```

Module files use the modern style — `src/domain.rs` next to `src/domain/` —
rather than `src/domain/mod.rs`.

Rationale:

1. **A workspace containing one crate defends nothing.** There is no
   infrastructure dependency to exclude, so the boundary currently costs
   ceremony and buys no enforcement. Principle 12 — complexity must be earned —
   applies to build topology as much as to distributed systems.
2. **The deferral is cheap.** Extracting a crate in Rust is a mechanical
   refactor: move a directory, add a `Cargo.toml`, rewrite `crate::domain::`
   to `tally_core::`. It is a rename, not a redesign. Paying ceremony now to
   avoid a cheap refactor later is a bad trade.
3. **Single-crate is the common Rust layout**, not a deviation. Workspaces are
   generally adopted for compilation and publishing reasons, which do not yet
   apply at this size.

## Consequences

- The domain's purity is a **convention**, enforced by review and by this
  document, not by the compiler. This is a genuine loss and is the price of the
  decision.
- Module cycles between future `domain` and `api` modules are possible and must
  be avoided deliberately.
- The whole crate recompiles on any change. Irrelevant at this size.
- Nothing here changes the *logical* boundary: the `domain` module is where the
  seam is, so extraction remains a directory move.

## Revisit trigger

**The first infrastructure dependency added to `Cargo.toml` — realistically
`sqlx` in Phase 2 — is the trigger to reopen this ADR.** At that moment the
crate boundary starts buying real enforcement, and the domain should be
extracted into `tally-core` before the dependency is introduced, not after.

Do not reintroduce a workspace before that trigger fires.

---

## Superseded 2026-09-03 by [[ADR 006 — Tally is rewritten in Java]]

This ADR reasoned about Cargo crates and Rust module privacy. Both are gone.

The **posture carries over even though the mechanism does not.** ADR 002 chose
a single crate with the domain's independence as a deliberately guarded
convention, on the grounds that there was no infrastructure to exclude yet and
the split would be a cheap mechanical refactor later. The Java MVP makes the
same call for the same reason: **one Gradle module**, with `tally.domain`
separated from `tally.core` by package, and an ArchUnit rule enforcing that the
domain imports nothing from `core` or from any infrastructure package.

Rejected for the MVP: a `:tally-domain` / `:tally-core` / `:tally-app` module
tree, and hexagonal package scaffolding. Same trigger as before — when the
first infrastructure dependency lands, revisit and split then.

One thing genuinely weakens. Rust's module privacy could stop a *sibling
module* from reaching a constructor; Java's package privacy cannot stop
anything in the same package. See the note on ADR 005.