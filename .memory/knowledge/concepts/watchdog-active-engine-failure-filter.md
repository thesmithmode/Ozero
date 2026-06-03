---
title: Watchdog active engine failure filter
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Watchdog active engine failure filter

## Key Points
- Watchdog recovery must ignore stale or sidecar engine failures when they do not match the active tunnel engine.
- A stale failure can bypass `TunnelController.onEngineDied` if the watchdog acts before active-engine filtering.
- Regression tests should prove stale sidecar failure does not enable killswitch or stop the VPN.
- Active engine matching is part of fail-closed correctness, not only UI labeling.
- This refines [[concepts/engine-runtime-failclosed-watchdog-path]] and [[connections/stale-engine-signals-cross-engine-failures]].

## Details

On 2026-06-02, review confirmed a real bug in `EngineWatchdogCoordinator`: it could react to failure from an inactive engine. That path could trigger killswitch or `stopVpnRequest()` before the stale-signal guard in `TunnelController.onEngineDied` had a chance to filter the event.

The corrected rule is that watchdog failure handling must compare the failed engine with the active `TunnelState` engine. If the failure belongs to a stale attempt or sidecar engine, it must not stop the active VPN and must not enable killswitch.

This is especially important in a multi-engine VPN service where subprocess, sidecar, and previous attempt signals can outlive the active selection. Fail-closed behavior should protect real active traffic, not punish unrelated stale state.

## Related Concepts

- [[concepts/engine-runtime-failclosed-watchdog-path]]
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[connections/stale-engine-signals-cross-engine-failures]]
- [[connections/engine-startup-status-authority-boundary]]

## Sources

- [[daily/2026-06-02]]: Session 20:45 confirmed that watchdog reacted to inactive-engine failure.
- [[daily/2026-06-02]]: Session 20:45 decided `EngineWatchdogCoordinator` must ignore failures when the failed engine does not match active `TunnelState`.
- [[daily/2026-06-02]]: Session 20:45 added an action item for regression tests proving stale URnetwork sidecar failure during active WARP does not trigger killswitch or stop VPN.
