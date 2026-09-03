---
id: 01M1JZNSSTPVS2RT39A4VBMH0M
title: ADR 005 — Postings are minted through their account
kind: rfc
aliases: [005-posting-construction]
originProject: tally
origin: /home/iago/workspace/tally/docs/adr/005-posting-construction.md
created: 2026-09-03T06:35:37.146486340Z
updated: 2026-09-03T16:49:38.713642179Z
documentName: 005-posting-construction
---

# ADR 005 — Postings are minted through their account

- **Status:** Accepted
- **Date:** 2026-08-28
- **Deciders:** Iago Ferreira

## Context

A posting is one leg of a transaction: an account, a direction, and an amount.
Two invariants are intrinsic to it — invariant 4, that the amount is strictly
positive, and, given ADR 004, that the amount is in the account's currency.

Invariant 4 is not decoration. Direction is carried by `Direction`, never by the
sign of the number. If amounts could be negative there would be two spellings of
every movement — a debit of `-50` and a credit of `50` — and
`sum(debits) == sum(credits)` would stop discriminating between a balanced
transaction and a nonsensical one. Zero is rejected for the same reason: a zero
posting records no economic fact, but it would let a transaction satisfy the
balancing rule vacuously.

The currency invariant is the one that forces a design decision, because
checking it requires the account, and a posting does not hold an account.

## Options considered

### A. Account-gated constructor (chosen)

`Account::post(&self, direction, amount) -> Result<Posting, PostingError>`. You
must hold the account to mint a posting against it.

- The currency check cannot be skipped, because there is no construction path
  that lacks an account. A posting whose currency differs from its account's is
  not merely invalid, it is unconstructible.
- The error surfaces at the point of the mistake rather than at transaction
  validation, where the offending posting would be one of several and the cause
  further away.
- Costs: Phase 2 rehydration from storage will need a path that does not have a
  live `Account` in hand. That path will have to be justified when it arrives,
  which is the point — it forces the question to be asked rather than assumed.

### B. Free-standing constructor

`Posting::new(account_id, direction, amount)`, checking positivity only, with
currency correctness left to the ledger.

- Keeps `Posting` a pure value type with a single obvious construction path, and
  the ledger has to resolve the id to an account anyway in order to check the
  referential invariant.
- Rejected: it makes a wrong-currency posting representable, and "representable
  but always rejected later" is exactly the state principle 5 exists to avoid.
  The currency check would also end up co-located with the *referential* check,
  which is a different kind of invariant with a different owner.

### C. Both constructors

- Rejected: two paths means the weaker one is the one people reach for, and it
  would build the rehydration path before there is any storage to rehydrate
  from.

## Decision

**`Account::post` is the only public way to construct a `Posting`.**

1. **The validating constructor is `pub(super)` in the posting module.**
   `Posting::new(&Account, direction, amount)` is reachable from
   `domain::account` and nowhere outside the crate. This keeps the invariant
   check next to the type it protects, while the only public entry point is on
   `Account`. Rust's `pub(super)` is doing real work here that a directory-based
   package-private visibility could not: it names a position in the module tree.
2. **`Posting` stores an `AccountId`, not an `Account` and not a borrow.** A
   journal entry must stay readable long after the `Account` value that produced
   it is dropped. Holding `&'a Account` would give `Posting` a lifetime
   parameter, and every type containing one — `Transaction`, the journal — would
   inherit it. Copying 16 bytes of identifier out of the borrow avoids that
   entirely, and keeps `Posting` a `Copy` value.
3. **Currency is checked before positivity.** An amount in the wrong currency is
   a category error; reporting it first is more useful than reporting that a
   number of the wrong kind is also not positive.
4. **`PostingError` variants carry the rejected values**, and its
   `CurrencyMismatch` names its fields `account` and `amount` rather than
   `left`/`right`. Unlike `MoneyError`'s symmetric arithmetic operands, these
   two sides are not interchangeable — one is fixed by the account, the other is
   what the caller offered.
5. **No `Display` for `Posting` yet.** Journal rendering is a real requirement
   but not yet an actual one, and the format should be decided against a real
   consumer.

## Consequences

- Any posting in the system carries an amount that is strictly positive and
  denominated in its account's currency. Nothing downstream needs to re-check
  either.
- Combined with ADR 004, the per-currency half of invariant 5 is close to
  structural: every posting inherits its account's currency, so a transaction's
  currency grouping follows from its accounts.
- `Transaction` can be built over `Posting` values without lifetimes,
  allocation-free per posting, and without borrowing the chart of accounts.
- Invariant 3 — the referenced account exists — is untouched by this and remains
  the ledger's responsibility. `Posting` holds an identifier, and an identifier
  is not proof of existence.
- Phase 2 will need a construction path from persisted rows. It will be added
  deliberately, with its own record, rather than inherited by accident from a
  constructor built for convenience now.

---

## Amended 2026-09-03 — Java rewrite ([[ADR 006 — Tally is rewritten in Java]])

The **rule stands**: a `Posting` is minted only through `Account.post`, so it is
always strictly positive and always in its account's currency. There is no
public constructor a caller can reach to build an unbalanced or mis-denominated
leg.

The **mechanism weakens, and this is the sharpest loss in the port.**

Rust enforced it with module privacy: the constructor was visible only to
`account`, and no sibling module — not even elsewhere in the same crate — could
call it. The compiler made an invalid `Posting` unconstructible.

Java's nearest equivalent is a package-private constructor on a final
`Posting`, with `Account` in the same package. That stops *other packages*, but
anything added to `tally.domain` later can call it directly. The guarantee
drops from "the compiler forbids it" to "nothing in this package does it, and
review must keep it that way."

Mitigations available, none as strong as the Rust original:

- Keep `tally.domain` small enough that the whole package is reviewable.
- An ArchUnit rule asserting `Posting`'s constructor is called only from
  `Account`.
- A sealed interface with `Account` as the only permitted minting path.

Worth stating rather than hiding: principle 5 — invalid financial states should
be impossible to represent — is enforced one notch less strongly in Java here.