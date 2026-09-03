---
id: 01M1JZNSSQ238695ZFJ63XYVYZ
title: ADR 004 — Accounts are denominated in a single currency
kind: rfc
aliases: [004-single-currency-accounts]
originProject: tally
origin: /home/iago/workspace/tally/docs/adr/004-single-currency-accounts.md
created: 2026-09-03T06:35:37.143009162Z
updated: 2026-09-03T16:49:27.538070922Z
documentName: 004-single-currency-accounts
---

# ADR 004 — Accounts are denominated in a single currency

- **Status:** Accepted
- **Date:** 2026-08-28
- **Deciders:** Iago Ferreira

## Context

`AccountId` (ADR 003) gives accounts identity, and `AccountKind` gives them the
debit/credit sign rule. What remained was the `Account` type itself, and with it
the question that shapes every type downstream of it: **can one account hold
more than one currency?**

The answer determines the type of a balance. If an account is multi-currency,
"the balance of account X" is not answerable — it is a map from currency to
amount, and every read of it has to name a currency or handle a collection. If
an account is single-currency, a balance is one `Money` and the question is
total.

It also determines where invariant 5 — `sum(debits) == sum(credits)` per
currency — gets checked, and how early a currency error can be caught.

This is also the first domain type with identity rather than pure value
semantics, so the ownership consequences are recorded here too.

## Options considered

### A. Currency fixed at opening (chosen)

`Account { id, kind, currency, name }`. An entity holding both dollars and euros
holds two accounts.

- A balance is always a single `Money`. Folding postings into a balance needs no
  grouping step and no map allocation.
- A wrong-currency amount is rejected by `Account::balance_effect` and
  `Account::post`, well before it can reach a transaction. The per-currency half
  of invariant 5 becomes close to structural rather than a validation pass.
- This is what core banking ledgers actually do: a customer with three
  currencies has three accounts, and that is visible in every statement they
  have ever received.
- Costs: multi-currency entities need N accounts, and something above the ledger
  has to know how to group them for presentation. FX becomes explicitly two
  postings against two accounts plus a rate applied outside the domain, which is
  more honest but more verbose.

### B. Multi-currency account

`Account { id, kind, name }` with no currency; a balance is
`BTreeMap<Currency, Money>`.

- Flexible, and closer to how a naive API would model "an account".
- Rejected: every balance read becomes a collection, `Money`'s arithmetic stops
  being usable directly on balances, and the balancing rule has to group by
  currency at the transaction level with no type-level help. Worst of all, the
  invariant "this amount belongs in this account" becomes uncheckable at the
  posting boundary — the account accepts anything, so the error surfaces later
  and further from its cause.

### C. Currency on the posting only, account currency-agnostic

- Rejected as a strictly weaker version of B: it has the same consequences for
  balances, without even the map to make the multi-currency intent explicit.

## Decision

**An account's currency is fixed when it is opened and cannot change.**

1. **`Account::open` is the only constructor**, and it mints the identity. There
   is deliberately no constructor accepting an existing `AccountId`.
   Reconstructing an account from storage is a boundary concern and the boundary
   does not exist yet; adding the path now would mean designing it against an
   imagined database.
2. **Names are trimmed and must not be empty.** An unnamed row in a chart of
   accounts is an operational hazard during an incident, not a valid account.
   Failure is `AccountError::EmptyName`.
3. **No maximum name length.** A cap is a storage constraint, and inventing one
   here would put an arbitrary number in the domain.
4. **`Account::balance_effect` reports currency mismatch as
   `MoneyError::CurrencyMismatch`** rather than defining an account-specific
   variant. The alternative forces callers to handle two structurally identical
   errors. The cost is real and worth stating: the variant's fields are named
   `left`/`right`, which here mean "the account's currency" and "the amount's
   currency" — documented, but not enforced by the type. `PostingError`
   (ADR 005) does name its fields for their roles, because construction is a
   boundary where the two sides are genuinely not interchangeable.
5. **Construction errors are separate from arithmetic errors.** `AccountError`
   covers opening; operations on an existing account keep returning `MoneyError`.
   A caller doing arithmetic does not have to widen its error type to include
   naming rules.

## On ownership

`Account` is the first domain type that is not `Copy`, because of the `String`
name. Three consequences, recorded because they set the pattern for every
non-`Copy` domain type that follows:

- **`open` takes `&str`, not `impl Into<String>`.** Trimming forces an
  allocation regardless, so accepting an owned `String` would only let a caller
  hand over a buffer that gets copied out of anyway.
- **`name()` returns `&str`, not `String`.** The borrow is tied to `&self`, so
  the compiler guarantees the account outlives the name being read. Returning an
  owned `String` would allocate on every read — a cost that is invisible in a
  language with reference semantics and explicit here.
- **`Account` derives `PartialEq` over all fields**, but structural equality is
  not identity. Two accounts opened with the same kind, currency and name are
  different accounts. Identity is `Account::id`, and nothing may treat field
  equality as a substitute for it.

## Consequences

- A balance is a `Money`, and `Account::zero_balance` is the seed for folding
  postings into one. No balance type ever needs a currency argument.
- Invariant 5 is enforced per currency by construction for any transaction whose
  postings all come from `Account::post`, because each posting inherits its
  account's currency.
- Multi-currency support is not lost, it is relocated: it becomes a property of
  a *set* of accounts. Whatever groups them — a customer, a wallet — lives above
  the ledger and does not exist yet.
- FX cannot be expressed as a single account changing currency. It must be
  modelled as postings against separate accounts, with the rate applied outside
  the domain. That is the intended outcome: an exchange rate is a valuation
  opinion, and the ledger records facts.
- Changing this later is expensive. It is not a type-signature change; it is a
  change to what a balance *is*, and it would touch every consumer.

---

## Reaffirmed 2026-09-03 — Java rewrite ([[ADR 006 — Tally is rewritten in Java]])

**Unchanged.** This is a pure domain rule with no Rust in it: an account fixes
its currency at opening and rejects any amount in another currency.

It ports directly. `Account` becomes a final class or record holding a
`Currency`, and the currency check stays a fallible operation returning a typed
error rather than throwing on a caller mistake.

The only translation note: Rust returned `Result<T, E>` so the caller could not
ignore the failure. Java has no checked equivalent that is pleasant to use —
the choices are a checked exception, an unchecked one, or a hand-rolled
`Result`. That decision belongs to the error-handling ADR for the Java version
and is not yet made.