---
id: 01M1MSHP7GJE3G4JE2V598TWW2
title: Reject conflicting account registration
kind: note
assignee: opencode
status: done
completed: 2026-09-03T23:46:20.032377255Z
relates_to: [01M1M2WXTAH040CM7WK4GJTZ99]
created: 2026-09-03T23:26:59.824516571Z
updated: 2026-09-03T23:46:20.034279340Z
---

Prevent `Ledger.register` from silently replacing an account with the same `AccountId` but a different kind or currency. First prove the bug with a failing regression test, then decide and implement the typed refusal behavior. The reason is journal integrity: historic postings must not be reinterpreted by mutable account metadata.

## Proof, 2026-09-03

Added `LedgerTest.KnownAccounts.conflictingRegistrationCannotChangeAccountMeaning`. It posts USD history for `cash`, reopens the same ID as an EUR asset, registers it, then asks for the historic USD balance. Before any production change, the test fails with `CurrencyMismatchException` at `LedgerTest.java:163`, proving that `register` silently replaces account metadata and makes existing journal facts uninterpretable.

The first targeted Gradle filter used a nested-method pattern unsupported by the configured test runner and reported no tests found. Re-running with `--tests tally.domain.LedgerTest` exercised the class and produced the expected one-test failure.

## Implementation and verification, 2026-09-03

`Ledger.register` now uses `putIfAbsent` and compares an existing account's kind and currency before accepting a duplicate definition. A mismatch throws `ConflictingAccountException`, which is sealed under `DomainException` and carries the account ID, existing definition, and refused definition as typed fields. The regression and exception hierarchy tests pass, and `./gradlew build` passed. The initial compiler warning about a dangling constructor Javadoc comment was fixed by combining both `@param` entries into one attached block.