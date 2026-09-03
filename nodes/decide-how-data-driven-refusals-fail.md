---
id: 01M1M9Q28MKPB5D4QZ58ZG053T
title: Decide how data-driven refusals fail
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T19:42:23.029393016Z
created: 2026-09-03T18:50:18.772841869Z
updated: 2026-09-03T19:42:23.030387987Z
---

The question [[ADR 009 — Domain failures are exceptions, not Result]]
deliberately left open, now reaching its decision point.

ADR 009 made domain failures unchecked exceptions, on the reasoning that a
well-written caller never adds dollars to yen — so a currency mismatch is a
**caller defect**, and "fail loudly at runtime" is the right answer for it.

`Transaction` is the first case where that reasoning **does not obviously
apply.** An unbalanced transaction is not a programmer error. It arrives from
legitimate input: a user, an import file, an API request. A caller may need to
catch it, report *which currency* failed to balance and by how much, and carry
on processing the rest of a batch.

That is precisely the profile ADR 009 named as unsettled:

> This reasoning does not automatically extend to data-driven refusals. [...]
> Those are decided when `Transaction` and `Posting` are built, not pre-judged
> here.

## The cases to decide together

- `Transaction.of(...)` — unbalanced, or fewer than two postings
- `Account.post(...)` — a non-positive amount
- `Ledger.post(...)` — a posting naming an unknown account (invariant 3)

## The tension

Reintroducing `Result` for these would re-open a decision the maintainer just
made, and would leave the domain with **two** failure mechanisms — the thing
ADR 009 simplified away. But making an unbalanced transaction an unchecked
exception means a batch importer discovers bad input by catching, which is the
weaker shape.

A third option exists and should be considered on its merits: keep exceptions,
and make the *validation* separately callable, so a caller who wants to inspect
before committing can, while the constructor still refuses to build an invalid
transaction.

**Do not decide this in the abstract.** Write `Transaction` far enough to see
the real call sites first, then choose.

**Done when:** the decision is recorded as an ADR node and the three cases above
fail consistently with it.