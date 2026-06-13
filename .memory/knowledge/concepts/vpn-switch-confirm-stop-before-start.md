---
title: VPN switch confirmed stop before start
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# VPN switch confirmed stop before start
## Summary
Engine switching must not start the new VPN until the old service has confirmed `Idle` or `Failed`; ignoring a stop timeout can overlap old cleanup with the new start.
## Key Points
- `MainActivity.restartVpnIfConnected` must treat a stop timeout as a real blocker, not continue to `ACTION_START`.
- A new start before old shutdown completes can race with `OzeroVpnService` resetting `stopping=false`.
- The switching watchdog should be restarted immediately before the new start so a slow stop does not consume the visual switching budget.
- The symptom can look like a failed engine switch, a return to the old engine, or an unstable ByeDPI to WARP transition.
## Details
The session investigated a suspected switch race while CI was running. The concrete risk was found in `MainActivity.restartVpnIfConnected`: stop was awaited through `withTimeoutOrNull`, but the result was ignored. That allowed `ACTION_START` to be sent even when the previous service had not reached a confirmed terminal stopped state.

This matters because `OzeroVpnService` resets `stopping=false` during start handling. If a new start is issued while the previous shutdown is still cleaning up, the old cleanup and new lifecycle can interleave. User-visible behavior can then appear as "did not switch", "switched back", or unstable behavior during transitions such as ByeDPI to WARP.

The safe contract is explicit: wait for the old engine to reach `Idle` or `Failed`; on timeout, do not start a new VPN. Once a confirmed stop is observed, restart the switching watchdog close to the new start so the UI watchdog measures the actual start phase, not the preceding shutdown delay.
## Related Concepts
- [[concepts/engine-switch-failure-containment]]
- [[concepts/vpnservice-double-shutdown-guard]]
- [[concepts/visual-connected-switching-state]]
- [[concepts/switching-watchdog-engine-conflict]]
## Sources
- [[daily/2026-05-30]]: The switching investigation identified `MainActivity.restartVpnIfConnected` ignoring the `withTimeoutOrNull` stop result.
- [[daily/2026-05-30]]: The fix decision was to wait for confirmed stop, not start on timeout, and restart the switching watchdog before start.
- [[daily/2026-05-30]]: The race was tied to `OzeroVpnService` resetting `stopping=false` when handling `ACTION_START`.
