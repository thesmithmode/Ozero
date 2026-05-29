---
title: Chain start timeout stale engine failure cascade
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Chain start timeout stale engine failure cascade

## Key Points
- A terminal UI failure can be attributed to the wrong engine when stale callbacks arrive after a chain transition.
- `Failed(BYEDPI, timeout)` was treated as a possible secondary symptom of FPTN startup/orchestration stalls.
- Auto-candidate failures must be correlated by attempt identity before publishing terminal status.
- Stop/reset paths must be idempotent so late callbacks cannot trigger another stop or overwrite current state.

## Details
The 2026-05-29 runtime investigation found that ByeDPI failures after an FPTN cycle were not necessarily ByeDPI root failures. Logs showed `Failed(BYEDPI, timeout)` appearing around failed or overlapping FPTN startup work rather than a clear current ByeDPI start. This made stale callback handling and engine-status ownership a primary diagnostic target.

The resulting invariant is that chain state must distinguish event source from currently displayed engine label. A non-terminal candidate failure, an old `onEngineDied`, or a delayed stop timeout must not be able to publish a terminal failure for the active UI engine unless it matches the current attempt/session identity.

## Related Concepts
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[connections/stale-engine-signals-cross-engine-failures]]
- [[connections/engine-lifecycle-stale-status-cascade]]
- [[concepts/byedpi-wedged-lane-generation-restart]]

## Sources
- [[daily/2026-05-29]] records repeated analysis that `Failed(BYEDPI, timeout)` can be a stale/cross-engine signal after FPTN activity.
- [[daily/2026-05-29]] records the plan to add attempt identity/correlation and protect reset/stop from stale callbacks.
