---
id: 01M1M9QGM9N8TN1WNDQEAZ6M0Z
title: Decide Transaction identity and timestamp
kind: note
assignee: claude-code
status: todo
created: 2026-09-03T18:50:33.481560599Z
updated: 2026-09-03T18:50:33.481560599Z
---

Blocks [[Transaction — enforce the double-entry balance invariant]].

The journal is append-only and ordered, so a transaction almost certainly needs
an identity and a time. Neither is as simple as it looks, and picking a default
by accident would bury a distributed-systems decision inside a constructor.

## Identity

Probably `TransactionId` as UUIDv7, for the same reasons as
[[ADR 003 — Account identity]]: mintable without coordination, sorts near
time order in an index. Worth confirming rather than assuming — it also decides
whether identity is minted inside `Transaction` or supplied by the caller, and
**supplied** is what an idempotency key would later need (Phase 4).

## Timestamp — the real question

Who assigns it, and what does it mean?

1. **The domain, at construction** (`Instant.now()`). Simple, and untestable
   without a clock abstraction. Also wrong in spirit: the domain would be
   reading a global mutable value, which is the sort of ambient dependency the
   layering rules exist to keep out.
2. **The caller passes it in.** Explicit and testable. Pushes the question up
   to whoever is better placed to answer it.
3. **A `Clock` injected into the domain.** Java's `java.time.Clock` exists
   exactly for this and `Clock.fixed(...)` makes tests deterministic. Costs a
   dependency on every construction site.

There is also a **semantic** question the mechanism cannot answer: is the
timestamp when the economic event happened, or when the ledger recorded it?
These diverge — a payment made Friday and imported Monday has two legitimate
times. Real ledgers usually carry both (value date vs. booking date), and
conflating them is a reconciliation bug waiting to happen.

The MVP may reasonably carry only one. It should carry it **deliberately**, and
this node should record which one it is.

**Done when:** identity and timestamp are decided, recorded as an ADR node, and
the value-date/booking-date distinction is either implemented or explicitly
deferred with a reason.