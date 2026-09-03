---
id: 01M1M3TH56HJAKTPC0YQ1CDC96
title: ADR 007 — Result over exceptions in the Java domain
kind: rfc
depends_on: [01M1M2QZB7RMP60Y5P7CX880SS]
context_for: [01M1M2W2B9QCJ45WAYBHKFACEJ]
created: 2026-09-03T17:07:20.870759173Z
updated: 2026-09-03T18:39:52.984839078Z
---

# ADR 007 — Result over exceptions in the Java domain

**Status:** accepted, 2026-09-03
**Follows:** [[ADR 006 — Tally is rewritten in Java]]

## Context

`Money` is the first domain type that can fail — cross-currency addition and
64-bit overflow. The choice shapes every type above it, so it is made before
`Money` is written.

Rust's `Result<T, E>` did two separable things, and Java preserves one more
easily than the other:

1. Failure was **visible in the signature**.
2. Failure was **unignorable** — `#[must_use]` plus having to unwrap.

The working agreement also bans `String`-based domain errors: errors are
matchable values, not prose.

## Options considered

**A. Unchecked exceptions.** Idiomatic, composes with everything, free on the
happy path. Loses *both* properties: the signature says nothing and the compiler
corrects nobody. A dropped currency mismatch becomes a production incident
rather than a build failure. Rejected on principle 5.

**B. Checked exceptions.** The only JVM mechanism that genuinely forces
acknowledgement, so it is the closest match to property 2. But it composes
badly with `Stream`, `Optional` and lambdas — and `Transaction` will fold over
collections of postings, which is precisely where that hurts. Also invites
`catch (Exception e)` swallowing, which is worse than either alternative.
Rejected on composition, not fashion.

**C. Hand-rolled `Result<T, E>` — chosen.**

```java
public sealed interface Result<T, E> permits Result.Ok, Result.Err {
    record Ok<T, E>(T value) implements Result<T, E> {}
    record Err<T, E>(E error) implements Result<T, E> {}
}
```

Recovers property 1 fully. Recovers *most* of property 2: because the interface
is sealed, a `switch` over `Ok`/`Err` is checked for exhaustiveness by the Java
25 compiler, and adding a case later breaks every incomplete switch. Not all of
it — nothing stops an `instanceof Ok` cast that ignores the other branch.

Costs an allocation per fallible call. Accepted: correctness before
performance, and principle 8 requires measurement before optimising.

## Decision

Fallible domain operations return `Result<T, E>`. Error types are **sealed
interfaces carrying the values that caused the failure**:

```java
public sealed interface MoneyError {
    record CurrencyMismatch(Currency left, Currency right) implements MoneyError {}
    record Overflow(long left, long right) implements MoneyError {}
}
```

### Exceptions still have a job

`Result` is for **expected domain failures** — a caller asking for something the
domain must refuse. Unchecked exceptions remain right for **programmer errors**:
a null argument, an invariant that should have been unreachable. The test is
whether a well-written caller could reasonably hit it.

Consequence worth remembering: `Math.addExact` signals overflow by throwing
`ArithmeticException`. That is a JDK implementation detail, not a domain error.
`Money` catches it at the boundary and converts it to `Err(new Overflow(...))`.
**The exception must not escape the domain.**

## Consequences

- Every fallible call site becomes a `switch`. Java has no `?` operator and this
  ADR does not invent one. A `map`/`flatMap` combinator chain would recover
  brevity at the cost of exactly the generic abstraction principle 10 warns
  against — revisit only with evidence from `Transaction`.
- `Result` is the **one** piece of generic machinery the domain is allowed. A
  second one needs its own ADR.
- Reversal is mechanical and cheapest now, while only `Money` depends on it.

---

## Amended 2026-09-03 — combinators added, commit `d0479d1`

This ADR originally said "no `map`, no `flatMap`", and set a trigger: revisit
only with evidence of real verbosity in `Transaction`. The maintainer raised the
question early, and the evidence turned out to be nameable in advance rather
than speculative — so the trigger is treated as met.

**The concrete call site:** `Transaction` must sum postings per currency.
`Money.add` returns a `Result`. Without `flatMap` that fold is a loop with an
unwrap in its middle, which is exactly the shape that lets an error be dropped.
Java has no `?` operator, so `flatMap` is the nearest available equivalent to
Rust's propagation.

**Four methods, each against a call site that exists:**

| Method | Justified by |
|---|---|
| `map` | transform a success without unwrapping |
| `flatMap` | chain fallible operations; the `Transaction` fold |
| `mapError` | `MoneyError` → aggregate-level error at a boundary |
| `fold` | terminal consumption where a `switch` is noise |

**Still refused:** `or`, `orElse`, `filter`, `recover`, `peek`, `stream`,
`ifOk`. That is how a `Result` becomes Vavr. Principle 10 — explicit domain
concepts over generic abstraction — is what stands in the way. A fifth
combinator needs a reason at the time it is added.

### Java details worth keeping

They take `java.util.function.Function`, not bespoke interfaces. Reuse the JDK's
functional interfaces unless a different *shape* is genuinely needed — checked
exceptions, or more than two arguments.

The wildcards (`? super T`, `? extends U`) are not decoration, and neither is
this, which surprised on first write:

```java
case Err<T, E>(var error) -> new Err<>(error);   // rebuild, cannot cast
```

Java's generics are **invariant**, so `Err<T, E>` and `Err<U, E>` are unrelated
types even though `map` carries the error across untouched. The `Err` has to be
reconstructed. Rust's `Result` had the same requirement but the pattern is
easier to miss here because the value genuinely does not change.

---

## SUPERSEDED 2026-09-03 by [[ADR 009 — Domain failures are exceptions, not Result]]

`Result`, `MoneyError` and the four combinators added in the amendment above
were **removed** in commit `9db4212`. Do not reintroduce them from this record.

The maintainer's objection was that this is Rust carried into Java, and it was
correct. What sealed it is that **this ADR contradicted itself**: it defined the
dividing line as "whether a well-written caller could reasonably hit it", and a
well-written caller never adds dollars to yen. By its own test, currency
mismatch belonged in the exception column from the start. `Result` was applied
to it reflexively because the Rust version did.

The requirement this ADR was actually protecting — errors are matchable values,
not prose — survives intact under exceptions, since the exception subclasses
carry typed fields rather than only a message.

What was genuinely lost: failure is no longer visible in the signature. That is
accepted for caller defects. It is **not** pre-decided for data-driven refusals
such as an unbalanced transaction, which are settled when `Transaction` is
built.