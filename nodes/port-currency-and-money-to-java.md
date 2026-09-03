---
id: 01M1M2W2B9QCJ45WAYBHKFACEJ
title: Port Currency and Money to Java
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T17:12:24.328559789Z
depends_on: [01M1M2VY6J2WY8DV8DVNGC0F1B]
created: 2026-09-03T16:50:42.665659872Z
updated: 2026-09-03T17:12:24.330594787Z
---

Increments 2-3 of [[Tally Java MVP — development plan]]. Carries
[[ADR 001 — Money representation]].

`Currency` — enum with ISO 4217 code and decimal scale. All three real scales:
JPY=0, USD/EUR/GBP/BRL=2, KWD=3.

`Money` — `long` minor units plus currency. **Every** arithmetic operation goes
through `Math.addExact` / `Math.subtractExact` / `Math.multiplyExact`. Java
wraps silently on `long` overflow in all builds with no profile to change it,
so unlike the Rust version there is no backstop if a raw `+` slips in — these
calls are load-bearing.

Rejects cross-currency arithmetic; no implicit conversion, ever.

**Done when:** tests cover overflow at `Long.MAX_VALUE`, currency mismatch, and
scale-correct formatting for all three scales.</body>

---

## Done 2026-09-03 — commits `d005478`, `28bb50b`, `66aed43`

Three atomic commits: `Result`, then `Currency`, then `Money` with 17 tests.
Each intermediate state was actually built rather than assumed — `Money` was
moved aside so the `Result` + `Currency` tree could be compiled on its own.

### Java lesson worth keeping: records collide with interface methods

`Result` was first written with `default Optional<T> value()` and
`default Optional<E> error()`. It would not compile:

```
error: value() in Ok cannot implement value() in Result
    record Ok<T, E>(T value) implements Result<T, E> {}
    return type T#1 is not compatible with Optional<T#1>
```

A record's **generated component accessor implements any same-named interface
method**, and the component name *is* the method name — there is no way to
rename or suppress it. Rust had no equivalent problem because enum variants
carry fields, not methods.

Fixed by deleting both helpers rather than renaming them: ADR 007 says the
exhaustive `switch` is the consumption mechanism, and those accessors were
convenience that worked against it. Only `isOk()` and `orElseThrow()` remain.

### Bug caught before it shipped

`toString` used `Math.abs(minorUnits)` to strip the sign. **`Math.abs(Long.MIN_VALUE)`
returns `Long.MIN_VALUE`** — still negative — because two's complement has no
positive counterpart for it. Formatting the smallest possible amount would have
produced garbage. Now strips the sign from the string instead, and there is a
test pinning `-92233720368547758.08 USD`.

The same asymmetry is why `negate()` is fallible at all.

### Design notes

- `subtract` may return a negative `Money`. `Money` is a quantity, not a
  posting; strict positivity belongs to `Posting`, where `Direction` carries
  the sign instead.
- `compareTo` **throws** rather than returning a `Result`, because
  `Comparable` fixes the signature. Ordering USD against JPY is a programmer
  error, not a domain outcome — consistent with the ADR 007 split.
- `Currency` stayed a closed enum, so a `switch` over currencies is
  compiler-checked.