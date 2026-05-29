---
title: "ByeDPI wedged lane generation restart"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# ByeDPI wedged lane generation restart
## Key Points
- ByeDPI repeated-start failures were traced to wedged native/proxy state that could poison later starts and other engines.
- A timed-out `stop()` must rotate to a new lane instead of waiting indefinitely for the old dispatcher/native job.
- `proxyGeneration` is required so a stale native job cannot clear or overwrite state from a newer successful start.
- The fix was pushed to `dev` as `816a49c5 FIX: Исправление повторного запуска ByeDPI`.
## Details
The 2026-05-29 logs showed a concrete runtime pattern: first ByeDPI start worked, but the second start could hang; after that, WARP/FPTN/URnetwork also failed until the app was fully restarted. This made ByeDPI look like an isolated engine failure, but the broader symptom was a poisoned shared lifecycle state.

The accepted fix direction was to isolate wedged lanes. If the existing `proxyJob` or native lane cannot stop within the bounded timeout, the next start should not wait on that stale dispatcher. Instead, the engine prepares a new lane and uses a generation guard so old completion callbacks cannot mutate `activeSocksPort` or other fresh state. This is closely related to [[concepts/byedpi-wedged-lane-restart-isolation]] and to the cross-engine lifecycle rule in [[connections/cascade-lifecycle-regressions-cross-engine-proof]].

The fix is intentionally not a protocol/argument change. Existing ByeDPI command behavior such as verbatim CMD mode remains governed by [[concepts/byedpi-cmd-verbatim-pipeline]]. The restart problem belongs to lifecycle isolation, stop timeout handling, and stale job ownership.
## Related Concepts
- [[concepts/byedpi-wedged-lane-restart-isolation]] - Same family of native/proxy lane isolation.
- [[concepts/byedpi-stop-timeout-contract]] - Explains why stop timeout is part of the engine contract.
- [[connections/cascade-lifecycle-regressions-cross-engine-proof]] - Shows why ByeDPI poisoned state can surface in other engines.
- [[concepts/engine-poisoned-state-recovery-proof]] - Requires proving recovery in the same process after a failed engine.
## Sources
- [[daily/2026-05-29]]: records repeated ByeDPI start failure after first success and cross-engine failures until app restart.
- [[daily/2026-05-29]]: records the decision to rotate wedged lane, avoid draining the old dispatcher, and add `proxyGeneration`.
- [[daily/2026-05-29]]: records commit `816a49c5` as the pushed ByeDPI restart fix.
