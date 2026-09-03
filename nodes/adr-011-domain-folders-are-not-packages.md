---
id: 01M1MGQWJ38YSA47Q925X3VRQ0
title: ADR 011 — Domain folders are not packages
kind: rfc
relates_to: [01M1JZNSSTPVS2RT39A4VBMH0M, 01M1M93MFQK4F0FXKR09ZCEJXT, 01M1JZNSRVD5ZJVVYG7CCR5GH4]
created: 2026-09-03T20:53:05.731927691Z
updated: 2026-09-03T20:55:09.576358368Z
---

# ADR 011 — Domain folders are not packages

**Status:** accepted, 2026-09-03
**Protects:** [[ADR 005 — Postings are minted through their account]] and the
sealing amendment on [[ADR 009 — Domain failures are exceptions, not Result]]

## Context

The domain reached eighteen files in one flat directory and stopped being
navigable. The obvious fix — subpackages — is not available, because two
invariants depend on the domain being a **single package**.

## The two constraints, both verified

**1. Package-private construction.** `Posting`'s constructor and `flip()` are
package-private so that only `Account` and `Transaction` can mint a posting.
Real subpackages would force both public and delete ADR 005 outright.

**2. The sealed hierarchy.** Outside a named module, a sealed type's permitted
subclasses must share its package. Tested rather than recalled — moving one
subclass into `tally.domain.failure` produces:

```
error: class DomainException in unnamed module cannot extend a sealed class
       in a different package
```

Note a correction to an earlier claim made in session: exceptions *can* be
grouped, provided `DomainException` moves **with** them. The constraint is
"same package as the sealed parent", not "stay in the root". A three-way real
package split was therefore possible — it was rejected on constraint 1, not
constraint 2.

## Decision

Group the sources into directories while keeping **one package**:

```
tally/domain/
  money/     Currency, Money
  account/   Account, AccountId, AccountKind, Direction, Posting
  ledger/    Transaction, TransactionId, Ledger
  failure/   DomainException + its 7 permitted subclasses
```

Every file declares `package tally.domain`. No imports cross the folders. The
compiled classes land flat in `tally/domain`, exactly as before. Gradle passes
`javac` an explicit file list rather than resolving through a sourcepath, so the
build never consults the directory structure.

`module-info.java` was considered and **rejected**: JPMS would lift constraint 2
but not constraint 1, so it would buy nothing here while adding real complexity
to the build and test configuration.

## The cost, and the trap

IDEs expect a file's directory to match its package and will report every file
here as mismatched. **IntelliJ offers to "fix" it** by moving files or rewriting
the `package` declaration.

**Refuse that offer.** Accepting turns the folders into real packages and
silently deletes both guarantees above — `Posting` becomes publicly
constructible, and the sealed hierarchy stops compiling or gets "fixed" by
unsealing it. This is the dangerous shape of the decision: the failure mode is
a one-click suggestion that looks like tidying, not a change a reviewer would
question.

Documented in `package-info.java` and `CLAUDE.md` so it is encountered at the
point of temptation rather than here.

Tools that resolve sources through a sourcepath rather than an explicit file
list may fail to find classes. Nothing in the current build does.

## Revisit when

The domain outgrows a single reviewable package. ADR 005's mitigation for
package privacy being weaker than Rust's module privacy is precisely *"keep
this package small enough to read in full"* — so the day that stops being true,
both this decision and ADR 005's enforcement need rethinking together, probably
by extracting a real module.

Implemented in commit `e0cdf5f`.

---

## REVERSED 2026-09-03, same day — commit `f6b9619`

**The folder grouping was tried and undone. The domain is one flat directory
again. Do not retry this.**

The technical claims in this ADR all held: it compiled, no imports crossed the
folders, the classes landed flat, and both invariants survived. What sank it was
the cost the ADR listed and underweighted — **every file has a directory that
disagrees with its package declaration, so IDEs flag every file, permanently.**

That is not an occasional annoyance. It is a warning on all eighteen files,
every time the project is opened, plus a standing one-click offer to "fix" it
that would silently delete `Posting`'s package-private gate and unseal
`DomainException`. Constant editor noise is a worse daily cost than a long file
list, and a dangerous suggestion that is always on screen will eventually be
accepted by someone.

**Lesson worth keeping:** "it builds and costs nothing on the classpath" is not
the same as "it costs nothing". The build was the wrong thing to measure — the
cost was entirely in the tools people actually read the code through, and it was
foreseeable enough that it was written into this very ADR as a known downside
before being tried anyway.

### What survives

The *reasons* the domain is flat, which were only articulated by attempting
this, are now documented in `package-info.java` and `CLAUDE.md`:

- `Posting`'s constructor and `flip()` are package-private, so only `Account`
  and `Transaction` can mint a posting.
- `DomainException` is `sealed`; outside a named module its permitted
  subclasses must share its package.
- `module-info.java` is **not** the way around this — JPMS lifts the sealing
  constraint but not the package-private one, so it buys nothing.

Flatness is also [[ADR 005 — Postings are minted through their account]]'s own
mitigation: the guarantee rests on the package staying small enough to read in
full. **When it stops being so, extract a real module — do not subdivide.**