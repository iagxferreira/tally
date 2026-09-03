---
id: 01M1M88829PBA3NCJQAZ0TRQVX
title: ADR 008 — Money holds BigInteger minor units
kind: rfc
depends_on: [01M1M2QZB7RMP60Y5P7CX880SS]
relates_to: [01M1JZNSRSFDJNTQ1G2T4RXDP6]
created: 2026-09-03T18:24:44.617128266Z
updated: 2026-09-03T18:24:51.793322657Z
---

# ADR 008 — Money holds BigInteger minor units

**Status:** accepted, 2026-09-03
**Amends:** the representation chosen in [[ADR 001 — Money representation]]
**Follows:** [[ADR 006 — Tally is rewritten in Java]]

## Context

ADR 001 chose `i64` minor units for Rust, and [[ADR 006 — Tally is rewritten in Java]]
carried that over as `long`. Once `Money` existed in Java the question was
reopened: on the JVM, shouldn't money be `BigDecimal`?

It is a fair question — `BigDecimal` is the conventional Java answer — and it
was examined properly rather than waved away.

## Decision

`Money` holds a **`BigInteger` count of minor units** plus its `Currency`.

Amounts are still counted in **cents** (or yen, or fils). Only the carrier
changed. The currency's scale still exists solely to give the count meaning and
to format it; no fractional major unit is ever held.

## Why not BigDecimal

**1. The invariant stops being structural.** Minor units make "amounts are exact
multiples of the currency's minor unit" impossible to violate — half a cent is
not an integer number of cents. `BigDecimal` will happily hold `1.005 USD`,
demoting that invariant to a rule that must be revalidated everywhere. Principle
5 says invalid financial states should be difficult to represent.

**2. `BigDecimal.equals` compares scale, not just value.** `1.50` and `1.5` are
unequal by `equals` while `compareTo` calls them identical. `Money` is a
`record`, so its `equals` is generated from its components — it would inherit
that split-brain directly, breaking `HashMap` keys, `List.contains`,
`distinct()`, and every AssertJ `isEqualTo` in the suite. Avoiding it means
overriding `equals` on a record to delegate to `compareTo`, which is fighting
the type. `BigInteger` has no scale, so the generated `equals` is simply right.

**3. `BigDecimal` dominates Java largely for infrastructure reasons** — JPA and
JDBC map `DECIMAL` to it. That is an infrastructure concern shaping a domain
type, which principle 6 forbids. Mapping `BigInteger` to `NUMERIC` in Phase 2 is
straightforward.

## Why BigInteger rather than long

Unboundedness **deletes an entire class of failure instead of adding one**:

- `MoneyError.Overflow` no longer exists. `CurrencyMismatch` is the only way
  arithmetic can fail, because mixing currencies is the only thing left to
  refuse.
- `negate()` is now **total**. It was fallible only because two's complement has
  no positive counterpart for `Long.MIN_VALUE` — an asymmetry that also caused a
  real formatting bug during the `long` implementation, where
  `Math.abs(Long.MIN_VALUE)` returns a negative number.
- `add` and `subtract` still return `Result`, for currency mismatch alone.

This matters more in Java than it did in Rust. Rust could set
`overflow-checks = true` so a stray `+` panicked rather than wrapping; Java's
`long` wraps silently in every build with no equivalent switch (see the note on
[[ADR 001 — Money representation]]). Removing the bound removes the hazard
rather than relying on every future `Math.addExact` call being remembered.

## Costs accepted

- An allocation per arithmetic operation, and no primitive comparison.
  Correctness before performance; principle 8 forbids optimising without a
  measurement, and there is none.
- Slightly noisier construction, mitigated by keeping a `long` overload of
  `Money.of` that widens immediately. Nothing downstream is bounded by `long`.

## Consequences

Sub-minor amounts remain unrepresentable — crypto at 18 decimals or per-unit
pricing to 1/1000 still breaks the model. That was ADR 001's stated pressure
point and this ADR does **not** relieve it; it only removes the upper bound. If
sub-minor amounts are ever needed, the answer is a smaller minor unit or an
explicit scale, not `BigDecimal`.

Implemented in commit `8d8b042`. Tests grew to 20, including a thousand-fold
accumulation of `Long.MAX_VALUE` that would previously have wrapped into a
plausible, wrong balance.
