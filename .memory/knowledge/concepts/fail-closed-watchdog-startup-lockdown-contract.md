---
title: Fail-closed watchdog and startup lockdown contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Fail-closed watchdog and startup lockdown contract

## Summary
Runtime engine failures and startup failures must route through one fail-closed coordinator, and startup lockdown file descriptors must count as active blocking state until failure handling finishes safely.

## Key Points
- Runtime callbacks should go through `EngineWatchdogCoordinator.handleEngineFailure`, not direct engine-death shortcuts.
- `lockdownStartupFdRef` is part of fail-closed state while startup failure is unresolved.
- Startup and runtime failure paths need regression tests because green CI alone does not prove leak containment.
- Failure routing is a security boundary, not only lifecycle cleanup.
- This connects [[concepts/engine-failure-recovery-isolation]] with [[concepts/vpn-slot-conflict-detection]].

## Details
The 2026-05-31 whole-project review found that runtime failures could bypass the intended watchdog path by calling lower-level engine-death handling directly. That weakens fail-closed behavior because shutdown, routing lockdown, and recovery decisions no longer share one authority.

The same review found that the startup lockdown file descriptor needs explicit treatment as a blocking/fail-closed descriptor. Until startup failure handling completes safely, the descriptor is not just a transient implementation detail; it is part of the mechanism that prevents traffic from leaking during partial startup.

## Related Concepts
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/engine-poisoned-state-recovery-proof]]
- [[concepts/vpn-slot-conflict-detection]]
- [[connections/cascade-lifecycle-regressions-cross-engine-proof]]

## Sources
- [[daily/2026-05-31]]: session 20:48 records the finding that runtime failures bypass `EngineWatchdogCoordinator`.
- [[daily/2026-05-31]]: session 20:48 records the decision to route runtime callbacks through `EngineWatchdogCoordinator.handleEngineFailure`.
- [[daily/2026-05-31]]: session 20:48 records that `lockdownStartupFdRef` must be counted as fail-closed fd during startup failure paths.
