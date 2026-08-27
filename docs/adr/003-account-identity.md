# ADR 003 — Account identity

- **Status:** Accepted
- **Date:** 2026-08-26
- **Deciders:** Iago Ferreira

## Context

Accounts need identity before postings can reference them. The choice
determines what uniqueness costs, how the journal behaves physically once it
reaches a database in Phase 2, and how Tally integrates with systems that
already number their accounts.

It also introduces the domain's first dependency, so it is worth stating why
that is acceptable.

## Options considered

### A. Generated UUIDv7 (chosen)

A 48-bit Unix millisecond timestamp in the high bits, the remainder random.

- Unique without coordination: any node can mint an identifier without
  consulting a central sequence, which keeps the option of sharding or
  multi-writer open.
- Sorts by creation time, because UUIDs are laid out big-endian. Inserts land
  at the right edge of a B-tree instead of scattering, so index pages stay hot
  and the journal reads back in roughly insertion order.
- Costs a dependency and 16 bytes, and means Tally mints identity rather than
  adopting an upstream system's numbering.

### B. Caller-supplied opaque string

- No dependency, and it matches how ledgers integrate in practice: accounts
  usually already exist in a core banking system with their own numbering.
- Readable in logs — `customer:1234:GBP` beats a UUID during an incident.
- Rejected for now: uniqueness enforcement moves entirely to storage, the type
  stops being `Copy` so every posting owns a heap allocation, and it
  immediately raises validation questions (length, charset, emptiness) that we
  would be answering without a real upstream system to answer them for.

### C. Both — internal UUID plus external reference

Where production ledgers usually converge: a stable internal identity, plus the
upstream system's reference kept alongside for operators and reconciliation.

- Rejected *for now*, not rejected. It is the right end state, but there is no
  external system to reference yet, so it would cost a lookup on every ingress,
  a second uniqueness constraint, and roughly double the code for a benefit
  that cannot yet materialise.

### D. Sequential integers

- Best possible index locality and the smallest key.
- Rejected: requires a coordinating sequence, which makes identity a
  distributed-systems problem the moment there is more than one writer. It also
  leaks business information — account `#1043` tells an observer how many
  accounts exist.

## Decision

**`AccountId` is a newtype over a generated UUIDv7.**

1. **Version 7 rather than 4.** Both give the same coordination-free
   uniqueness. v4's 122 random bits scatter index inserts uniformly, making the
   index working set the entire index; v7's timestamp prefix keeps inserts
   local. The choice costs nothing now and is expensive to reverse once there
   is data.
2. **A newtype, not a bare `Uuid`.** `AccountId` and a future `TransactionId`
   are distinct types, so they cannot be transposed at a call site. With
   `#[repr(transparent)]` the wrapper has the layout of the `Uuid` it contains,
   so the safety is purely a compile-time property.
3. **No `Default`.** A "default account" is meaningless, and `Uuid::nil()`
   would silently collide across every caller that forgot to set one.
4. **`from_uuid` does not validate the version.** An identifier persisted under
   an earlier scheme is still that account's identity; rejecting it would lose
   data rather than protect an invariant.
5. **No `FromStr` yet.** Parsing would put `uuid::Error` in the domain's public
   API, and that is a boundary decision better made when there is an actual
   parsing boundary — an HTTP path parameter or a database column — to make it
   against.

## On the dependency

This is the first dependency in the domain, so the rule it is tested against is
principle 6: the domain must not depend on HTTP, PostgreSQL, Kafka, or
frameworks.

`uuid` is a value-type library. It performs no I/O, starts no runtime, and
imposes no framework. It does pull `getrandom` and `libc` transitively for
entropy, which is a real cost and worth knowing about. The alternative — hand
rolling a v7 generator — would mean writing our own entropy handling, which is
strictly worse.

The rule is about infrastructure coupling, not about dependency count, and this
does not breach it.

## Consequences

- Identifiers can be minted anywhere without coordination.
- Phase 2 gets index locality for free, and Phase 6 reconciliation will need
  the external reference from option C — expected to be additive rather than a
  migration, since nothing outside the domain touches the inner `Uuid`.
- Ordering `AccountId`s approximates ordering by creation time. This is a
  convenience only: identifiers minted in the same millisecond order by their
  random bits, and clocks move backwards. It must never be used as a substitute
  for a real timestamp, and no invariant may depend on it.
