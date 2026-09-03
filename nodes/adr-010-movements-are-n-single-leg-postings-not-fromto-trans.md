---
id: 01M1MCAMSF82WHRQ7AZCRCSMGJ
title: ADR 010 — Movements are N single-leg postings, not from/to transfers
kind: rfc
relates_to: [01M1JZNSSTPVS2RT39A4VBMH0M]
context_for: [01M1M2WS9DWCXM34B3KG63HB86]
created: 2026-09-03T19:35:57.487273139Z
updated: 2026-09-03T19:36:38.034096963Z
---

# ADR 010 — Movements are N single-leg postings, not from/to transfers

**Status:** accepted, 2026-09-03
**Decides:** the shape [[Transaction — enforce the double-entry balance invariant]] is built on
**Relates to:** [[ADR 005 — Postings are minted through their account]]

## Context

`Posting` was built as one leg — an account, a direction, a positive amount.
The maintainer asked the obvious question: shouldn't a posting be `from → to`?

It is the right question to ask, and the answer is not automatic — both models
exist in production systems.

## Why one leg rather than a pair

**Multi-leg events are ordinary, not exotic.** A €110 sale with tax:

```
DEBIT   cash          110
CREDIT  revenue       100
CREDIT  tax payable    10
```

Three legs, **one** economic event. No `from → to` expresses this. Faking it as
two transfers invents an event that never happened and destroys the "one
transaction is one fact" property that makes a journal auditable. Payroll
splitting gross into net, tax and pension has the same shape; so does a fee
withheld from a settlement.

**`from`/`to` also smuggles in an assumption.** It implies decrease → increase,
which is only true for asset accounts. Paying down a loan *debits* a liability:
money leaves an asset and the liability shrinks — both go "down" in ordinary
language. That intuition is exactly what
[[Port Direction, AccountId and AccountKind to Java]] avoided by deriving the
sign rule from the accounting equation instead of tabulating it.

## The counter-argument, which is real

**TigerBeetle uses two-legged transfers** — `debit_account_id` /
`credit_account_id` — and it is among the most serious financial ledgers built
in the last decade. The restriction buys something significant: a two-legged
transfer is **balanced by construction**. Invariant 5 stops being validated and
becomes unrepresentable-if-violated, which is principle 5 in its strongest form.
Multi-leg cases are composed from linked transfers.

| | N-legged postings | `from → to` transfer |
|---|---|---|
| Multi-leg (tax, fees, splits) | Natural | Needs linked transfers |
| Balance invariant | **Validated** | **Structural** |
| Per-currency multi-currency balance | Expressible | Not really |
| Used by | General ledgers | TigerBeetle, payment ledgers |

## Decision

**Keep single-leg postings**, and add a convenience factory for the common 1:1
case:

```java
// reads as from -> to
Transaction.transfer(cash, revenue, Money.of(2500, USD));

// and three legs remain expressible
Transaction.of(
    cash.debit(Money.of(110, EUR)),
    revenue.credit(Money.of(100, EUR)),
    taxDue.credit(Money.of(10, EUR)));
```

`transfer` is a **constructor convenience, not a second model**. It mints a
debit and a credit and delegates to the same validation. There must be no code
path where a transfer is treated as anything other than two postings.

## Consequences

- The balance invariant stays **validated rather than structural**. This is the
  price of multi-leg expressiveness and should be stated plainly rather than
  glossed: `Transaction` must check it, and that check must be well tested,
  because nothing in the type system enforces it.
- README invariants 2 and 5 are unchanged — they already describe this model.
- `transfer` covering the majority of call sites means the wordier `of` form is
  reserved for the cases that genuinely need it, which is also a readability
  win: seeing `Transaction.of` with three legs signals something worth reading.

---

## Addendum — the factory vocabulary: transfer, split, reverse

The maintainer proposed three named constructors rather than one. Accepted,
with one renaming.

All three are **constructor conveniences over the same model** — each mints
postings and delegates to the same validation. There must be no code path where
any of them is treated as a distinct kind of thing.

### `transfer(from, to, amount)`

The 1:1 case. Two legs, one debit and one credit.

### `split(source, direction, destinations)`

One leg against several. The cases this names are ordinary, not exotic:

```
DEBIT   cash          110      // a sale with tax
CREDIT  revenue       100
CREDIT  tax payable    10
```

Also: a settlement with a fee withheld, payroll splitting gross into net, tax
and pension. The balance check already enforces that the source equals the sum
of the destinations, so `split` adds a name and an intent, not a new rule.

### `reverse(original)` — **not** `rollback`

The operation is right; the word is wrong and the distinction matters.

In a database, *rollback* discards uncommitted work and leaves nothing behind.
A posted transaction cannot be discarded. Invariant 6: **a posted transaction is
immutable; corrections are reversing entries, not edits.** The correction is an
equal and opposite transaction, and **both** remain in the journal permanently —
that is the point, because an auditor must be able to see the mistake *and* its
correction. Naming it `rollback` would import the expectation that something
disappears, which is precisely what an append-only journal must never do.

Reversal flips the direction of every posting and keeps the amounts.

**This creates a real dependency:** a reversal must reference the transaction it
reverses, or the correction is untraceable and the pair cannot be reconciled.
That requires `TransactionId`, so `reverse` cannot be built before
[[Decide Transaction identity and timestamp]] is settled.

Open sub-question for that decision: should a reversal be refusable — can a
transaction be reversed twice, or can a reversal itself be reversed? Reversing a
reversal is arithmetically identical to reposting the original, and whether that
should be *expressible* is a modelling question, not a coding one.