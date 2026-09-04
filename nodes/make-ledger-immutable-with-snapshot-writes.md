---
id: 01M1MV1J3SKYN2WZJEFJ455MTM
title: Make Ledger immutable with snapshot writes
kind: note
assignee: opencode
status: done
completed: 2026-09-03T23:56:32.501692689Z
created: 2026-09-03T23:53:08.473961071Z
updated: 2026-09-03T23:56:32.503534547Z
---

Refactor the in-memory Ledger from mutable maps to immutable snapshots. `register` and `post` should return new Ledger instances backed by defensive immutable copies; existing snapshots must never change. Use standard JDK collections for now, avoiding a persistent-collections dependency until measurement justifies it. This establishes safe concurrent reads while leaving writer coordination explicit for later phases.

## Decision, 2026-09-03

Use standard JDK defensive immutable copies for the first functional version. `register` and `post` will return new snapshots; existing `Ledger` instances remain unchanged. Avoid persistent-collection dependencies until performance measurements justify them.

## Implementation and verification, 2026-09-03

Refactored `Ledger` to immutable snapshots. `register`, `registerAll`, and `post` return a new `Ledger`; existing instances retain immutable account and journal maps. JDK defensive copies preserve journal insertion order without adding a dependency. Added tests proving source snapshots remain unchanged and writes from one snapshot form independent branches. `./gradlew build` passed. Readers can now share snapshots safely; coordinating which snapshot becomes the current writer state remains outside this class.