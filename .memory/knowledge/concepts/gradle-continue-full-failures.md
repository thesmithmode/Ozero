---
title: "Gradle --continue for Full Failure Visibility"
aliases: [gradle-continue, fail-fast-trap, full-failure-visibility]
tags: [gradle, ci, testing, process]
sources:
  - "daily/2026-05-01.md"
created: 2026-05-01
updated: 2026-05-01
---

# Gradle --continue for Full Failure Visibility

Gradle's default fail-fast behavior stops execution at the first task failure. In a multi-module Android project, this means a single broken test in one module hides all failures in other modules. The `--continue` flag forces Gradle to execute all tasks regardless of failures, producing a complete list of issues in a single CI run. Without it, fixing CI becomes an iterative push→fail→fix→push→next-fail loop that can take hours.

## Key Points

- Gradle default: first task failure stops the entire build — remaining modules are never tested
- `--continue` flag: execute all tasks, report all failures at the end — one CI run shows everything
- Applied to both `test` and `style` (ktlint/detekt) steps in `ci.yml`
- Critical for multi-module projects: Ozero has 9+ Gradle modules, each with independent test tasks
- Reduces the CI feedback loop from potentially N sequential push-fix cycles to exactly 1

## Details

### The Fail-Fast Problem

In a typical Gradle multi-module build, the test task runs across all modules sequentially (or in parallel, depending on configuration). When module A's tests fail, Gradle aborts immediately. Modules B through I are never tested. The developer fixes module A, pushes, waits for CI, and discovers module B also fails. This cycle repeats until all modules pass — each iteration costing 10-15 minutes of CI time plus context switching.

For Ozero, this became acute when `useJUnitPlatform()` was added to all library modules, revealing latent test failures in at least 6 modules simultaneously. Without `--continue`, discovering all 6+ failures would have required 6+ sequential CI runs.

### Implementation

The fix adds `--continue` to the Gradle command in `ci.yml`:

```yaml
- name: Run tests
  run: ./gradlew test --continue

- name: Run style checks
  run: ./gradlew ktlintCheck detekt --continue
```

This applies to both test execution and style checking. Style violations in one module no longer prevent test execution in others.

### Complementary Rule: N > 0 Tests

`--continue` alone is insufficient. A module with 0 tests still reports success (see [[concepts/junit-platform-silent-skip]]). The complete CI discipline requires both:

1. `--continue` — see all failures at once
2. Verify `N > 0` tests per module — catch silent skips

Both rules were added to Ozero's global `CLAUDE.md` as mandatory CI practices.

### Applicability Beyond Gradle

The same principle applies to other test runners:
- pytest: `--maxfail=0` (default) already continues on failure; `--tb=short` for compact output
- Jest: `--bail=false` (default continues)
- Go: `go test ./...` continues across packages by default

The lesson is universal: CI must show ALL broken surfaces in one run.

## Related Concepts

- [[concepts/junit-platform-silent-skip]] - The companion problem: tests exist but don't run; --continue shows failures only for tests that actually execute
- [[concepts/ci-workflow-discipline]] - CI-only testing workflow where --continue is essential for fast feedback

## Sources

- [[daily/2026-05-01.md]] - Session 21:55: `--continue` added to ci.yml test/style steps after discovering that fail-fast hid failures across 6+ modules
