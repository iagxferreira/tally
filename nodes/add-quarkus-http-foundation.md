---
id: 01M1MXB0C65PC7GHQTKQB5M4J7
title: Add Quarkus HTTP foundation
kind: note
assignee: opencode
status: done
completed: 2026-09-04T00:36:34.574225322Z
context_for: [01M1MXATB6N2RSPZYV74T9FMDW]
created: 2026-09-04T00:33:15.142372773Z
updated: 2026-09-04T00:36:34.576009298Z
---

Start the HTTP plan with the smallest Quarkus foundation: configure the Gradle plugin/BOM and REST + Jackson test dependencies, preserving the pure domain boundary. Do not add endpoints yet; verify the project still builds and record the chosen Quarkus version and dependency rationale.

## Implementation and verification, 2026-09-03

Configured Quarkus `3.39.2` through the Gradle plugin and enforced BOM, adding `quarkus-rest`, `quarkus-rest-jackson`, and `quarkus-junit5`. No endpoints or domain changes were made. `./gradlew build` passed, including Quarkus augmentation and the existing test suite. Committed as `a5ce873 build: add Quarkus HTTP foundation`.