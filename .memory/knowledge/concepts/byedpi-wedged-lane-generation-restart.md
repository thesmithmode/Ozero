---
title: ByeDPI wedged lane generation restart
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# ByeDPI wedged lane generation restart

## Key Points
- Repeated ByeDPI starts can fail when a stuck native or proxy job poisons the next lifecycle lane.
- Waiting for the old dispatcher is insufficient when the lane is wedged.
- Rotating the lane after stop timeout isolates the next start from stale native state.
- A generation guard prevents old jobs from clearing fresh runtime state.
- This extends [[concepts/byedpi-stop-timeout-contract]] and [[concepts/byedpi-stale-serverfd-unconditional-forceclose]].

## Details

The daily log identified a concrete runtime pattern: first ByeDPI start succeeds, the second start hangs or fails, and then other engines cannot start until the app is fully restarted. The root was not command-line arguments or protocol configuration. It was poisoned lifecycle state in the native/proxy lane, visible through orchestrator hangs and later WARP/FPTN/URnetwork failures.

The selected fix was to rotate the ByeDPI lane when `proxyJob` is wedged, instead of waiting for the old dispatcher to drain. `stop()` must prepare a fresh lane after timeout so the next start can proceed. A `proxyGeneration` guard prevents a late completion from an old native job from resetting `activeSocksPort` or overwriting the state of a newer start.

This is a stronger invariant than simply lengthening stop timeouts. It treats wedged native work as an isolated stale lane and requires a bounded cleanup path. The log records commit `816a49c5 FIX: Исправление повторного запуска ByeDPI` as the implementation point.

## Related Concepts
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/byedpi-stale-serverfd-unconditional-forceclose]]
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/engine-switch-failure-containment]]

## Sources
- [[daily/2026-05-29]]: Repeated ByeDPI start failed after a first successful start and blocked other engines.
- [[daily/2026-05-29]]: Commit `816a49c5` introduced lane rotation, stop-timeout lane preparation, and `proxyGeneration`.
- [[daily/2026-05-29]]: Tests were added or updated for ByeDPI restart and ChainOrchestrator non-blocking behavior after hung stop.
