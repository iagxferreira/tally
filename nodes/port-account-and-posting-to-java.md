---
id: 01M1M2WFP84MTY169AA8KGRNFJ
title: Port Account and Posting to Java
kind: note
assignee: claude-code
status: todo
depends_on: [01M1M2W2B9QCJ45WAYBHKFACEJ, 01M1M2WBG09YBT67Z0DBK0F982]
created: 2026-09-03T16:50:56.328597439Z
updated: 2026-09-03T16:53:54.057908241Z
---

Increment 7 of [[Tally Java MVP — development plan]]. Carries
[[ADR 004 — Accounts are denominated in a single currency]] and
[[ADR 005 — Postings are minted through their account]].

`Account` — identity, kind, and a currency fixed at opening. Applies the sign
rule; rejects amounts in any other currency.

`Posting` — one leg of a movement, minted **only** through `Account.post`, so it
is always strictly positive and always in its account's currency.

The mechanism is the interesting part and it is weaker than the Rust original.
Rust used module privacy, which a sibling module could not reach. Java gets a
package-private constructor on a final `Posting` with `Account` in the same
package — which stops other packages but not future additions to
`tally.domain`. Add an ArchUnit rule asserting the constructor is called only
from `Account`, and keep the package small enough to review.

**Done when:** it is impossible to construct a `Posting` from `tally.core`, and
tests prove negative amounts and foreign currencies are both rejected.</body>
