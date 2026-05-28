---
title: CI extra modules test gate
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# CI extra modules test gate

## Summary
CI can be false-green when module tests exist but are not wired into the main workflow. Extra module test jobs and coverage verification are required for modules that otherwise silently skip their tests.

## Key Points
- The 2026-05-28 audit confirmed that several module tests were written but not started by CI.
- A new CI job was added for sing-box and extra modules, which immediately surfaced hidden failures.
- `shared-warp-settings` is a Kotlin library module, so its correct Gradle task is `:shared-warp-settings:test`, not `testDebugUnitTest`.
- Desktop Compose coverage debt was not allowed to block Android release gating, but tests still remained runnable and reported.

## Details
The user asked whether existing tests were enough to guarantee URnetwork, ByeDPI, and sing-box regressions would not recur. The answer was that green CI could not prove runtime behavior, but CI could be strengthened by ensuring all relevant module tests actually run and that coverage gates are not vacuously green.

The new extra-module job found real hidden issues in `singbox-subscription`, `singbox-room`, `engine-masterdns`, `singbox-process`, `shared-warp-settings`, and `app-desktop`. This article specializes [[concepts/ci-module-test-coverage-gap]] and links to [[concepts/new-engine-module-ci-checklist]] because every new engine or library module must be explicitly registered in CI.

## Related Concepts
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/new-engine-module-ci-checklist]]
- [[concepts/junit-platform-silent-skip]]

## Sources
- [[daily/2026-05-28]]: Tests were confirmed to exist but not run for several sing-box and extra modules.
- [[daily/2026-05-28]]: Adding the new CI job surfaced hidden failures in extra module tests.
- [[daily/2026-05-28]]: `shared-warp-settings` required `:shared-warp-settings:test` because it is not an Android module.
