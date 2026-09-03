---
id: 01M1M2WBG09YBT67Z0DBK0F982
title: Port Direction, AccountId and AccountKind to Java
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T18:57:13.320176102Z
depends_on: [01M1M2VN1EK18WV2Q8Y6GF1Z6N]
created: 2026-09-03T16:50:52.032601725Z
updated: 2026-09-03T18:57:13.322264197Z
---

Increments 4-6 of [[Tally Java MVP — development plan]]. Carries
[[ADR 003 — Account identity]].

`Direction` — `DEBIT` / `CREDIT`. Direction is never a sign on the amount.

`AccountId` — a record wrapping a UUIDv7. **Decide the minting strategy first:**
`UUID.randomUUID()` is v4 only and the JDK has no v7 factory as of Java 25, so
this is either the JUG library (`com.fasterxml.uuid:java-uuid-generator`) or a
hand-rolled constructor packing `System.currentTimeMillis()` into the top 48
bits with correct version/variant nibbles and `SecureRandom` for the rest.
Hand-rolling is ~20 lines and worth doing once for the understanding; a bug in
it silently destroys index locality rather than failing loudly, so it needs
tests asserting monotonicity across mints and the version nibble.

`AccountKind` — the five kinds, with the debit/credit sign rule **derived from
the accounting equation**, not written out as a truth table. That derivation is
the point; a lookup table would pass the same tests and teach nothing.

**Done when:** sign rule tested against all five kinds in both directions, and
UUIDv7 monotonicity is covered.</body>

---

## Done 2026-09-03 — commits `ac0d8f9`, `4cbf814`, `1fcec91`

Three atomic commits: `Direction`, `AccountKind`, `AccountId`. 53 tests passing.

### UUIDv7: JUG, not hand-rolled

The maintainer confirmed v7. Used `com.fasterxml.uuid:java-uuid-generator`
(already declared in the build) via
`Generators.timeBasedEpochGenerator()`, held as one shared static generator —
JUG documents its generators as thread-safe, and this avoids re-seeding a
`SecureRandom` per mint.

Justified inside the domain on the same grounds the Rust `Cargo.toml` used for
the `uuid` crate: a value-type library brings no I/O, no runtime and no
framework, so it does not breach the no-infrastructure rule. Swapping to a
hand-rolled ~20-line generator later is contained to `AccountId.mint()`.

### Two details worth keeping

**Construction rejects any non-v7 UUID.** This matters more than it looks: a v4
would still *work*, and nothing downstream would ever complain — index locality
would simply degrade, quietly, forever. That is precisely the class of fault
worth making unconstructible rather than documenting.

**`UUID.compareTo` compares the high bits as a *signed* long**, which is wrong
for UUIDs in general. It is correct for v7 because the top bit belongs to the
48-bit millisecond timestamp and stays zero until past the year 10000. Worth
knowing before anyone "fixes" it with an unsigned comparison.

### AccountKind derivation

The sign rule is computed from the accounting equation
(`assets + expenses = liabilities + equity + revenue`) rather than tabulated:
each kind stores only which side it sits on. A lookup table would pass every
per-kind assertion — only the equation-level tests would catch it drifting, so
those are the ones that matter.

`effectOf(direction)` returns the `+1`/`-1` that `Ledger.balanceOf` will
multiply each posting's amount by.