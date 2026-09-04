---
id: 01M1N3FD7PPK4803FB23VKF4JH
title: Validate REST request payloads
kind: note
assignee: opencode
status: done
completed: 2026-09-04T02:22:12.164930309Z
created: 2026-09-04T02:20:30.838760373Z
updated: 2026-09-04T02:22:12.167041588Z
---

Add Jakarta Bean Validation to the HTTP request records. Reject missing account fields, missing/short transaction posting lists, null posting fields, and non-positive minor units at the HTTP boundary. Keep domain exceptions responsible for financial invariants and identifier/account resolution.

## Implementation and verification, 2026-09-04

Added `quarkus-hibernate-validator`. `CreateAccountRequest` now requires kind and currency; `PostTransactionRequest` requires at least two postings and validates nested entries; `TransactionPostingRequest` requires account ID, direction, currency, and strictly positive `BigInteger` minor units. Resource-level `@NotNull`/`@Valid` triggers standard Quarkus `400` validation responses before business logic. Removed redundant null-shape checks and the broad `NullPointerException` catch; domain and identifier failures remain handled at the application boundary. Full `./gradlew build` passed.