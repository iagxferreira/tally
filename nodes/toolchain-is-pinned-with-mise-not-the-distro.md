---
id: 01M1M36E8DW9CTAC2C59S1BQBK
title: Toolchain is pinned with mise, not the distro
kind: reference
context_for: [01M1M3J8SFNP2R5QCC1QKSRDS3]
created: 2026-09-03T16:56:22.541307088Z
updated: 2026-09-03T17:02:56.807076051Z
---

# Toolchain is pinned with mise, not the distro

Recorded 2026-09-03 while starting
[[Gradle skeleton for the Java rewrite]].

## The problem this solved

The dev machine (Fedora 44) has only `java-25-openjdk-headless` — a **JRE with
no `javac`**. `java -version` reports 25.0.4.1, which is misleading:
`/usr/lib/jvm/java-25-openjdk/bin/` holds `java`, `keytool`, `rmiregistry` and
nothing else. Gradle was not installed, `sudo` requires a password, and there is
no SDKMAN.

The obvious fix was `sudo dnf install java-25-openjdk-devel`. **Do not do that.**

## The decision

The machine already runs [mise](https://mise.jdx.dev) (2026.8.10), which
manages Go, Node, Rust, uv and — already — Java and Gradle. Toolchains are
pinned per-repo in `.mise.toml`, installed into the user's home, and need no
root.

This is strictly better for Tally:

- The Java version travels with the repository, the way `rust-toolchain.toml`
  pinned 1.97.1 before it. Same intent, same guarantee, no root.
- It survives a distro that ships a headless JRE by default.
- `mise` was already the machine's convention; a `dnf`-installed JDK would sit
  outside it and drift.

`.mise.toml` therefore replaces `rust-toolchain.toml` in the rewrite, and is
part of the skeleton commit.

## Gotcha worth remembering

Gradle's own toolchain support and `mise`'s Java pin are two different
mechanisms and can disagree. `mise` decides which `java` runs Gradle;
`java { toolchain { languageVersion } }` in `build.gradle.kts` decides what the
code compiles against. Set both, and keep them the same, or Gradle will
silently go looking for a JDK it cannot find.
