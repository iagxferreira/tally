---
id: 01M1N10MTCWJ04KNH2VAE9P1P1
title: Implement account creation API
kind: note
assignee: opencode
status: done
completed: 2026-09-04T01:42:35.363363811Z
created: 2026-09-04T01:37:29.932228756Z
updated: 2026-09-04T01:42:35.365027830Z
---

Implement the first real HTTP vertical slice: POST /accounts backed by an application-scoped service holding immutable Ledger snapshots. Synchronize snapshot replacement to avoid lost updates during concurrent account creation. Use typed JSON request/response records, preserve the framework-free domain, return 201 Created, and update OpenAPI metadata/tests.

## Implementation and verification, 2026-09-04

Added `LedgerService` as an application-scoped boundary holding immutable `Ledger` snapshots. `openAccount` is synchronized so concurrent account creation cannot overwrite a newer snapshot. Added typed `CreateAccountRequest` and `AccountResponse` records; `POST /accounts` now returns `201` with the UUIDv7 account ID, kind, and currency. JSON is required (`415` otherwise), and missing kind/currency returns `400` rather than leaking a null dereference as `500`. Added OpenAPI response schema metadata and integration coverage. Full `./gradlew build` passed; the running dev server returned a created USD asset account.