---
title: URnetwork startup readiness and runtime peer grace
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# URnetwork startup readiness and runtime peer grace

## Key Points
- A long `awaitReady()` timeout before `onEngineStarted()` blocks URnetwork lifecycle and recovery.
- `peers=0` is not a startup failure by itself; it needs runtime grace and recovery.
- Startup readiness should use attach/connect signals such as `tunnelStarted`, `connectIssued`, `CONNECTED`, and `providerStateAdded`.
- The 5-minute no-peer window belongs in runtime watchdog, not startup gate.
- This updates the interpretation of [[concepts/urnetwork-readiness-connectionstatus]] and [[concepts/urnetwork-provide-tun-investigation]].

## Details

The 2026-05-29 URnetwork investigation isolated a regression where logs showed `CONNECTING peers=0 deadline=300000ms`. The issue was not that the SDK attach path was absent; logs showed engine-side attach. The problem was that a 5-minute wait lived inside startup readiness before `TunnelController.onEngineStarted()`, preventing watchdog and recovery infrastructure from running.

The accepted model separates startup from runtime health. Startup should pass once the engine has actually attached and issued connection work, or when official runtime signals indicate enough readiness: `CONNECTED`, `providerStateAdded > 0`, or real peers. A bare `CONNECTING` should not be treated as final success, but `peers=0` should also not block startup for five minutes.

The 5-minute peer grace remains useful after startup. It belongs in `EngineWatchdogCoordinator` or equivalent runtime health logic, where the engine is already marked started and can continue searching peers without freezing the UI and lifecycle. The log records commit `310e86c4 FIX: Исправление готовности URnetwork` as the implementation.

## Related Concepts
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/urnetwork-provide-tun-investigation]]
- [[concepts/health-monitor-p2p-mismatch]]
- [[concepts/engine-await-ready-pattern]]

## Sources
- [[daily/2026-05-29]]: Diff against `v0.2.0` connected the regression to a `45_000ms -> 300_000ms` readiness wait.
- [[daily/2026-05-29]]: User clarified that the 5-minute window should allow runtime peer search, not block startup.
- [[daily/2026-05-29]]: Commit `310e86c4` moved no-peer grace to runtime and used `providerStateAdded` as a readiness signal.
