---
id: 01M1MS0TFHAT5ARSG8792Z6ESH
title: Ignore generated bin output
kind: note
assignee: opencode
relates_to: [01M1JZSF51ZD17X6TC2E4BYWE9]
created: 2026-09-03T23:17:47.120960027Z
updated: 2026-09-03T23:19:19.090409701Z
---

The repository-root `bin/` directory contains compiled Java class files from the local build and is now ignored via `/bin/` in `.gitignore`. This prevents generated artifacts from appearing as untracked project changes.