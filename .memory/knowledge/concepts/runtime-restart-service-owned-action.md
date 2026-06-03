---
title: Runtime restart service owned action
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Runtime restart service owned action

## Key Points
- Runtime config restart should be a single VPN service action, not separate app-level stop/start foreground-service intents.
- The running service should perform stop without `stopSelf`, restore foreground state, and then start VPN again.
- Restart completion should wait for real startup or terminal state, not only enqueue a start request.
- Baseline fingerprints should advance only after restart success.
- This links [[concepts/runtime-restart-application-scope-observer]] with [[concepts/settings-restart-baseline-debounce-state-machine]].

## Details

The 2026-06-02 runtime restart fix moved ownership into `OzeroVpnService`. Instead of an app-level coordinator sending separate stop and start intents through `startForegroundService`, the coordinator should send a single `ACTION_RESTART_RUNTIME_CONFIG`. The service then performs the restart inside the already-running foreground service.

This is necessary because `performShutdown(callStopSelf=false)` can still remove the foreground notification. A service-owned restart must restore foreground state before calling `startVpn()` again. It must also abort if an external user stop is already in flight, so a background config update cannot cancel a deliberate user stop.

Restart success is also a state-machine issue. Completing after an `ACTION_START` enqueue creates a baseline race: the fingerprint may be marked applied before `StartSequenceCoordinator` actually enters `Probing`, `Connecting`, `Connected`, or `Failed`. The restart callback therefore needs a success/failure result, and the baseline must update only after a successful restart.

## Related Concepts

- [[concepts/runtime-restart-application-scope-observer]]
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/runtime-restart-pending-fingerprint-baseline]]
- [[concepts/vpn-switch-confirm-stop-before-start]]

## Sources

- [[daily/2026-06-02]]: Session 20:45 decided to replace separate app-level stop/start foreground-service intents with a single service action `ACTION_RESTART_RUNTIME_CONFIG`.
- [[daily/2026-06-02]]: Session 20:45 recorded that service-side restart must restore foreground before internal `startVpn()`.
- [[daily/2026-06-02]]: Session 21:12 decided that baseline fingerprint advances only after successful restart and that in-flight user stop aborts restart.
