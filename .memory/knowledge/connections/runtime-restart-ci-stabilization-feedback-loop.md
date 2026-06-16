---
title: Runtime restart fixes and CI gates can amplify each other during stabilization
sources:
  - daily/2026-06-03.md
created: 2026-06-13
updated: 2026-06-13
---
# Runtime restart fixes and CI gates can amplify each other during stabilization
## Key Points
- The 2026-06-03 red wave combined runtime restart state-machine changes with detekt and JaCoCo gate work.
- Runtime fixes changed behavior in critical lifecycle code, while stricter gates exposed style and coverage debt.
- Stale CI artifacts made it easy to fix yesterday's failure instead of the current red reason.
- Stabilization needs a loop of current-run grounding, one confirmed bug fix, regression proof, and then the next gate.
## Details
The non-obvious connection is that the red CI period was not caused by a single module. Runtime restart changes touched observer, coordinator, watchdog, startup snapshot, and service restart semantics. At the same time, `detekt` and JaCoCo enforcement surfaced complex methods, large tests, and uncovered parser branches. Each successful fix could expose the next gate, making progress look like churn.

The log's durable lesson is sequencing. First, ground the current red run; second, decide whether the active failure is runtime behavior, style, or coverage; third, make a narrow fix with a regression test or structural lint repair; fourth, wait for the next terminal CI signal. This combines [[bug-by-bug-red-ci-stabilization]], [[current-ci-run-grounding-before-fix]], and [[engine-settings-restart-startup-runtime-match]].
## Related Concepts
- [[bug-by-bug-red-ci-stabilization]]
- [[current-ci-run-grounding-before-fix]]
- [[engine-settings-restart-startup-runtime-match]]
- [[singbox-subscription-branch-coverage-edges]]
## Sources
- `daily/2026-06-03.md`: described the long red period as interaction between runtime restart fixes and stricter detekt/Jacoco gates.
- `daily/2026-06-03.md`: recorded the stabilization strategy of not mixing functional fixes, coverage policy, and lint repairs in the same diagnostic loop.
