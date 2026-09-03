---
id: 01M1M93MFQK4F0FXKR09ZCEJXT
title: ADR 009 — Domain failures are exceptions, not Result
kind: rfc
relates_to: [01M1M3TH56HJAKTPC0YQ1CDC96]
created: 2026-09-03T18:39:42.071378573Z
updated: 2026-09-03T18:46:00.167607717Z
---

# ADR 009 — Domain failures are exceptions, not Result

**Status:** accepted, 2026-09-03
**Supersedes:** [[ADR 007 — Result over exceptions in the Java domain]]
**Follows:** [[ADR 006 — Tally is rewritten in Java]]

## Context

ADR 007 chose a hand-rolled `Result<T, E>` for fallible domain operations, and
was later amended to add `map`, `flatMap`, `mapError` and `fold`. Both decisions
are reversed here, days later, and it is worth recording *why* rather than
quietly rewriting.

The maintainer's objection: this is Rust carried into Java. ADR 006 states the
learning goal is now Java and the JVM; writing Rust-shaped Java undercuts it,
and principle 9's prohibition on architecture cosplay applies to idiom, not only
to infrastructure.

## The argument that actually settled it

**ADR 007 contradicted itself.** It stated the dividing line explicitly:

> `Result` is for expected domain failures — a caller doing something the domain
> must refuse. Unchecked exceptions remain correct for programmer errors. The
> distinction is whether a well-written caller could reasonably hit it.

Apply that test to `CurrencyMismatch`. **A well-written caller never adds
dollars to yen.** It is a defect in the calling code, not a condition anyone
catches and recovers from. By ADR 007's own criterion it belonged in the
exception column. `Result` was applied to it anyway — reflexively, because the
Rust implementation did.

Once that is granted, the machinery loses its justification. `flatMap` existed
chiefly to chain operations that could have been chained directly:

```java
a.add(b).flatMap(s -> s.add(c))   // before
a.add(b).add(c)                    // after
```

## Decision

Domain failures are **unchecked exceptions**, in one hierarchy:

- `DomainException extends RuntimeException` — abstract base for every domain
  refusal. A single `catch (DomainException e)` at an application boundary is
  the intended shape.
- `CurrencyMismatchException extends DomainException` — carries both currencies.

`Result`, `MoneyError` and the four combinators are deleted.

`Money.add` and `Money.subtract` return `Money`. `compareTo` throws
`CurrencyMismatchException` rather than `IllegalArgumentException`, so mixing
currencies fails **one** way everywhere instead of two.

## What survives from ADR 007

The requirement ADR 007 was really protecting was never `Result` — it was that
**domain errors are matchable values, not prose**. That holds:

- Exception subclasses carry the values that caused the failure as **typed
  fields** (`left()`, `right()`), not merely a formatted string.
- The message exists for stack traces and logs. Code that reacts to a failure
  matches on the exception type and reads its fields; nothing parses a message.
- No `String`-based domain errors, exactly as the working agreement requires.

## What was given up

Failure is no longer visible in the signature. A caller can forget to handle a
`CurrencyMismatchException` and find out at runtime, where `Result` put it in
the return type. This is a real loss and is accepted knowingly: the failures in
question are caller defects, so "find out at runtime, loudly, with a stack
trace" is an acceptable outcome for them.

**This reasoning does not automatically extend to data-driven refusals.** An
unbalanced transaction or a non-positive posting amount can arrive from
legitimate input, and a caller may genuinely need to handle it. Those are
decided when `Transaction` and `Posting` are built, not pre-judged here. If one
of them warrants a return-typed outcome, that needs its own ADR and a reason at
the time — reintroducing `Result` wholesale would be re-opening this decision,
not applying it.

Implemented in commit `9db4212`. 21 tests remain, all describing money rather
than plumbing; 12 tests that existed only to verify the machinery went with it.

---

## Amended 2026-09-03 — the hierarchy is sealed, commit `0e0de83`

`DomainException` is now `sealed ... permits CurrencyMismatchException`.

This makes error handling a **compile-time** concern. A `switch` over a domain
failure is checked for exhaustiveness and takes no `default` branch, so adding
a failure to the `permits` clause breaks every handler that does not account
for it:

```java
String describe(DomainException failure) {
    return switch (failure) {          // no default
        case CurrencyMismatchException e ->
            e.left().code() + "!=" + e.right().code();
    };
}
```

An open hierarchy was the wrong default for a ledger: it forces handlers to end
in a catch-all that silently swallows failure types nobody considered, so a new
failure mode surfaces as an unhandled case in production rather than as a
broken build.

**Verified rather than assumed** — an unpermitted subclass was added
temporarily and `javac` rejected it:

```
error: class is not allowed to extend sealed class: DomainException
       (as it is not listed in its 'permits' clause)
```

The cost is editing `permits` for each new failure. That is the mechanism
working, not friction to route around. A subclass must also be `final`,
`sealed` or `non-sealed`; ours are `final`.

### Deliberately not built

No translation layer from domain failures to HTTP statuses, problem-detail
payloads, or retry decisions. Those need a boundary to sit at, none exists yet,
and the shape depends entirely on which boundary it turns out to be. See
[[Docker configuration for Tally]] for the same reasoning applied elsewhere:
the trigger is the arrival of real infrastructure, not the anticipation of it.