---
id: 01M1MH3SXQ5TBJ434B2NAWA3S2
title: Property tests for the double-entry invariants
kind: note
assignee: opencode
status: doing
relates_to: [01M1M2WS9DWCXM34B3KG63HB86]
created: 2026-09-03T20:59:36.247145650Z
updated: 2026-09-03T23:21:36.278621066Z
---

Split out of [[Transaction — enforce the double-entry balance invariant]] on
2026-09-03, because that node's "done when" required property tests and it was
closed with example-based tests only. Recording the gap rather than leaving a
false "done".

The Phase 1 scope in [[Tally]] also lists property tests explicitly.

## Why examples are not enough here

Every current test picks its own numbers. That verifies the cases someone
thought of, which is exactly the wrong shape for invariants that must hold for
*all* inputs. The properties worth generating over:

- **Balance is preserved under reversal.** For any balanced transaction,
  posting it and then its reversal returns every account to its prior balance.
- **The journal determines the balances.** For any sequence of transactions,
  folding the journal from scratch equals the ledger's reported balances —
  which is invariant 8 stated as a property.
- **Order does not matter.** Any permutation of the same postings produces the
  same balances, since addition is commutative and no state is carried.
- **The accounting equation holds.** After any sequence of postings,
  `assets + expenses == liabilities + equity + revenue`. This is the property
  the whole design exists to preserve and nothing currently asserts it.
- **Unbalanced sets are always refused.** For any set of postings whose debits
  and credits differ, `Transaction.of` throws and `imbalanceOf` reports exactly
  the difference.
- **Money arithmetic.** Addition is associative and commutative; `negate` is an
  involution; no sequence of operations can produce a wrong total, which
  matters more now that `BigInteger` removed the overflow guard rails.

Note the multi-currency generation the original node asked for is **moot** —
[[Decide multi-currency scope for the MVP]] restricted transactions to a single
currency, so generators should produce single-currency sets and separately
assert that mixed sets are refused.

## Tool

jqwik is the usual JUnit 5 choice. Adding it is a test-scope dependency, so the
bar is lower than ArchUnit's was — but the same question applies: does it earn
its place, or would a loop over randomised inputs in plain JUnit do? Decide
before adding, and record why.

**Done when:** the accounting equation and the reversal property are covered by
generated inputs, not chosen ones.

## Decision, 2026-09-03

Use plain JUnit 5 randomized loops rather than adding jqwik. The project keeps its dependency surface small, and the generated cases can remain explicit and readable in the existing test style.

## Compiler feedback, 2026-09-03

The first test draft failed compilation for two ordinary Java reasons: a hexadecimal `Random` seed without `L` was treated as an oversized `int`, and a lambda could not capture `credit` after reassignment because it was not effectively final. The fix was to use a `long` seed and copy the adjusted value to `unequalCredit` before capture. This was a test-only correction; no production code was added.