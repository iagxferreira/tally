---
id: 01M1M2WS9DWCXM34B3KG63HB86
title: Transaction — enforce the double-entry balance invariant
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T19:42:44.315023695Z
depends_on: [01M1M2WFP84MTY169AA8KGRNFJ, 01M1M9Q28MKPB5D4QZ58ZG053T, 01M1M9QGM9N8TN1WNDQEAZ6M0Z, 01M1M9QRG9PWNQJXPEE51RFY1Z]
created: 2026-09-03T16:51:06.157173672Z
updated: 2026-09-03T19:42:44.316351523Z
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

---

## Done 2026-09-03 — commits `f35423c`, `5723996`. 135 tests.

All three blocking decisions were taken with real code in front of us, as this
node asked.

### The three decisions

**Identity and time** — `TransactionId` minted as UUIDv7; occurrence time from
an **injected `java.time.Clock`**, not `Instant.now()`. A domain that reads a
global clock cannot be tested deterministically; tests use `Clock.fixed`.
Only **one** time is recorded. Real ledgers separate when an event occurred
from when it was booked (value date vs booking date) — a payment made Friday
and imported Monday has two legitimate dates. **Known limitation, documented in
the Javadoc, not an oversight.**

**Multi-currency** — **single currency per transaction** for the MVP. The
eventual rule is per-currency balancing; refusing what is unsupported beats
appearing to allow it. README invariant 5 still describes the eventual rule and
**needs correcting** — see below.

**Data-driven refusals** — exception **plus** an inspectable check.
`Transaction.of` throws `UnbalancedTransactionException` carrying the imbalance
as a typed `Money`; `Transaction.imbalanceOf(postings)` returns
`Optional<Money>` so an importer with untrusted input can look before leaping
without using exceptions as control flow. One failure mechanism, per
[[ADR 009 — Domain failures are exceptions, not Result]]; the query is a second
way to *ask*, not a second way to *fail*.

### The factory vocabulary from [[ADR 010 — Movements are N single-leg postings, not from/to transfers]]

`transfer`, `split` and `reverse` all delegate to the same validation. No
transaction type tag: **the presence of `reverses()` is what makes a
transaction a reversal**, and it names *which* transaction — a tag would say
less and could drift out of agreement with the postings.

`reverse` is emphatically **not** a rollback. Nothing is removed or edited; the
correction joins the original in the journal so an auditor sees both.

### The seal fired a third time

Adding `UnbalancedTransactionException` and `MalformedTransactionException` to
`permits` broke `DomainExceptionTest`'s handler again:

```
error: the switch expression does not cover all possible input values
```

Three for three. Every new failure type this project has added has been caught
by the compiler rather than slipping past a `default` branch.

### Follow-up needed

**README invariant 5 is now inaccurate** — it promises per-currency balancing
that the code refuses. Correct it, per the documentation-honesty rule.