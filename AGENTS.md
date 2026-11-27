# AGENTS.md

## Project boundaries

This is a Kotlin/JVM 21 plugin for IntelliJ IDEA with Haskell and Cabal file support and HLS integration through IntelliJ's built-in LSP client. Keep IDE compatibility aligned with `build.gradle.kts` and `gradle.properties`. Follow [docs/DESIGN.md](docs/DESIGN.md) for architecture; use the Gradle wrapper, not a separately installed Gradle.

## Workflow and verification

- Track scope, acceptance criteria, decisions and handoff in GitHub Issues. Link the Issue from a small draft PR. Do not use a parallel local task ledger.
- Run `./gradlew test buildPlugin verifyPlugin` (on Windows: `gradlew.bat test buildPlugin verifyPlugin`). For IDE integration changes, also record which IDE, host route (local, WSL or Dev Container) and HLS version were exercised. A successful plugin build does not prove those integrations work.
- Inherit the configured agent model rather than pinning a version here. Report commands and results; distinguish checks not run from passing checks. Publication to JetBrains Marketplace is separate from validation.

## Required verification for code changes

Use all three for affected application logic: mutation testing, formal verification, and property-based testing. Store measurable acceptance criteria and results in the Issue/PR. Start with HLS selection, launch configuration, and URI/host boundaries. Evaluate mutations (investigate surviving non-equivalent cases); give property failures a seed or counterexample; state the model/proof's assumptions and check how it maps to implementation tests. If any technique is missing or unrun, record the gap and keep the verification incomplete. Do not represent a bounded model check as an unbounded proof.
