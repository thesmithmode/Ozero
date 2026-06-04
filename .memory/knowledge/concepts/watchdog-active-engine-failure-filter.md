---
title: "Engine watchdog must ignore stale failures for inactive engines"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# Engine watchdog must ignore stale failures for inactive engines
## Key Points
- Watchdog recovery should only react to failures from the engine that is currently active in tunnel state.
- Failures from stale, sidecar, or otherwise inactive attempts must be ignored.
- A stale failure can otherwise trigger the wrong recovery path or overwrite the active engine's state.
- This guard is part of the broader stale-signal isolation problem across engine lifecycle events.
## Details
The daily log describes a bug class where a watchdog receives a failure event from an engine that is not the one currently running the tunnel. If that event is treated as authoritative, the system can stop the wrong session, enable a killswitch unnecessarily, or publish a terminal failure for a tunnel that is still healthy.

The fix is to compare the failing engine against the active tunnel state before performing recovery. This preserves state ownership boundaries and keeps watchdog handling aligned with [[connections/stale-engine-signals-cross-engine-failures]] and [[concepts/engine-lifecycle-stale-status-cascade]].
## Related Concepts
- [[connections/stale-engine-signals-cross-engine-failures]]
- [[concepts/engine-lifecycle-stale-status-cascade]]
- [[connections/runtime-restart-watchdog-preflight-state-ownership]]
## Sources
- `daily/2026-06-02.md`: the log states that watchdog recovery must ignore stale or sidecar failures whose engine does not match the active tunnel state.
