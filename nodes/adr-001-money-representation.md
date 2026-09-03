---
id: 01M1JZNSRSFDJNTQ1G2T4RXDP6
title: ADR 001 — Money representation
kind: rfc
aliases: [001-money-representation]
originProject: tally
origin: /home/iago/workspace/tally/docs/adr/001-money-representation.md
created: 2026-09-03T06:35:37.113610878Z
updated: 2026-09-03T18:24:57.543257623Z
documentName: 001-money-representation
---

# ADR 001 — Money representation

- **Status:** Accepted
- **Date:** 2026-08-26
- **Deciders:** Iago Ferreira

## Context

Every value in a ledger flows through the money type. If that type can lose
precision, silently wrap, or mix currencies, no amount of correctness work
above it can compensate. This decision is therefore made first and is the most
expensive one in the project to reverse.

Hard constraint, non-negotiable: **no `f32`/`f64` anywhere in the money path.**
Binary floating point cannot represent `0.10` exactly, errors compound over
large row counts, and there is no defensible way to audit a value that was
never exact.

Beyond that, the open questions were the numeric carrier, how currency is
identified, and whether arithmetic should be fallible.

## Options considered

### A. `i64` count of minor units + currency (chosen)

`Money { minor_units: i64, currency: Currency }`, e.g. `1050 + USD` is
`$10.50`.

- Range ±9.22×10^18 minor units, i.e. ±92 quadrillion USD. Global M2 is around
  10^14, so the headroom is roughly four orders of magnitude.
- 16 bytes, `Copy`, arithmetic is a single instruction. Nothing is faster.
- Maps to Postgres `BIGINT`: exact, indexable, no driver conversion surprises.
- Serialises losslessly. (Wire format should still be a *string* rather than a
  JSON number, because JavaScript clients silently mangle integers above 2^53 —
  that is an API-layer decision, deferred to Phase 3.)
- Cannot represent sub-minor-unit quantities. See "Load-bearing assumption".

### B. `i128` minor units

Same model with ~2×10^38 range, which would accommodate crypto's 18 decimals.
Rejected for now: it doubles the size of the hottest value type in the system,
and **Postgres has no native 128-bit integer**, so the clean `BIGINT` mapping
of option A is lost in exchange for headroom we have no concrete use for.
Principle 12 — distributed and numeric complexity must be earned.

### C. `rust_decimal::Decimal`

96-bit mantissa plus a per-value scale; essentially C#'s `decimal`. Exact
base-10 arithmetic, handles sub-minor precision, maps to `NUMERIC`.

Rejected on two grounds. Arithmetic is an order of magnitude slower than an
integer add. More importantly, **scale becomes a property of the value rather
than of the currency**: nothing prevents constructing `$10.005`, which is not a
representable amount of money, and `10.5` and `10.50` compare equal while being
distinct values. That moves correctness out of the type and into discipline,
which is the opposite of what we want from the foundation type.

### D. Arbitrary precision (`bigdecimal`)

Always correct, heap-allocating, not `Copy`, allocations in the hot path. It
solves a problem we do not have.

## Decision

1. **`i64` minor units + currency.** Option A.
2. **`Currency` is a closed enum**, not an open `{ code, scale }` struct. An
   invalid currency is unconstructible, and every `match` over currencies is
   checked for exhaustiveness by the compiler. The cost is that adding a
   currency requires a code change and a release. We would rather support six
   currencies honestly than 180 badly. The initial set deliberately spans all
   three ISO 4217 scales — JPY (0), USD/EUR/GBP/BRL (2), KWD (3) — so that
   scale bugs surface in our own tests rather than in production.
3. **Arithmetic returns `Result`.** `Money` does not implement `std::ops::Add`
   or `Sub`, because `Add::add` must return `Self` and therefore could only
   panic or lie on overflow and currency mismatch. In a ledger, arithmetic that
   can fail should *look* like it can fail at every call site. Verbosity is the
   feature.
4. **`Money` does not implement `Ord`/`PartialOrd`.** A derived implementation
   would compare amounts first and silently produce an ordering between USD and
   EUR. `try_cmp` returns `Result` instead.
