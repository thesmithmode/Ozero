---
title: ByeDPI wedged lane restart isolation
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-06-13
---
# ByeDPI wedged lane restart isolation

## Key Points
- A repeated ByeDPI start can fail because the old native/proxy lane remains wedged after stop timeout.
- Waiting for a stuck dispatcher can poison the shared orchestrator and block WARP, FPTN, and URnetwork starts.
- Restart recovery should rotate the lane and isolate stale native jobs instead of draining indefinitely.
- A generation guard prevents old proxy jobs from overwriting fresh runtime state.
- The regression test surface must include a stuck stop followed by a successful different-engine start.

## Details

The 2026-05-29 runtime logs showed the first ByeDPI launch working, then a repeat start hanging; after that, other engines failed to start until the app was fully restarted. The root was narrowed to the native/proxy lane and the shared lifecycle path rather than ByeDPI arguments or port parsing.

The accepted fix strategy was to rotate away from a wedged lane. `stop()` after timeout prepares a new lane, and a subsequent start must not wait for the old dispatcher to drain. A `proxyGeneration` guard keeps late completion from an old native job from resetting `activeSocksPort` or other state belonging to the new launch.

This pattern also protects shared orchestration. Without lane isolation, `ChainOrchestrator` can remain blocked or poisoned after `chainOrchestrator.stop hung > 4000ms`, causing false downstream failures in engines that never directly caused the original stuck stop.

The accepted ordering for the 2026-05-29 fix cycle was to isolate ByeDPI first, then continue to FPTN, URnetwork, and exit-node display. This prevented a stuck ByeDPI stop from being misdiagnosed as independent WARP, FPTN, or URnetwork failures.

## Related Concepts
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/engine-switch-failure-containment]]
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/byedpi-stale-serverfd-unconditional-forceclose]]
- [[connections/multi-engine-lifecycle-exitnode-regression-loop]]

## Sources
- [[daily/2026-05-29]]: repeated ByeDPI start failed after an initially successful launch and poisoned later module starts.
- [[daily/2026-05-29]]: commit `816a49c5 FIX: Исправление повторного запуска ByeDPI` rotated wedged lanes and added `proxyGeneration`.
- [[daily/2026-05-29]]: tests were added or updated for ByeDPI restart and `ChainOrchestrator` not blocking subsequent starts after hung stop.
- [[daily/2026-05-29]]: stale native jobs were identified as able to overwrite fresh state without a generation guard.
- [[daily/2026-05-29]]: the work order treated ByeDPI restart poisoning as the first shared-lifecycle fix before FPTN, URnetwork, and sing-box follow-ups.
