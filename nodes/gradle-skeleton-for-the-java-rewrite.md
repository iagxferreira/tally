---
id: 01M1M2VN1EK18WV2Q8Y6GF1Z6N
title: Gradle skeleton for the Java rewrite
kind: note
assignee: claude-code
status: done
completed: 2026-09-03T17:02:16.610104459Z
relates_to: [01M1M2TYB9BP16Z9RYEKJTBW3C]
created: 2026-09-03T16:50:29.038654813Z
updated: 2026-09-03T17:02:16.611394372Z
---

Increment 1 of [[Tally Java MVP — development plan]].

Stand up the Java build and remove the Rust one in the same commit, so `main`
never sits in a half-language state.

**Add:** `build.gradle.kts`, `settings.gradle.kts`, Gradle wrapper, Java 25
toolchain, JUnit 5, ArchUnit, Error Prone. Package skeleton `tally.domain` and
`tally.core`.

**Remove:** `Cargo.toml`, `Cargo.lock`, `rust-toolchain.toml`, `src/`,
`target/`. Update `.gitignore` (`/target` and `**/*.rs.bk` go; `build/`,
`.gradle/` arrive).

**Done when:** `./gradlew build` passes on a clean checkout and no Rust file
remains outside git history.</body>

---

## Blocked 2026-09-03 — no JDK on the dev machine

Attempted to start this and stopped before touching the repo.

`java -version` reports OpenJDK 25.0.4.1, which is misleading: only the
**headless JRE** is installed. `/usr/lib/jvm/java-25-openjdk/bin/` contains
`alt-java`, `java`, `keytool`, `rmiregistry` — **no `javac`**. Installed
packages are `java-25-openjdk-headless` and `java-25-openjdk-crypto-adapter`.
Gradle is not installed and there is no SDKMAN.

Needed before this task can start:

```sh
sudo dnf install java-25-openjdk-devel
```

Gradle itself is an open choice: install the distro package, or fetch
`gradle-wrapper.jar` + properties from the Gradle distribution and pin the
version in-repo (preferred — no system install, version travels with the repo).

Deliberately did **not** write the build files or remove `src/` while blocked.
The repo rule is that every commit builds and passes tests on its own; landing
a Gradle skeleton that cannot compile, alongside the deletion of the working
Rust domain, would leave `main` broken and unverifiable. The removal of Cargo
and `src/` should happen in the same commit as a build that demonstrably runs.

---

## Done 2026-09-03 — commit `e037ecd`, pushed to main

Unblocked by `mise` rather than by `dnf` — see
[[Toolchain is pinned with mise, not the distro]]. No root was needed after all.

Shipped: Gradle 9.7.0 wrapper, Java 25 toolchain, `mise.toml` replacing
`rust-toolchain.toml`, Error Prone 2.50.0, ArchUnit 1.4.1 and JUnit 5.12.2 on
the test classpath, `tally.domain` / `tally.core` package skeleton. Cargo and
all five Rust domain modules removed in the same commit.

### Compiler error worth keeping

`options.errorprone { ... }` failed to resolve in the Kotlin DSL:

```
None of the following candidates is applicable:
fun DependencyHandler.errorprone(dependencyNotation: Any): Dependency?
```

Kotlin resolved `errorprone` to the *dependency configuration* the plugin adds,
not the `CompileOptions` extension. Gradle's Kotlin DSL generates type-safe
accessors for configurations automatically, but extension **functions** on
`CompileOptions` are ordinary Kotlin extensions and need an explicit
`import net.ltgt.gradle.errorprone.errorprone`. Groovy's dynamic dispatch would
have found it at runtime; Kotlin fails at script-compile time. That is the
trade a typed build script makes, and it will recur on any plugin whose DSL
hangs off an existing Gradle type.

### Version lookup gotcha

Maven Central's `core=gav` query returns versions **unsorted**. It reported
ArchUnit 1.3.2 as newest when the real latest is 1.4.1. The `latestVersion`
field from the default query is the authoritative one.

### Not done here

`nodes/` (the MindGraph vault) is still untracked and was deliberately left out
of the commit — whether the vault is committed alongside the code is an open
question for the maintainer.