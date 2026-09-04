---
id: 01M1N4BPQHKMKAJT88PDXR3ZAR
title: Implement account balance API
kind: note
assignee: opencode
status: done
completed: 2026-09-04T03:43:31.354725860Z
created: 2026-09-04T02:35:58.065220755Z
updated: 2026-09-04T03:43:31.356722730Z
---

Replace the placeholder GET /accounts/{id}/balance route with a typed balance response derived from the in-memory Ledger. Preserve exact minor units and currency, return 404 for an unknown account and 400 for a malformed UUID, and cover the behavior with REST integration tests.

Implemented 2026-09-04: GET /accounts/{id}/balance now delegates to Ledger.balanceOf through LedgerService and returns typed BalanceResponse with exact BigInteger minor units and currency. Valid unknown UUIDs return 404; malformed UUIDs and non-v7 UUIDs return 400. Added integration coverage for zero balance, derived posted balance, unknown account, malformed ID, and OpenAPI schema. Full ./gradlew build passed.