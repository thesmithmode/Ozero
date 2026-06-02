---
title: Coverage gate vs test harness validity loop
sources:
  - daily/2026-06-01.md
created: 2026-06-02
updated: 2026-06-02
---

# Coverage gate vs test harness validity loop

## Key Points
- A strict coverage gate is useful only when its measured surface is honest and its sentinel tests are deterministic.
- Broad exclusions can hide production risk, while flawed tests can turn a valid gate into noise.
- Post-release CI recovery needs both coverage-boundary review and test-harness validation.
- The loop closes only when CI reaches terminal status with real tests, nonzero coverage, and no known flaky sentinels.

## Details

The 2026-06-01 sessions connected two issues that are easy to treat separately: honest JaCoCo boundaries and app test determinism. Removing coverage verification or hiding production classes gives a false green. But leaving invalid or race-prone tests in the gate also creates red CI that does not represent product risk.

Ozero's recovery path therefore needs a dual proof: keep `jacocoTestCoverageVerification` active with narrow evidence-based excludes, and audit failing sentinel tests for realistic fixtures, deterministic coroutine scheduling, and helper correctness. `EngineSettingsRestartObserverTest`, `EngineRuntimeConfigRestartObserverTest`, and `UnifiedLoggerRotationVisibilityTest` each showed how a strict gate can be blocked by test-harness mistakes if the harness does not match production behavior.

## Related Concepts
- [[concepts/jacoco-exclude-evidence-boundary]]
- [[concepts/post-release-app-test-harness-regression-map]]
- [[concepts/engine-runtime-config-restart-observer-stateflow-tests]]
- [[concepts/ci-failure-batch-analysis-before-push]]

## Sources
- [[daily/2026-06-01]]: Sessions at 02:41, 10:02, and 10:47 record the coverage gate restoration and exclusion-boundary decisions.
- [[daily/2026-06-01]]: Sessions at 21:53, 21:56, and 22:36 record app test-harness failures and the need to classify failures before more pushes.
