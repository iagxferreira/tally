# ADR 007 — Error handling in the Java domain

- **Status:** Accepted
- **Date:** 2026-09-03
- **Deciders:** Iago Ferreira
- **Context:** ADR 006 (the move to Java)

## Context

`Money` is the first domain type that can fail: adding two amounts in different
currencies is not an error the caller can be trusted to remember about, and
neither is 64-bit overflow. How that failure is expressed shapes every type
built on top of it, so it is decided before `Money` is written rather than
after.

The Rust version returned `Result<T, E>`. That did two things at once, and it
is worth separating them because Java preserves one more easily than the other:

1. It made failure **visible in the signature** — the return type said the
   operation could fail, and which ways.
2. It made failure **unignorable** — `#[must_use]` on `Result` meant discarding
   one was a warning, and getting the value out required acknowledging the error
   case.

The working agreement also bans `String`-based domain errors: errors must be
matchable values, not prose. `anyhow`-style erasure belongs in applications.

## Options considered

### A. Unchecked exceptions

`Money.add` throws `CurrencyMismatchException`.

- Idiomatic Java, composes with everything, zero allocation on the happy path.
- Loses **both** properties above. The signature says nothing, and a caller who
  forgets is not corrected by the compiler — a dropped currency mismatch
  becomes a production incident rather than a build failure.
- Rejected. Principle 5 says invalid financial states should be difficult to
  represent; this makes mishandling them easy and silent.

### B. Checked exceptions

`Money.add(...) throws CurrencyMismatchException`.

- The only JVM mechanism that genuinely forces the caller to acknowledge
  failure, which is the closest match to property 2.
- Composes badly with `Stream`, `Optional` and lambdas — and `Transaction` will
  fold over collections of postings, which is exactly where this hurts.
- Encourages `catch (Exception e)` swallowing under pressure, which is worse
  than either alternative.
- Rejected on composition grounds, not on fashion.

### C. A hand-rolled `Result<T, E>` (chosen)

```java
public sealed interface Result<T, E> permits Result.Ok, Result.Err {
    record Ok<T, E>(T value) implements Result<T, E> {}
    record Err<T, E>(E error) implements Result<T, E> {}
}
```

- Failure is visible in the signature, recovering property 1 fully.
- Java 25's pattern matching for `switch` makes consumption exhaustive: because
  the interface is `sealed`, a `switch` over `Ok` and `Err` is checked by the
  compiler, and adding a case later breaks every incomplete switch. That
  recovers much of property 2 — not all of it, since nothing stops a caller
  writing an `instanceof Ok` cast and ignoring the other branch.
- Costs an allocation per fallible operation. Accepted: correctness before
  performance, and principle 8 says optimisation requires measurement, which
  there is none of yet.
- Fights parts of the JVM ecosystem that expect exceptions. Contained, because
  the domain does not touch that ecosystem — that is what ADR 002 and ADR 006
  are for.

## Decision

Fallible domain operations return `Result<T, E>`. Error types are **sealed
interfaces**, never strings and never enums-with-a-message:

```java
public sealed interface MoneyError {
    record CurrencyMismatch(Currency left, Currency right) implements MoneyError {}
    record Overflow(long left, long right) implements MoneyError {}
}
```

Each error carries the values that caused it, so a caller can report the fault
without the domain formatting prose.

### Exceptions are still used, for a different thing

`Result` is for **expected domain failures** — a caller doing something the
domain must refuse. Unchecked exceptions remain correct for **programmer
errors**: a null argument, a broken invariant that should have been impossible.
The distinction is whether a well-written caller could reasonably hit it.

One consequence worth stating: `Math.addExact` signals overflow by throwing
`ArithmeticException`. That is an implementation detail of the JDK, not a domain
error, so `Money` catches it at the boundary and converts it into
`Err(new Overflow(...))`. The exception must not escape the domain.

## Consequences

- Every fallible call site becomes a `switch`, which is more verbose than Rust's
  `?`. Java has no error-propagation operator and this ADR does not invent one:
  a `map`/`flatMap` combinator chain would recover brevity at the cost of being
  the kind of generic abstraction principle 10 warns against. Revisit only if
  the verbosity becomes a real problem in `Transaction`, with evidence.
- `Result` lives in `tally.domain` and is the one piece of generic machinery
  the domain is allowed. If a second one is proposed, it needs its own ADR.
- If this proves wrong, the reversal is mechanical and cheapest now, while only
  `Money` depends on it.
