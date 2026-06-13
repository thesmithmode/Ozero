---
title: Current CI run must be grounded before fixing red jobs
sources:
  - daily/2026-06-03.md
created: 2026-06-13
updated: 2026-06-13
---
# Current CI run must be grounded before fixing red jobs
## Key Points
- Fixes must target the current failed run and commit SHA, not a stale local artifact.
- Local CI logs are useful fallback evidence only when their run id and commit match the active red state.
- Old detekt findings can remain in saved logs after the code has already been refactored.
- When GitHub CLI access is blocked or slow, artifact freshness has to be stated explicitly before acting.
## Details
The 2026-06-03 log repeatedly shows stale or incomplete CI artifacts pulling diagnosis in the wrong direction. Earlier saved detekt logs pointed to `EngineSettingsRestartObserverTest` as a `LargeClass`, but the class had already been split in the current branch. Treating that log as current would have produced a redundant or harmful fix.

The operational rule is to bind every red-job diagnosis to a run id, workflow event, commit SHA, and concrete failure text. Local artifacts can still be the best available source when `gh` is blocked by sandbox or access issues, but they need freshness checks before they become evidence. This is the practical boundary between [[ci-fresh-run-authority-contract]] and [[ci-snapshot-artifact-failure-grounding]].
## Related Concepts
- [[ci-fresh-run-authority-contract]]
- [[ci-snapshot-artifact-failure-grounding]]
- [[ci-artifact-report-driven-debugging]]
- [[dev-ci-kotlin-style-cascade]]
## Sources
- `daily/2026-06-03.md`: multiple sessions noted that local CI artifacts could be stale or incomplete and had to be matched against the current `dev` SHA before fixing.
- `daily/2026-06-03.md`: recorded that a prior `LargeClass` detekt signal became invalid after the test class was already split.
