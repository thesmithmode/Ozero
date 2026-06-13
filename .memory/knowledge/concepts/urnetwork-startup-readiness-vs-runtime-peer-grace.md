---
title: URnetwork startup readiness versus runtime peer grace
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork startup readiness versus runtime peer grace

## Key Points
- URnetwork `peers=0` is not automatically a startup failure.
- A long `awaitReady()` gate before `onEngineStarted()` blocks watchdog and recovery.
- Startup readiness should be short and based on attach/connect signals.
- The 5-minute `0 peers` grace belongs in runtime health handling after the engine has started.
- `providerStateAdded` and `CONNECTED` are stronger readiness signals than peer count alone.

## Details

The 2026-05-29 logs showed URnetwork stuck at `CONNECTING peers=0 deadline=300000ms`. Comparing with `v0.2.0` revealed that the long 300-second readiness wait had moved into startup. That blocked `TunnelController.onEngineStarted()`, so watchdog and runtime recovery never began, even though the SDK could continue connecting in the background.

The corrected model separates startup from runtime health. Startup should confirm that the tunnel has been attached and connection work has actually been issued, using signals such as `tunnelStarted`, `connectIssued`, `providerStateAdded`, `CONNECTED`, and peers when available. It should not wait five minutes for peer count.

The long `0 peers` tolerance is still useful, but only after the engine is considered started. At that point `EngineWatchdogCoordinator` can observe runtime degradation without freezing the lifecycle. This preserves user-visible progress while allowing URnetwork to continue searching for peers.

## Related Concepts
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/engine-poisoned-state-recovery-proof]]
- [[concepts/health-monitor-p2p-mismatch]]

## Sources
- [[daily/2026-05-29]]: URnetwork logs showed `awaitReady status=CONNECTING peers=0 deadline=300000ms`.
- [[daily/2026-05-29]]: the 5-minute wait was moved out of startup and into runtime watchdog behavior.
- [[daily/2026-05-29]]: `providerStateAdded` from `windowStatus` was identified as a stronger readiness signal.
- [[daily/2026-05-29]]: commit `310e86c4 FIX: Исправление готовности URnetwork` implemented the short startup gate and runtime peer grace.
