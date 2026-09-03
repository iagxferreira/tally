---
id: 01M1M2XDHN6EM795RGY1K5JZS2
title: Rewrite CLAUDE.md and README for the Java project
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T19:08:24.675254561Z
depends_on: [01M1M2VN1EK18WV2Q8Y6GF1Z6N]
created: 2026-09-03T16:51:26.901916139Z
updated: 2026-09-03T19:08:24.676771839Z
---

Increment 11 of [[Tally Java MVP — development plan]].

**`CLAUDE.md`** — the working agreement is Rust-specific throughout and must be
rewritten for Java/JVM. What changes: pairing mode teaches Java and the JVM
(memory model, immutability, sealed types, why `Result` vs exceptions is a real
choice) with comparisons to Rust/Kotlin/Go where they help; the layout section
describes Gradle and packages; verification becomes `./gradlew build` in place
of the fmt/clippy/test triple; the Rust lint policy section is replaced by the
ArchUnit + Error Prone rules.

What **does not** change: the 8-step cycle, the eleven engineering principles,
the ban on `String`-based domain errors, commit conventions, and documentation
honesty. Those were never about Rust.

**`README.md`** — the Implemented / Experimental / Planned table currently
describes a Rust crate that no longer exists. Rewrite it honestly: after the
skeleton commit, *nothing* is implemented, and saying so is the point of the
documentation-honesty rule.

Also write [[ADR 006 — Tally is rewritten in Java]] into `docs/adr/006-*.md` so
the repo and the vault agree.

**Done when:** no document in the repo describes Tally as a Rust project.</body>

---

## Done 2026-09-03 — commit `3828269`

Both files described a Rust crate that no longer exists, with links into the
removed `docs/adr/` directory. They were the only documentation a reader of the
repository has, and they were wrong.

### README

States honestly what exists (money, currency, direction, account kind, account
identity, 53 tests) and that **`Account`, `Posting`, `Transaction` and `Ledger`
do not exist**, so Tally cannot currently record a transaction. The invariants
section now marks which of the eight are actually enforced today — one — rather
than listing all eight as if they were.

### CLAUDE.md

Kept what was never about Rust: the eight-step cycle, the eleven principles,
atomic conventional commits, documentation honesty. Replaced the rest — pairing
mode teaches Java and the JVM, verification is `./gradlew build`, layout
describes one Gradle module with packages.

Three warnings carried over deliberately rather than dropped:

- Do not carry Rust idioms across for their own sake; a `Result` was built and
  removed for exactly that reason.
- Exceptions were chosen for **caller defects**; that is unsettled for
  data-driven refusals, so do not reintroduce `Result` without deciding first.
  See [[Decide how data-driven refusals fail]].
- Java has no equivalent of Rust's `overflow-checks`, which is why `Money`
  holds a `BigInteger`.

### Caught in my own draft

The first version of both files claimed **ArchUnit guards the domain
boundary**. It does not — ArchUnit is on the test classpath but
[[ArchUnit and Error Prone guard rules]] has not been done. Corrected before
committing to say so explicitly, including that principle 2 (no floating point)
is currently enforced by **review, not the build**.

Worth remembering as a pattern: when rewriting a working agreement, it is easy
to describe the intended state rather than the actual one. The documentation
honesty rule applies to the file that contains the documentation honesty rule.