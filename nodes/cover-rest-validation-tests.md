---
id: 01M1N3PX5PTNXTHADT5AZN04PS
title: Cover REST validation tests
kind: note
assignee: opencode
status: done
completed: 2026-09-04T02:26:45.175131223Z
created: 2026-09-04T02:24:36.534653202Z
updated: 2026-09-04T02:26:45.179489463Z
---

Add integration tests proving the REST Bean Validation constraints reject too few transaction postings, non-positive minor units, and invalid nested posting fields.

## Implementation and verification, 2026-09-04

Added integration coverage for transaction request validation: fewer than two postings, zero minor units, and invalid nested posting fields all return `400` before service execution. Full `./gradlew build` passed.