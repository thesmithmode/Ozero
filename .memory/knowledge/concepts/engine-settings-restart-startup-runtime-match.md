---
title: Startup snapshot acceptance must match runtime-affecting settings
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Startup snapshot acceptance must match runtime-affecting settings
## Key Points
- Startup acceptance cannot rely only on the target engine matching the current engine.
- If runtime-affecting fields change during startup, the observer must require a restart or reject the snapshot.
- This prevents stale TUN, DNS, IPv6, and traffic-mode state from being promoted as already applied.
- The log tied the fix to `EngineSettingsRestartObserver` review findings.
## Details
The daily log describes a bug where a startup snapshot could be treated as applied even though the runtime configuration changed in flight. The problem was not the engine identity itself; the problem was that state observed during startup could diverge from the runtime-affecting fields that actually shaped the active tunnel.

The correct boundary is therefore semantic equality over the fields that matter to runtime behavior. If those fields differ, startup should not be considered complete. The result is a restart or a queued retry instead of a false acceptance. This idea is closely related to [[runtime-restart-application-scope-observer]] and [[settings-restart-baseline-debounce-state-machine]].
## Related Concepts
- [[runtime-restart-application-scope-observer]]
- [[runtime-restart-service-owned-action]]
- [[settings-restart-baseline-debounce-state-machine]]
- [[engine-runtime-config-restart-observer-stateflow-tests]]
## Sources
- `daily/2026-06-03.md`: stated that `EngineSettingsRestartObserver` had to stop accepting startup snapshots when runtime-affecting fields changed during in-flight startup.
