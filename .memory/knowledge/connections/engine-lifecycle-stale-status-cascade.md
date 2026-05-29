---
title: Engine lifecycle stale status cascade
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# Engine lifecycle stale status cascade

## Key Points
- A visible engine failure can be caused by stale lifecycle events from another engine.
- Long FPTN auth ladders, wedged ByeDPI stops, and URnetwork startup waits all stress the same orchestration boundary.
- `ChainOrchestrator` mutex serialization makes long start/stop paths system-wide, not engine-local.
- Terminal UI states must be tied to the current candidate/run, not only to the last emitted engine label.
- Regression tests need cross-engine sequences, not only single-engine happy paths.

## Details

The 2026-05-29 investigations connected several symptoms that initially looked engine-specific. `Failed(BYEDPI, timeout)` often appeared after unsuccessful FPTN cycles rather than during a direct ByeDPI start. ByeDPI repeated-start failures then poisoned the shared lifecycle so WARP, FPTN, and URnetwork could fail afterward. URnetwork's long startup wait similarly blocked `onEngineStarted()` and delayed recovery.

The non-obvious connection is that all of these failures cross the same orchestration layer. A long or wedged operation under shared start/stop serialization can let stale callbacks, terminal failures, or reset logic apply to the wrong active engine. This makes the UI label misleading: the engine shown as failed may be the victim of stale state rather than the source of the root defect.

The durable invariant is that terminal failure signals must be scoped to the current chain, candidate, and generation. Intermediate auto-candidate failures should not surface as final UI failures, and late callbacks from previous engines must not mutate the current runtime state.

## Related Concepts
- [[concepts/byedpi-wedged-lane-restart-isolation]]
- [[concepts/fptn-upstream-dns-websocket-boundary]]
- [[concepts/urnetwork-startup-readiness-vs-runtime-peer-grace]]
- [[concepts/engine-switch-failure-containment]]
- [[concepts/engine-poisoned-state-recovery-proof]]

## Sources
- [[daily/2026-05-29]]: `Failed(BYEDPI, timeout)` was repeatedly observed after FPTN attempts rather than a direct ByeDPI start.
- [[daily/2026-05-29]]: `ChainOrchestrator` mutex serialization made long FPTN start and wedged ByeDPI stop affect other engines.
- [[daily/2026-05-29]]: stale `Probing(engineId)` could overwrite state for a different current runtime.
- [[daily/2026-05-29]]: final fix plans required candidate/run identifiers, generation guards, and cross-engine regression scenarios.
