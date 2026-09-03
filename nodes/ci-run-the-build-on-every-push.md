---
id: 01M1MH42R29DSSV5055BMP2XM1
title: "CI: run the build on every push"
kind: note
assignee: claude-code
status: todo
created: 2026-09-03T20:59:45.282827754Z
updated: 2026-09-03T20:59:45.282827754Z
---

There is no CI. `.github/` does not exist, and nothing verifies the build on
push or on a pull request.

This is a public repository with 155 tests that only ever run on one laptop.
Every claim the README makes about what is enforced rests on a suite nobody
else can see pass.

## What it needs to do

`./gradlew build` — which is compilation with `-Xlint:all -Werror`, Error Prone,
and the tests. That is already the single command the working agreement names,
so CI has nothing to invent.

## Decisions to make while doing it

- **JDK provisioning.** `mise.toml` pins Temurin 25.0.4 and Gradle 9.7.0.
  `jdx/mise-action` uses that file directly and keeps one source of truth;
  `actions/setup-java` is the conventional choice but restates the version, so
  the pin can drift. Prefer mise for exactly that reason.
- **Gradle caching**, so runs are not several minutes of downloading.
- Whether to run on pull requests as well as pushes to `main`.

## Why this matters more than it looks

It is also the trigger for [[Docker configuration for Tally]], which is
currently the only ready task and is deliberately parked. That node splits
Docker into a reproducible build image — *useful once there is CI* — and a
runtime image, which is not earned until Phase 2 brings a database. Settling CI
settles whether the build image has a job to do.

**Done when:** a green check appears on a push to `main`, and a deliberately
broken test makes it red.