---
id: 01M1MXATB6N2RSPZYV74T9FMDW
title: Quarkus as Tally HTTP layer
kind: rfc
assignee: opencode
created: 2026-09-04T00:33:08.966503427Z
updated: 2026-09-04T00:33:08.966503427Z
---

Decision: use Quarkus for Tally's HTTP layer. Quarkus REST and Jackson may depend on `tally.core`, while `tally.domain` remains free of framework and transport types. CDI is accepted in `tally.core` for application composition. The immutable domain Ledger remains behind an application service backed by `AtomicReference`, because Quarkus serves requests concurrently and framework scope does not provide atomic state transitions. The first HTTP implementation remains in-memory and non-persistent; idempotency and cross-process consistency remain later concerns.