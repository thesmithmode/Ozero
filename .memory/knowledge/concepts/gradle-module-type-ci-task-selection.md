---
title: Gradle module type CI task selection
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Gradle module type CI task selection

## Key Points
- CI wiring must choose Gradle tasks from the module type, not from naming assumptions.
- A Kotlin library module uses `:module:test`; Android-only tasks such as `testDebugUnitTest` are invalid there.
- Adding skipped modules to CI should start with module classification, then test and coverage task wiring.
- Wrong task selection can create a false infrastructure failure before real hidden test failures are visible.

## Details
The extra-modules CI expansion exposed a concrete task-selection error: `shared-warp-settings` is wired as an `ozero.kotlin.library` module, so the correct test task is `:shared-warp-settings:test`, not `:shared-warp-settings:testDebugUnitTest`. The failure was not a product regression, but it blocked the gate before the newly enabled tests could reveal real stale fakes and coverage gaps.

This is a CI design rule for future module onboarding. Before adding a module to a grouped job, inspect its Gradle plugin and available task surface, then wire the matching test and coverage tasks. It complements [[concepts/ci-extra-modules-test-gate]] and [[concepts/shared-warp-settings-branch-coverage]]: the gate is only meaningful if the task exists, executes tests with N>0, and reports coverage for the intended module.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/shared-warp-settings-branch-coverage]]
- [[concepts/ci-module-test-coverage-gap]]
- [[connections/ci-extra-gate-latent-failures]]

## Sources
- [[daily/2026-05-28]]: CI run `26583636181` failed because `:shared-warp-settings:testDebugUnitTest` was selected for a Kotlin library module.
- [[daily/2026-05-28]]: the fix changed the task to `:shared-warp-settings:test`.
- [[daily/2026-05-28]]: after the task wiring fix, extra-module CI exposed real hidden failures and branch-coverage gaps.
