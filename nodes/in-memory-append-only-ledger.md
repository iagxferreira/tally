---
id: 01M1M2WXTAH040CM7WK4GJTZ99
title: In-memory append-only Ledger
kind: note
assignee: claude-code
status: todo
depends_on: [01M1M2WS9DWCXM34B3KG63HB86]
created: 2026-09-03T16:51:10.794681493Z
updated: 2026-09-03T18:51:06.541549889Z
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