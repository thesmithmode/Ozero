---
title: shared-warp-settings branch coverage gate
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# shared-warp-settings branch coverage gate

## Key Points
- `shared-warp-settings` is a Kotlin library module, so CI must run `:shared-warp-settings:test`, not Android `testDebugUnitTest`.
- Line coverage can pass while branch coverage still fails; the observed branch gap was about `0.91` against the `0.95` gate.
- Coverage fixes should add tests for parser, AWG builder, upper-bound validation, invalid numeric branches, and UI-state branches instead of lowering thresholds.
- This gate is part of the broader extra-module CI coverage problem described in [[concepts/ci-extra-modules-test-gate]] and [[concepts/ci-module-test-coverage-gap]].

## Details

During the 2026-05-28 CI hardening session, the newly wired extra-modules job first failed because `shared-warp-settings` was treated like an Android module. The correct Gradle task is `:shared-warp-settings:test`, because the module uses the Kotlin library convention rather than Android unit-test tasks.

After the task name was corrected, the module exposed a real coverage gap: line coverage was close enough, but branch coverage remained below the required threshold. The accepted fix was to add targeted tests for uncovered parser and validation branches, not to weaken the coverage rule. This keeps the module aligned with the repository-wide expectation that CI gates prove tests actually run and satisfy the 95%+ line/branch/function coverage contract.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/gradle-continue-full-failures]]
- [[connections/ci-false-green-vectors]]

## Sources
- [[daily/2026-05-28]] records the wrong `:shared-warp-settings:testDebugUnitTest` task, the correction to `:shared-warp-settings:test`, and the later branch coverage failure.
- [[daily/2026-05-28]] records that the fix added tests for parser, AWG, invalid numeric, upper-bound validation, and UI-state branches rather than lowering coverage thresholds.