5. **Currency mismatch is a runtime typed error, not a compile-time one.** We
   could encode currency in the type (`Money<Usd>` via phantom types) and get
   compile-time safety. Rejected: a ledger reads currencies from a database at
   runtime, so every I/O boundary would need a downcast back to a runtime check,
   while smearing generics across the entire codebase. Runtime check,
   exhaustively tested.
6. **`overflow-checks = true` in the release profile.** Rust wraps integer
   overflow in release builds by default and only panics in debug. A silently
   wrapped balance is the worst failure this system could have. Our own
   arithmetic is already checked; this is defense in depth for any raw `+` that
   slips in later. We accept the small performance cost.

## Load-bearing assumption

**Rounding happens at the boundary, never inside the ledger.**

A posting is a *settled fact*: an exact whole number of minor units. `$10.005`
is not postable — no bank moves half a cent. Interest accrual, percentage fees,
FX conversion and splits do produce non-representable intermediates, but those
roundings must be **explicit and allocated** (largest-remainder or banker's
rounding, with the residual assigned to a named posting) *before* a `Money`
value exists.

If this assumption holds, `i64` minor units is sufficient forever. If Tally
ever needs to post sub-minor amounts — crypto with 18 decimals, per-unit
pricing to 1/1000 — this assumption is the premise to attack, and option B or C
becomes the answer. A future reader disagreeing with this ADR should start
here.

## Consequences

- Money arithmetic is exact and fast; overflow and currency mismatch are
  observable rather than silent.
- Call sites are more verbose: `a.checked_add(b)?` rather than `a + b`.
- Adding a currency is a code change and a release.
- A separate rate/percentage type will be needed when interest and FX arrive.
  It is deliberately not designed yet.
- Sub-minor precision is out of scope, and revisiting it means revisiting this
  ADR rather than patching around it.

---

## Amended 2026-09-03 — Java rewrite ([[ADR 006 — Tally is rewritten in Java]])

The **decision stands**: money is exact minor units in a signed 64-bit integer,
paired with its currency. `i64` becomes `long`, which is the same type.

The **enforcement does not survive.** Two Cargo-level guarantees are lost:

- `float_arithmetic = "deny"` — no Java equivalent exists. Replaced by an
  ArchUnit test asserting no class in `tally.domain` references `float` or
  `double`, plus Error Prone at compile time.
- `overflow-checks = true` — Java's `long` wraps silently in every build and
  there is no profile switch to change it. Overflow safety must therefore live
  inside `Money` itself: `Math.addExact` / `Math.subtractExact` /
  `Math.multiplyExact`, which throw `ArithmeticException` rather than wrapping.

The consequence worth remembering: in Rust a raw `+` that slipped past review
would still panic in release. In Java it will quietly produce a wrong balance.
The `Math.*Exact` calls are now load-bearing, and dropping one is a silent
financial bug rather than a caught one.

---

## Amended again 2026-09-03 — see [[ADR 008 — Money holds BigInteger minor units]]

The **minor-unit decision stands and is reaffirmed**: amounts are still counted
in cents, yen and fils, and the scale still lives on `Currency`.

The **carrier changed** from `long` to `BigInteger`, after the maintainer
reopened the question of whether Java should use `BigDecimal`.

`BigDecimal` was rejected: it can hold `1.005 USD`, which demotes the minor-unit
invariant from structural to merely validated, and its `equals` compares scale
as well as value — poison for a `record` whose `equals` is generated from its
components.

The immediate consequence for *this* ADR: the overflow hazard described in the
previous amendment **is gone**, not merely mitigated. `MoneyError.Overflow` no
longer exists, `negate()` is total, and the warning above that "the
`Math.*Exact` calls are now load-bearing" no longer applies — there are none.
An unbounded representation removes the hazard instead of relying on every
future author remembering to reach for `Math.addExact`.

What has **not** changed is this ADR's stated pressure point: sub-minor amounts
remain unrepresentable. Removing the upper bound did nothing for that.