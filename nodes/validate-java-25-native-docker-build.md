---
id: 01M1MYB631JN7HKBXGH5BNT0AE
title: Validate Java 25 native Docker build
kind: note
assignee: opencode
status: done
completed: 2026-09-04T01:02:47.837260281Z
created: 2026-09-04T00:50:49.569716797Z
updated: 2026-09-04T01:02:47.839974253Z
---

Retain Tally's Java 25 toolchain and validate native compilation using a pinned or explicitly selected Java 25 GraalVM builder in Docker. Do not downgrade Java or silently use an unsupported JDK. If native compilation succeeds, add reproducible Docker packaging; if not, record the incompatibility and stop.

## Implementation and verification, 2026-09-04

Configured Quarkus container native builds with the UBI9 Mandrel Java 25 builder. `./gradlew test quarkusBuild -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false` compiled the native executable successfully; Quarkus reported Mandrel `25.0.4.1`. The executable started and served `/journal` with the expected `501` response. Added `.dockerignore` and a two-stage `src/main/docker/Dockerfile.native`; `docker build --file src/main/docker/Dockerfile.native --tag tally:native .` passed. The final image uses `quarkus-micro-image:2.0`, user `1001`, port `8080`, and the native entrypoint. The regular `./gradlew build` also passed.