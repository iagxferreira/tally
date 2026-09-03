---
id: 01M1M9PRN9GC3S0HX8SJT8CY2F
title: The first end-to-end ledger test
kind: reference
context_for: [01M1M2WXTAH040CM7WK4GJTZ99, 01M1M2WS9DWCXM34B3KG63HB86]
created: 2026-09-03T18:50:08.937906596Z
updated: 2026-09-03T18:50:54.883392012Z
---

# The first end-to-end ledger test

The target the MVP is built toward. Nothing here compiles as of 2026-09-03;
this is the definition of done for
[[Tally Java MVP — development plan]], written down so the increments have
something concrete to aim at rather than each being judged on its own.

```java
Account cash    = Account.open(AccountKind.ASSET,   Currency.USD);
Account revenue = Account.open(AccountKind.REVENUE, Currency.USD);

Transaction sale = Transaction.of(
        cash.debit(Money.of(2500, Currency.USD)),
        revenue.credit(Money.of(2500, Currency.USD)));

ledger.post(sale);

assertThat(ledger.balanceOf(cash)).isEqualTo(Money.of(2500, Currency.USD));
assertThat(ledger.balanceOf(revenue)).isEqualTo(Money.of(2500, Currency.USD));
```

## What each line demands

| Line | Requires |
|---|---|
| `Account.open` | `AccountKind`, `AccountId`, currency fixed at opening |
| `cash.debit(...)` | `Direction`, `Posting`, account-gated minting (ADR 005) |
| `Transaction.of(...)` | ≥2 postings, per-currency `sum(debits) == sum(credits)` |
| `ledger.post(...)` | append-only journal, invariant 3 (accounts must exist) |
| `ledger.balanceOf(...)` | balances **derived** by folding postings, never stored |

## Why this shape

`Account.open` has no id argument: identity is minted, not supplied (ADR 003).

`cash.debit(...)` rather than `new Posting(cash, DEBIT, amount)` — a posting is
unconstructible except through its account, which is what makes "always
positive, always in the account's currency" structural rather than validated.

`balanceOf` returns `Money`, not a number: a balance without its currency is
the bug this whole domain exists to prevent.

The counter-tests matter as much as this one: an unbalanced `Transaction.of`
must be refused, a single-posting transaction must be refused, and
`ledger.post` of a transaction naming an unknown account must be refused.
