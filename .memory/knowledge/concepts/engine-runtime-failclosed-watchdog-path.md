---
title: Engine runtime fail-closed watchdog path
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Engine runtime fail-closed watchdog path

## Key Points
- Runtime engine failures must flow through `EngineWatchdogCoordinator.handleEngineFailure`.
- Direct `TunnelController.onEngineDied` calls can bypass fail-closed policy and recovery ownership.
- `lockdownStartupFdRef` must count as a blocking fail-closed fd until startup failure is safely resolved.
- Review priority is runtime safety first, then secrets, then CI proof fidelity.

## Details

The read-only review on 2026-05-31 found critical runtime safety risks: runtime failures could bypass `EngineWatchdogCoordinator`, and the startup lockdown file descriptor was not treated as part of fail-closed state. The decision was that runtime callbacks should route through the watchdog/coordinator instead of direct controller death handling.

The important boundary is ownership. `TunnelController` can represent tunnel state, but engine failure policy, fail-closed routing, and recovery sequencing need a single coordinator path. Startup is also part of the contract: if a lockdown fd is created during startup, it remains security-relevant until the failure path has safely closed or replaced it.

## Related Concepts
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/engine-switch-failure-containment]]
- [[concepts/vpn-switch-confirm-stop-before-start]]
- [[connections/runtime-engine-fix-ci-proof-loop]]

## Sources
- [[daily/2026-05-31]]: Session 20:48 records that runtime failures bypassed `EngineWatchdogCoordinator`.
- [[daily/2026-05-31]]: Session 20:48 records that `lockdownStartupFdRef` was not accounted as fail-closed fd.
- [[daily/2026-05-31]]: Session 20:48 records the prioritization of fail-closed routing before security storage and CI coverage fidelity.
