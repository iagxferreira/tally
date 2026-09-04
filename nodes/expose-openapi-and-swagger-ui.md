---
id: 01M1MZEY3WC1EY4J4VWRSJQSV4
title: Expose OpenAPI and Swagger UI
kind: note
assignee: opencode
status: done
completed: 2026-09-04T01:12:29.622034316Z
created: 2026-09-04T01:10:21.052208269Z
updated: 2026-09-04T01:12:29.624635886Z
---

Add Quarkus SmallRye OpenAPI support for the Tally REST routes. Generate a tested OpenAPI JSON document and expose Swagger UI through Quarkus. Keep the domain framework-free and document the current route responses accurately.

## Implementation and verification, 2026-09-04

Added `io.quarkus:quarkus-smallrye-openapi`, which exposes `/q/openapi?format=json` and `/q/swagger-ui/`. Configured the generated API title as `Tally Ledger API` and version `0.1.0`. Annotated the four placeholder ledger operations with tags, summaries, and their current `501` response descriptions. Added integration assertions for the generated metadata, all four paths, and Swagger UI availability. Full `./gradlew build` passed.