---
id: 01M1M2X7255SAHA4Z4BEVBWKD2
title: ArchUnit and Error Prone guard rules
kind: note
assignee: claude-code
status: dropped
depends_on: [01M1M2VN1EK18WV2Q8Y6GF1Z6N]
created: 2026-09-03T16:51:20.261094189Z
updated: 2026-09-03T19:11:18.762285613Z
---

Increment 10 of [[Tally Java MVP — development plan]]. This is what replaces the
Cargo lint policy lost in [[ADR 006 — Tally is rewritten in Java]].

Three rules, each standing in for a guarantee Rust gave for free:

1. **No floating point in the domain** — no class in `tally.domain` may
   reference `float` or `double`. Replaces `float_arithmetic = "deny"`.
2. **Domain independence** — `tally.domain` imports nothing from `tally.core`
   and nothing from any infrastructure package. Replaces the convention ADR 002
   guarded by hand.
3. **Posting construction** — `Posting`'s constructor is called only from
   `Account`. Replaces Rust module privacy; see
   [[ADR 005 — Postings are minted through their account]].

Error Prone is wired in at compile time for what it can catch earlier.

These are test-time, not compile-time. That is a real downgrade and the tests
are therefore load-bearing: if someone marks them `@Disabled` the invariants
quietly stop being enforced. Note that in the test class itself.

**Done when:** each rule is proven to fail by temporarily violating it.</body>

---

## Dropped 2026-09-03 — commit `c620ea3`

The maintainer questioned whether ArchUnit was needed at all. Checking the three
planned rules against what actually exists settled it — **two protect nothing:**

| Rule | Status |
|---|---|
| `tally.domain` must not depend on `tally.core` | Vacuous — `tally.core` is empty, nothing to import |
| `Posting`'s constructor called only from `Account` | Vacuous — `Posting` does not exist |
| No `float`/`double` in the domain | Real, but one rule over a five-class domain |

Principle 9 — complexity must be earned — applies to **test tooling** as much as
to architecture. I carried these forward from the Rust lint policy without
re-checking whether they had anything to guard, which is exactly the reflex the
principle exists to catch.

ArchUnit was removed from `build.gradle.kts`. Error Prone **stays**: it is
already wired into compilation and doing real work.

### What this gives up, plainly

Invariant 1 (no floating point) was a compile error in Rust and is now a review
responsibility. That gap is real and accepted, because the domain is currently
small enough to read in full. It is documented in both README and CLAUDE.md
rather than left for someone to discover.

Note that reflection alone would not have closed it either: it sees fields,
parameters and return types but **not inside method bodies**, and neither
approach reliably catches intermediate `double` arithmetic that is rounded
before it is returned.

### Framing worth remembering

"ArchUnit vs. unit tests" is a false opposition — ArchUnit rules *are* JUnit
tests, running in the same suite and failing the same build. The real question
is whether the library earns its place over plain assertions. Here it did not,
yet.

### Revisit when

`tally.core` has contents, `Posting` exists, **or** a floating-point near-miss
actually happens in review. Any of the three makes the rules non-vacuous.