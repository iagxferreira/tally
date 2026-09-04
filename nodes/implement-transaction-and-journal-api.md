---
id: 01M1N2F3KVQVZ1TRWXCFNHECNS
title: Implement transaction and journal API
kind: note
assignee: opencode
status: done
completed: 2026-09-04T02:06:51.298647242Z
created: 2026-09-04T02:02:52.411054642Z
updated: 2026-09-04T02:06:51.300596664Z
---

Build POST /transactions and GET /journal over the existing in-memory Ledger. Use exact minor-unit amounts and typed HTTP DTOs. Resolve account IDs through the ledger before constructing Transaction so domain invariants remain authoritative. Synchronize transaction posting with the application ledger snapshot; expose immutable journal response DTOs.

## Implementation and verification, 2026-09-04

Added typed transaction request/response DTOs using `BigInteger minorUnits`, currency, direction, and account UUID. `LedgerService.postTransaction` resolves every account through the current ledger, creates postings through `Account.post`, constructs with `Transaction.of`, and synchronizes replacement of the immutable ledger snapshot. `POST /transactions` returns `201` for a balanced transaction and `400` for malformed/unbalanced/domain-refused input. `GET /journal` maps the immutable journal to explicit response DTOs in posting order. Added integration tests for JSON requirements, balanced posting, unbalanced refusal, and journal visibility. Full `./gradlew build` passed.