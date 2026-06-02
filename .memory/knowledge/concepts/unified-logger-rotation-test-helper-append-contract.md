---
title: Unified logger rotation test helper append contract
sources:
  - daily/2026-06-01.md
created: 2026-06-02
updated: 2026-06-02
---

# Unified logger rotation test helper append contract

## Key Points
- A log rotation test helper must preserve pre-rotation markers when the assertion expects them in the rotated file.
- Using `writeText(...)` inside `forceRotation()` can erase the marker that the test later expects in `.prev`.
- The helper should append rotation-forcing data when the test contract is about visibility across rotation.
- A shifted CI failure can reveal a test helper bug after earlier app-test races are fixed.

## Details

`UnifiedLoggerRotationVisibilityTest` exposed a test-harness bug after `EngineSettingsRestartObserverTest` was stabilized. The helper `forceRotation()` wrote new content with `writeText(...)`, which replaced the file contents and removed `PRE-ROTATION-MARKER`. The test then expected that marker to be visible in the rotated `.prev` file, so the helper contradicted the scenario it was asserting.

The fix principle is not to weaken the assertion, but to make the fixture preserve the precondition. Rotation helpers that need to cross the threshold should append bytes or otherwise keep existing content intact when the test proves marker visibility across rotation.

## Related Concepts
- [[concepts/code-quality-review-proof-standard]]
- [[concepts/test-tautology-always-green]]
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/post-release-app-test-harness-regression-map]]

## Sources
- [[daily/2026-06-01]]: Sessions at 21:53 and 21:56 identify `UnifiedLoggerRotationVisibilityTest` as a test helper bug where `writeText(...)` erased `PRE-ROTATION-MARKER`.
- [[daily/2026-06-01]]: Session at 21:56 records that the failure appeared after restart observer fixes, indicating multiple independent app-test issues.
