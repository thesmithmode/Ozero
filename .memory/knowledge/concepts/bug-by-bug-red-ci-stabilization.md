---
title: Red CI stabilization should proceed one confirmed bug at a time
sources:
  - daily/2026-06-03.md
created: 2026-06-13
updated: 2026-06-13
---
# Red CI stabilization should proceed one confirmed bug at a time
## Key Points
- When CI is red across several jobs, fix one confirmed cause before expanding the investigation.
- A review finding must be confirmed or rejected against code before it is treated as done.
- Mixing runtime fixes, test rewrites, lint changes, and coverage policy in one loop can prolong the red period.
- The target workflow is one bug, root-cause fix, regression test, CI signal, then the next bug.
## Details
The 2026-06-03 log shows a repeated user correction: broad hypothesis sweeps were not producing visible progress. The stabilized process was to take one confirmed problem at a time, starting with the three review findings around runtime startup acceptance, WARP raw INI preservation, and Singbox selected-profile fingerprint.

This is not a general ban on batch analysis. It is a rule for red-wave recovery when the same few areas keep failing and old artifacts are stale. The batch step is to map failures; the repair step should be narrow enough that the next CI result can be attributed. This connects [[ci-failure-batch-analysis-before-push]] with [[runtime-restart-watchdog-preflight-state-ownership]].
## Related Concepts
- [[ci-failure-batch-analysis-before-push]]
- [[runtime-restart-watchdog-preflight-state-ownership]]
- [[current-ci-run-grounding-before-fix]]
- [[coverage-gap-targeted-branch-remediation]]
## Sources
- `daily/2026-06-03.md`: user repeatedly required a one-bug-at-a-time workflow for confirmed review bugs and CI failures.
- `daily/2026-06-03.md`: recorded that mixing runtime restart, tests, lint, and coverage changes made the red CI period harder to close.
