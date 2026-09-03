---
id: 01M1M3J8SFNP2R5QCC1QKSRDS3
title: Docker configuration for Tally
kind: note
assignee: claude-code
status: todo
depends_on: [01M1MH42R29DSSV5055BMP2XM1]
relates_to: [01M1M2TYB9BP16Z9RYEKJTBW3C]
created: 2026-09-03T17:02:50.159289275Z
updated: 2026-09-03T20:59:52.674659830Z
---

Requested 2026-09-03 alongside the Java rewrite
([[Tally Java MVP — development plan]]).

## Honest scoping first

The MVP has **no infrastructure**: no database, no HTTP server, no broker. A
`Dockerfile` for an in-memory library that exposes no port and stores nothing
would be architecture cosplay, which principle 9 exists to prevent. So this
task is scheduled but deliberately **not** ready work yet — what it should
contain depends on which of two things it is for:

1. **A reproducible build/CI image** — useful *now*. A container pinning JDK 25
   and Gradle so the build is reproducible off this machine, and so CI does not
   depend on `mise` being present. This is small and genuinely earns its place
   once there is CI.
2. **A runtime image + compose stack** — not earned until Phase 2. That is when
   PostgreSQL arrives and `docker compose` has something to compose: the app,
   a database, and eventually a broker. Writing it before then means guessing
   at a topology that does not exist.

## When it becomes real

Trigger: the first infrastructure dependency landing (Phase 2, PostgreSQL).
That is the same trigger ADR 002 named for revisiting the module split, so the
two should be considered together.

## Notes for whoever picks it up

- The build already pins its own toolchain twice (`mise.toml` and the Gradle
  toolchain block), so a build image must not introduce a *third* JDK version
  that can drift. Pin it from the same Temurin 25.0.4 build.
- Prefer a multi-stage build: Gradle + JDK to compile, JRE-only to run. The
  dev machine's headless-JRE situation is a reminder that runtime and compile
  images have genuinely different needs.
- `.dockerignore` must exclude `build/`, `.gradle/` and `nodes/`.

**Done when:** it is decided which of the two images is wanted, and only that
one is written.</body>
