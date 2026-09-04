---
id: 01M1MXKJQ9BTYCC533D0ZTRVMQ
title: Create Tally REST routes
kind: note
assignee: opencode
status: done
completed: 2026-09-04T00:39:55.232593046Z
created: 2026-09-04T00:37:56.073168604Z
updated: 2026-09-04T00:39:55.234286995Z
---

Define the first Quarkus REST route contract and drive endpoint behavior with TDD. Keep HTTP resources in `tally.core`; the domain remains framework-free. Start with accounts, balances, transactions, and journal routes, but implement only the smallest tested vertical slice per increment.

## Scope decision, 2026-09-03

First increment defines four route contracts only: `POST /accounts`, `GET /accounts/{id}/balance`, `POST /transactions`, and `GET /journal`. Each initially returns `501 Not Implemented`; domain/application behavior will be a later TDD increment. Use Quarkus REST integration tests to prove route matching.

## Implementation and verification, 2026-09-03

Added `LedgerResource` in `tally.core` with `POST /accounts`, `GET /accounts/{id}/balance`, `POST /transactions`, and `GET /journal`. Each route currently returns a JSON `501 Not Implemented` response; no domain or application behavior is implied. Added `@QuarkusTest`/REST-assured tests. The tests first failed with `404` before the resource existed, then passed after route registration. Full `./gradlew build` passed.