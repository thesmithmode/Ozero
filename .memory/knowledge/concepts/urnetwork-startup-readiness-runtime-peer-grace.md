---
title: "URnetwork startup readiness vs runtime peer grace"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# URnetwork startup readiness vs runtime peer grace
## Key Points
- URnetwork must not wait 5 minutes in startup `awaitReady()` for `peers=0`; that blocks `onEngineStarted()` and watchdog/recovery.
- Startup readiness should be short and based on attach/connect evidence such as `tunnelStarted`, `connectIssued`, `CONNECTED`, `providerStateAdded`, or peers.
- The 5-minute `peers=0` tolerance belongs in runtime watchdog after the engine is marked started.
- The fix was committed as `310e86c4 FIX: Исправление готовности URnetwork`.
## Details
The regression pattern was `status=CONNECTING peers=0 deadline=300000ms`. The problem was not simply the size of the timeout but its layer: it lived in startup readiness before `TunnelController.onEngineStarted()`. While that wait was active, the lifecycle never reached the runtime phase where watchdog and recovery could manage transient peer absence.

The stable contract is to separate startup from runtime. Startup should prove that attach/connect flow has actually been initiated and that the SDK reports a meaningful readiness signal. `peers=0` by itself is not a startup failure. After startup succeeds, a runtime grace window can allow peer discovery to continue without falsely killing the engine.

`providerStateAdded` from `windowStatus` is an important signal because the reference URnetwork app uses it as part of runtime state. This concept is separate from relay/provide behavior in [[concepts/urnetwork-relay-provideenabled-sol-contract]] and should not be mixed with URnetwork relay diagnostics.
## Related Concepts
- [[concepts/urnetwork-readiness-connectionstatus]] - Earlier readiness rule for accepting SDK `CONNECTED`.
- [[concepts/urnetwork-provide-tun-investigation]] - Explains dummy IoLoop and provide-side behavior, separate from client readiness.
- [[connections/startup-readiness-runtime-recovery-boundary]] - General startup/runtime boundary shared by multiple engines.
- [[concepts/engine-poisoned-state-recovery-proof]] - Requires proving recovery without app restart.
## Sources
- [[daily/2026-05-29]]: records the observed `CONNECTING peers=0 deadline=300000ms` pattern after `attachTun`.
- [[daily/2026-05-29]]: records the agreed design of short startup readiness plus a 5-minute runtime peer grace.
- [[daily/2026-05-29]]: records commit `310e86c4` implementing URnetwork readiness changes and pushing them to `dev`.
