---
id: 01M1M9QRG9PWNQJXPEE51RFY1Z
title: Decide multi-currency scope for the MVP
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T19:42:21.520079436Z
created: 2026-09-03T18:50:41.545377530Z
updated: 2026-09-03T19:42:21.521073439Z
---

Blocks [[Transaction — enforce the double-entry balance invariant]].

The README's invariant 5 says: for each currency within a transaction,
`sum(debits) == sum(credits)`. Multi-currency transactions balance **per
currency**; there is no implicit exchange rate.

That wording commits to multi-currency transactions being *representable*. The
MVP has to decide whether they are *supported yet*.

## Options

**A. Support per-currency balancing now.** Honours the stated invariant
exactly. `Transaction` groups postings by currency and checks each group. Not
much harder than the single-currency version — a `Map<Currency, Money>` fold
rather than one accumulator.

**B. Restrict the MVP to single-currency transactions.** Simpler, and refuses
what it cannot yet do honestly. But it contradicts a documented invariant, so
the README must be corrected rather than left aspirational — the documentation
honesty rule applies.

## Why this is not merely a scope question

A genuinely multi-currency transaction (say, an FX trade: debit USD, credit
EUR) balances per currency **only if each side is separately balanced**, which
an FX trade is not — that is what the exchange-rate leg is for. So option A
does not actually make FX expressible; it makes *unrelated* movements shareable
within one transaction. Whether that is desirable is a modelling question, not
a coding one.

Worth deciding what a multi-currency transaction is even *for* before
supporting it. If the answer is "nothing the MVP needs", option B is the honest
choice and the invariant should be reworded to match.

**Done when:** the choice is recorded, and either the code supports per-currency
balancing or the README's invariant 5 is corrected to match reality.