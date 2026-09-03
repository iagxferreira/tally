---
id: 01M1M2VY6J2WY8DV8DVNGC0F1B
title: ADR 007 — Error handling in the Java domain
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T17:08:02.168979809Z
depends_on: [01M1M2VN1EK18WV2Q8Y6GF1Z6N]
created: 2026-09-03T16:50:38.418775666Z
updated: 2026-09-03T17:08:02.171383528Z
---

Blocks `Money`, so it must be decided before increment 3 of
[[Tally Java MVP — development plan]].

Rust's `Result<T, E>` made failure unignorable at the type level and the
working agreement banned `String`-based domain errors in favour of typed enums.
Java has no direct equivalent. The options, with the trade-off that matters:

1. **Checked exceptions** — the only JVM mechanism that forces the caller to
   acknowledge failure, which is exactly what `Result` did. Unfashionable, and
   they compose badly through streams and lambdas.
2. **Unchecked exceptions** — idiomatic and readable, but a dropped currency
   mismatch becomes a runtime incident. Weakens principle 5.
3. **A hand-rolled `Result<T, E>`** — closest to the Rust original, and with
   sealed interfaces plus pattern matching in Java 25 it reads well. Costs an
   allocation per operation and fights the rest of the JVM ecosystem.

Whatever is chosen, the error *type* must be a sealed hierarchy, not a string
message — that part of the working agreement carries over unchanged.

**Done when:** the ADR is written to `docs/adr/` and recorded in the vault.</body>

---

## Done 2026-09-03 — chosen: hand-rolled `Result`

The decision itself lives in
[[ADR 007 — Result over exceptions in the Java domain]]. This task node is the
request; that node is the record. Read that one.

Summary: fallible domain operations return a sealed `Result<T, E>`; error types
are sealed interfaces carrying the values that caused the failure. Unchecked
exceptions stay for programmer errors only. `Math.addExact`'s
`ArithmeticException` is converted to `Err(Overflow)` at the `Money` boundary
and must not escape the domain.

Checked exceptions were the closest match to Rust's unignorable-failure
property and were still rejected — they compose badly with the stream folds
`Transaction` will need.

### Process change made during this task

The maintainer directed that **MindGraph handles docs**, so `docs/adr/` was
removed from the repo (commit `bb3d78b`) and the vault is now the single source
of truth for decision records. ADRs 006 and 007 were briefly written to
`docs/adr/` first (commits `04e1d28`, `ad27500`) before that instruction; those
commits stand in history but the folder is gone. Do not recreate it.