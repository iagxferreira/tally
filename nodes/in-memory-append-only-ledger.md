---
id: 01M1M2WXTAH040CM7WK4GJTZ99
title: In-memory append-only Ledger
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T20:01:22.168870958Z
depends_on: [01M1M2WS9DWCXM34B3KG63HB86]
created: 2026-09-03T16:51:10.794681493Z
updated: 2026-09-03T23:11:41.238535770Z
---

Increment 9 of [[Tally Java MVP — development plan]]. The last domain piece of
the MVP.

An append-only journal of posted transactions. Balances are **derived** by
folding postings, never stored as independent truth (invariant 8).

This is also where invariant 3 finally gets enforced — every posting references
an account that exists. That one is referential rather than intrinsic, so it
needs the ledger as context; the other invariants were enforceable by
construction, this one cannot be.

Concurrency: the MVP ledger is **not thread-safe**, and the working agreement
requires that consistency guarantees be documented rather than assumed. Say so
in the Javadoc explicitly rather than leaving it for a reader to discover. The
concurrent-posting question is Phase 2 and must not be pre-solved here.

**Done when:** posting a transaction referencing an unknown account is
rejected, balances fold correctly across many transactions, and the
thread-safety posture is written down.</body>

---

## Open question surfaced 2026-09-03 — where do known accounts live?

Invariant 3 says every posting references an account that exists. That is the
one invariant which **cannot** be enforced by construction, because it needs
context no single posting has. Something must hold the set of known accounts,
and the choice is not obvious:

1. **The `Ledger` owns its accounts** — `ledger.open(kind, currency)` returns an
   `Account` already registered. Invariant 3 becomes trivially true for anything
   the ledger minted, which is attractive. But it couples account lifecycle to
   the journal, and in Phase 2 accounts and transactions are almost certainly
   different tables with different write patterns.
2. **The `Ledger` holds a registry it is given** — accounts are opened
   independently and registered. Keeps the two lifecycles separate, at the cost
   of a real "unknown account" failure path that has to be tested.
3. **The `Ledger` validates against `AccountId`s it has seen** — weakest, and
   effectively trusts the caller.

Option 2 is the honest MVP shape: it keeps the failure real rather than
defining it away, and invariant 3 is supposed to be enforced, not made vacuous.
Decide when the ledger is written, and record it.

Note the interaction with [[Decide how data-driven refusals fail]]: posting a
transaction that names an unknown account is a **data-driven** refusal, not a
caller defect, so it lands in that decision too.

---

## Done 2026-09-03 — commits `7945fa2`, `bd16f3f`. 155 tests. **Phase 1 complete.**

### The open question, answered: accounts are registered, not owned

Option 2 from the list above. A ledger that minted its own accounts would make
invariant 3 **vacuously true** for everything it created, which is not the same
as enforcing it. Keeping the lifecycles separate also matches Phase 2, where
accounts and transactions are different tables with different write patterns.

`register(account)` is idempotent — an account is identified by its id, so
re-registering is not a state change and refusing it would make replaying a set
of registrations needlessly fragile.

### Three referential checks, all of which need the ledger's context

1. **Unknown account** (invariant 3) — the only invariant that cannot be
   enforced by construction, because a `Posting` carries an `AccountId` and
   nothing about it in isolation knows whether that account is real.
2. **Duplicate transaction** — posting the same transaction twice would double
   every amount in it. **This is not idempotency**: that is a Phase 4 concern
   needing a caller-supplied key. This is the narrower structural fact that a
   journal must not contain an entry twice.
3. **Reversal of an unposted transaction** — a correction must correct
   something. Otherwise a correcting entry moves balances for an error the
   journal has no record of.

### A bug I wrote and caught before committing

The reversal check first threw `UnknownAccountException`, naming an arbitrary
account taken from the reversal's first posting, and its Javadoc referenced an
exception type that did not exist. Both wrong. `UnknownTransactionException`
now carries the identifier that is actually missing. Worth remembering: reusing
a nearby exception type because it is *there* produces failures that mislead
whoever reads them at 3am.

### Balances are derived, and that is O(journal)

No balance field anywhere. Storing one creates a second source of truth that can
disagree with the journal, and when they disagree nothing can say which is
wrong. This is obviously not how it works at scale — the fix is a **measured,
explicitly invalidated projection**, not a hand-maintained counter. Principle 8:
optimisation requires a measurement, and there is none yet.

### Thread safety

**Not thread-safe**, stated in the Javadoc and the README rather than defended.
Two threads posting concurrently corrupt the journal; a balance read during a
post may observe a partially applied transaction. Concurrent posting is a Phase
2 question that needs a real storage model to answer.

### The seal fired a fourth time

Three new failures broke the exhaustive handler again. Four rounds, four
compile-time catches, zero silent fall-throughs. `DomainException` now permits
seven subclasses and a test pins that count.

### Target vs. reality

[[The first end-to-end ledger test]] predicted the API almost exactly. Two
differences, both deliberate: the clock is injected rather than ambient, and
accounts must be registered with the ledger before use — which is the invariant
3 decision showing up in the call site.

## Review finding, 2026-09-03

`Ledger.register` currently overwrites `accounts` by `AccountId` without checking the existing account definition. `Account.reopen` can therefore supply the same ID with a different kind or currency after postings exist. Subsequent `balanceOf` then either derives historic postings using a different sign rule or starts its fold at a zero in the replacement currency and throws `CurrencyMismatchException`. Account registration must be immutable per ID, or reject conflicting definitions; the behavior needs a typed failure decision and regression tests.