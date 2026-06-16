---
title: Post-release CI gate and test harness feedback loop
sources:
  - daily/2026-06-01.md
created: 2026-06-01
updated: 2026-06-01
---
# Post-release CI gate and test harness feedback loop

## Summary
After a green release baseline, newly strengthened CI gates and sentinel tests can make `dev` red without proving a single runtime regression. The correct response is to map workflow, coverage, and test-harness changes together before continuing reactive fix-push cycles.

## Key Points
- A green release like `v1.0.13` does not prove newly added or strengthened tests are valid.
- When CI shifts from one app test failure to another, the failures may be independent test-harness bugs rather than one product regression.
- Coverage-gate changes, app-test additions, and workflow ordering must be compared together against the release baseline.
- `cancelled` runs with green upstream jobs are not valid green proof.
- After several failed fix-push cycles, stop and build an evidence map from `v1.0.13..dev`.

## Details
The June 1 session reached a point where all major jobs except `Tests - app` were green, but app failures moved from `EngineSettingsRestartObserverTest` to `UnifiedLoggerRotationVisibilityTest`. That movement showed that the red CI state was not necessarily one runtime defect. Some failures came from new or strengthened app sentinels, coroutine-test races, and a test helper that overwrote the marker it later expected to find after log rotation.

The user stopped the reactive cycle and asked why CI had not been red in release `v1.0.13`. The resulting rule is to compare workflows, coverage configuration, `app/src/test`, and relevant runtime/state-machine changes across `v1.0.13..dev` before more pushes. Each failure should be classified as runtime bug, changed contract, or test-harness bug before applying fixes.

This connection ties [[concepts/release-last-good-baseline-audit]] to [[concepts/runTest-backgroundscope-hot-flow-collectors]] and [[concepts/jacoco-honest-coverage-gate-boundary]]. It also supports [[connections/release-ci-green-vs-runtime-engine-proof]] by emphasizing that both green and red CI require interpretation against what changed.

## Related Concepts
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/runTest-backgroundscope-hot-flow-collectors]]
- [[concepts/jacoco-honest-coverage-gate-boundary]]
- [[connections/release-ci-green-vs-runtime-engine-proof]]

## Sources
- [[daily/2026-06-01]]: The log records that failures moved from `EngineSettingsRestartObserverTest` to `UnifiedLoggerRotationVisibilityTest`, implying multiple independent app-test failures.
- [[daily/2026-06-01]]: The user stopped further CI experiments and required a `v1.0.13..dev` comparison across app tests, workflows, coverage config, and related contracts.
