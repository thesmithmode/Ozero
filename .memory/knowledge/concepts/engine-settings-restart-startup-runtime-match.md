---
title: Startup snapshot acceptance must match runtime-affecting settings
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-13
---
# Startup snapshot acceptance must match runtime-affecting settings
## Key Points
- Startup acceptance cannot rely only on the target engine matching the current engine.
- If runtime-affecting fields change during startup, the observer must require a restart or reject the snapshot.
- This prevents stale TUN, DNS, IPv6, and traffic-mode state from being promoted as already applied.
- The accepted snapshot check must compare runtime fields against the previous startup snapshot, not just against the current engine identity.
- The review tied the fix to `EngineSettingsRestartObserver` and regression tests around DNS, IPv6, traffic mode, and BYEDPI args.
## Details
The daily log describes a bug where a startup snapshot could be treated as applied even though the runtime configuration changed in flight. The problem was not the engine identity itself; the problem was that state observed during startup could diverge from the runtime-affecting fields that actually shaped the active tunnel.

The correct boundary is therefore semantic equality over the fields that matter to runtime behavior. If those fields differ, startup should not be considered complete. The result is a restart or a queued retry instead of a false acceptance. This idea is closely related to [[runtime-restart-application-scope-observer]] and [[settings-restart-baseline-debounce-state-machine]].

The 2026-06-03 review narrowed the implementation rule: `startupAcceptedSnapshot` may be accepted only when `snapshot.sameRuntimeFor(currentEngine, trigger.previous)` or an equivalent runtime-field comparison holds. A target-engine match alone is insufficient because startup auto-fallback can change the visible engine while the active TUN/config was already built from an older snapshot. That makes this a runtime state-machine invariant rather than a UI-label cleanup.
## Related Concepts
- [[runtime-restart-application-scope-observer]]
- [[runtime-restart-service-owned-action]]
- [[settings-restart-baseline-debounce-state-machine]]
- [[engine-runtime-config-restart-observer-stateflow-tests]]
- [[runtime-config-restart-boundary-loop]]
## Sources
- `daily/2026-06-03.md`: stated that `EngineSettingsRestartObserver` had to stop accepting startup snapshots when runtime-affecting fields changed during in-flight startup.
- `daily/2026-06-03.md`: recorded the chosen fix of comparing runtime-affecting fields before accepting startup fallback as already applied.
