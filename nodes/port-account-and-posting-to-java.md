---
id: 01M1M2WFP84MTY169AA8KGRNFJ
title: Port Account and Posting to Java
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T19:20:43.254811898Z
depends_on: [01M1M2W2B9QCJ45WAYBHKFACEJ, 01M1M2WBG09YBT67Z0DBK0F982]
created: 2026-09-03T16:50:56.328597439Z
updated: 2026-09-03T19:20:43.256880491Z
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

---

## Done 2026-09-03 — commits `0dc5e87`, `51e4b95`

65 tests passing. Each commit built in isolation before landing.

### A record cannot do what ADR 005 requires

`Posting` was written as a `record` with a package-private canonical
constructor. It does not compile:

```
error: invalid canonical constructor in record Posting
    (attempting to assign stronger access privileges; was public)
```

**A record's canonical constructor must be at least as accessible as the record
itself.** Records are transparent carriers of their state, so the language
refuses to let one be publicly readable but not publicly constructible — which
is precisely what account-gated minting asks for. Rust had no such tension: a
`pub struct` with private fields and a `pub(super)` constructor is ordinary.

The rule won. `Posting` is a `final class` with hand-written `equals`,
`hashCode` and `toString`. **Do not "improve" it back into a record.**

### The seal proved itself the same afternoon

Adding `NonPositiveAmountException` to `DomainException`'s `permits` clause
broke a handler written hours earlier:

```
error: the switch expression does not cover all possible input values
```

That handler would have silently ignored the new failure under an open
hierarchy. This is the concrete payoff of
[[ADR 009 — Domain failures are exceptions, not Result]]'s sealing amendment,
observed rather than argued.

### Entity vs value

`Account` is **not** a record either, for a different reason: it is an
**entity**. Two accounts are the same account when they share an `AccountId`,
regardless of kind or currency. A record generates component-wise equality —
the value-object answer — which would make identity meaningless. This matters
once accounts are persisted: an account rehydrated from a row must equal the
one in memory.

`Money` stays a record because it genuinely is a value.

### Decisions taken while building

- **`Posting` holds `AccountId`, not `Account`.** Postings are journal entries
  that outlive any in-memory object graph. It also keeps invariant 3 a real
  check rather than a tautology — the ledger must confirm the account exists.
- **`open()` vs `reopen()`** kept as separate factories, so reconstituting an
  account by accident is hard.
- **Non-positive amount raises an exception**, consistent with
  [[ADR 009 — Domain failures are exceptions, not Result]]. A caller holding a
  `Money` can check `isPositive()` before minting, so this remains a caller
  defect. **This does not pre-decide the unbalanced-transaction case** — see
  [[Decide how data-driven refusals fail]], which is sharper because an
  unbalanced set of postings has no equivalent cheap pre-check.