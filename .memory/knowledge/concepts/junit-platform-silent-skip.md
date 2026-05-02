---
title: "JUnit Platform Silent Test Skipping"
aliases: [useJUnitPlatform, junit-jupiter-silent-skip, gradle-zero-tests]
tags: [testing, gradle, junit, gotcha, ci]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# JUnit Platform Silent Test Skipping

When a Gradle module uses JUnit Jupiter (JUnit 5) test annotations but does not declare `useJUnitPlatform()` in `build.gradle.kts`, Gradle's default JUnit 4 runner silently skips all Jupiter tests. The build reports `BUILD SUCCESSFUL` with 0 tests executed. Coverage gates pass trivially on 0 tests, creating a fictional coverage report. Tests can exist for months without ever running.

## Key Points

- Missing `useJUnitPlatform()` in `tasks.withType<Test>` causes Gradle to use JUnit 4 vintage runner, which cannot discover `@Test` from `org.junit.jupiter.api`
- Gradle reports `BUILD SUCCESSFUL` with 0 tests — no warning, no error, no indication that tests were skipped
- Coverage gates (JaCoCo) on 0 tests produce 100% pass rate — a trivially true but meaningless result
- In Ozero, commit `98002fd` added `useJUnitPlatform()` to library modules, revealing 5+ pre-existing broken tests that had been silently skipped for ~3 months
- All revealed failures were test infrastructure issues (MockK native mocking, stale sentinels, unreachable paths), not production logic regressions

## Details

### The Silent Skip Mechanism

Gradle's test task defaults to the JUnit 4 test runner. JUnit Jupiter uses a different annotation namespace (`org.junit.jupiter.api.Test` vs `org.junit.Test`) and a different discovery mechanism (JUnit Platform). When `useJUnitPlatform()` is not configured, the test task scans for JUnit 4 `@Test` annotations, finds none in Jupiter test files, and completes successfully with zero tests.

This is particularly insidious because the developer experience suggests everything is working: the build passes, IDE runs individual tests (IDEs typically use their own test runners with Jupiter support), and CI shows green. The gap only becomes visible when someone checks the actual test count in CI output — a number that is easy to overlook in verbose Gradle logs.

### Discovery in Ozero

The Ozero project had JUnit Jupiter tests in multiple library modules (`:engines-core`, `:core-storage`, `:common-vpn`, `:common-dns`, `:common-crypto`) without `useJUnitPlatform()`. These tests were written over ~3 months, passed IDE-level testing, but never executed in CI. Commit `98002fd` added `useJUnitPlatform()` across all modules, immediately revealing failures:

- **ByeDpiEngineTest**: MockK cannot mock `native external fun` — requires refactored mock setup using relaxed mocks or wrapper interfaces
- **OzeroDatabaseMigrationTest**: Stale version sentinel `==4` needed relaxing to `>=4` as schema evolved
- **SubscriptionVerifierTest**: Tests targeting unreachable code paths (RFC 8032 empty message edge case) reframed to production paths
- **HevTunnelGateway, NativeHev, SentinelLogsRegression, HealthMonitor, LanRoutes**: Additional failures pending investigation

None of these were new bugs introduced by `98002fd` — they were latent test infrastructure problems masked by the silent skip.

### Prevention Pattern

Two rules prevent recurrence:

1. **Every module's `build.gradle.kts`** must include `tasks.withType<Test> { useJUnitPlatform() }` when using Jupiter
2. **CI must verify `N > 0` tests per module** — a `BUILD SUCCESSFUL` with 0 tests is a red flag, not a green signal

Both rules were codified in Ozero's global `CLAUDE.md` after this discovery.

## Related Concepts

- [[concepts/gradle-continue-full-failures]] - Companion CI discipline: show ALL failures per run, not just the first
- [[concepts/ci-workflow-discipline]] - CI-only testing means silent skips are invisible until module-level verification
- [[concepts/robolectric-room-migration-testing]] - One of the tests revealed as broken after useJUnitPlatform activation

## Sources

- [[daily/2026-05-01.md]] - Session 21:55: useJUnitPlatform() activation revealed 5+ latent broken tests across library modules; all failures = test infrastructure, not production logic
