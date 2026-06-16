---
title: Android JaCoCo executionData false green
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---
# Android JaCoCo executionData false green

## Key Points
- Android JaCoCo verification can be false-green when `executionData` points at an obsolete AGP output path.
- Real unit-test `.exec` data may live under `build/jacoco/testDebugUnitTest.exec`.
- N>0 tests prove tests ran, but do not prove the module is included in the intended coverage gate.
- CI should include `app-desktop`, `buildSrc`, and an explicit decision for instrumentation UI tests.
- Coverage proof must combine real execution data, module inclusion, and N>0 test discovery; any one signal alone can be false confidence.

## Details

On 2026-05-31, a CI/test read-only review found that the Android JaCoCo gate likely used an old `outputs/unit_test_code_coverage/...` path while the actual execution data lived under `build/jacoco/testDebugUnitTest.exec`. That makes coverage verification suspect because the gate can pass or check empty/missing data instead of real executed tests.

The same review separated several coverage concerns. N>0 test XML gates are necessary but not sufficient; they confirm that a module produced tests, not that every required module participates in CI or coverage verification. `app-desktop`, `buildSrc`, and instrumentation/UI tests need explicit gate decisions rather than accidental omission.

This is why coverage gate fixes should be reviewed as a system: execution data paths, included modules, branch/function thresholds, and test discovery all have to point at the same executed suite. A green coverage task that consumes stale data is as misleading as a green N>0 task that excludes a changed module.

## Related Concepts
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/dev-push-ci-visible-full-run-contract]]
- [[concepts/app-desktop-coverage-gate-scope]]
- [[concepts/shared-warp-settings-branch-coverage]]
- [[connections/coverage-artifact-policy-feedback-loop]]

## Sources
- [[daily/2026-05-31]]: Session 20:48 records the suspected stale Android JaCoCo `executionData` path.
- [[daily/2026-05-31]]: Session 20:48 records missing or incomplete gates for `app-desktop`, `buildSrc`, and instrumentation/UI tests.
- [[daily/2026-05-31]]: Session 20:48 records that N>0 verification does not prove inclusion in the CI gate.
- [[daily/2026-05-31]]: Sessions 20:48 and 20:57 record CI coverage as a release trust boundary tied to fail-closed and security review priorities.
