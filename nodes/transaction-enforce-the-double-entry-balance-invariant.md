---
id: 01M1M2WS9DWCXM34B3KG63HB86
title: Transaction — enforce the double-entry balance invariant
kind: note
assignee: claude-code
status: todo
depends_on: [01M1M2WFP84MTY169AA8KGRNFJ, 01M1M9Q28MKPB5D4QZ58ZG053T, 01M1M9QGM9N8TN1WNDQEAZ6M0Z, 01M1M9QRG9PWNQJXPEE51RFY1Z]
created: 2026-09-03T16:51:06.157173672Z
updated: 2026-09-03T18:50:51.665784886Z
---

Increment 8 of [[Tally Java MVP — development plan]]. **The first genuinely new
domain type** — the Rust version never reached it.

This is where financial invariants 2 and 5 live:

- a transaction contains at least two postings;
- for each currency within the transaction, `sum(debits) == sum(credits)`.

Multi-currency transactions balance **per currency**. There is no implicit
exchange rate and there must be no way to express one.

Design questions to work through rather than assume:

- Is balance checked in a fallible factory, or made unrepresentable by the type?
  A factory is the honest MVP answer; "unrepresentable" is hard here because
  balance is a property of a *set* of postings.
- Does the per-currency sum use `Money`'s overflow-checked arithmetic, or a
  widened accumulator? Summing many `long` minor units can overflow where each
  posting individually cannot.
- `Transaction` must defensively copy the posting list. Rust's ownership made
  this free; Java does not, and a caller retaining the list would break
  immutability (invariant 6).

**Done when:** property tests generate balanced and unbalanced multi-currency
sets and the invariant holds for every one.</body>
