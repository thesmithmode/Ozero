---
title: "Engine Chip/Switching Desync Fix"
aliases: [switching-desync, chip-desync, connected-clearing-switching]
tags: [vpn-engine, state-machine, tunnel-controller, concurrency, gotcha]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# Engine Chip/Switching Desync Fix

`TunnelController` state machine had a bug where `Connected(engineX)` unconditionally cleared the `switching` state, even when switching targeted a different engine. This caused the UI chip to display one engine (WARP) while the tunnel was actually connected through another (URnetwork), or vice versa.

## Key Points

- `Connected(X)` should only clear `switching` if `sw.to == null` (no pending target) OR `sw.to == X` (connected to the intended target)
- If `switching.to == Y` and we receive `Connected(X)` where `X ≠ Y`, `switching` must be preserved — a restart to Y is still pending
- `restartVpnIfConnected`: previously set `switching.to = null`; fixed to `switching.to = tunnelController.switching.value?.to` (preserves the pending user target through restart)
- The fix is generic, not specific to any engine pair — any `Connected(X)` → `switching(to=Y)` desync triggers it
- An existing test `switchingDoesNotClearOnConnectedOfDifferentEngine` was asserted with `assertNull` under the wrong preconditions, effectively protecting the bug as an invariant

## Details

### The Desync Scenario

User selects WARP → `switching(from=null, to=WARP)`. Before WARP connects, user selects URnetwork → `switching(from=null, to=URNETWORK)`. WARP connects first → `Connected(WARP)` clears `switching` unconditionally → chip shows WARP Connected. URnetwork restart never happens. Tunnel is WARP but user intended URnetwork.

The chip UI reads from `switching.to` to show the "target" engine. When `switching` is cleared incorrectly, chip falls back to the currently-connected engine, hiding that a pending switch was in progress.

### The Generic Fix

```kotlin
// Before (buggy):
is TunnelState.Connected -> {
    switching = null  // always clears
}

// After (correct):
is TunnelState.Connected -> {
    val sw = switching
    if (sw == null || sw.to == null || sw.to == event.engineId) {
        switching = null  // only clear if this connection satisfies the intent
    }
    // else: switching to different engine still pending — keep switching state
}
```

### restartVpnIfConnected Preservation

`restartVpnIfConnected` triggers when the engine settings change while VPN is active. Previously it reset `switching.to = null`, losing track of the target. Fix: copy `switching.value?.to` before restart so the state machine knows which engine to connect to after the restart completes.

### Sentinel Was Protecting the Bug

Test `switchingDoesNotClearOnConnectedOfDifferentEngine` (line 505-519) used `assertNull(tunnelController.switching.value)` after emitting `Connected(X)` when `switching.to = Y ≠ X`. The null assertion was wrong — the switching should survive. This is a textbook [[concepts/sentinel-protecting-bug-trap]] case: the test was added to document the observed (buggy) behavior rather than the correct behavior. The test was rewritten to assert that `switching` is preserved when the engine does not match.

## Related Concepts

- [[concepts/sentinel-protecting-bug-trap]] — identical pattern: sentinel asserting wrong behavior blocks correct fix
- [[concepts/vpn-engine-pipeline]] — ManualEngineSource and StrategyEngine provide engine selection state that `switching` tracks
- [[concepts/engine-chip-race-observer]] — separate but related: EngineSettingsRestartObserver only restarted from Connected state, missing Probing/Connecting transitions
- [[concepts/debounce-split-heterogeneous-flow]] — debounce split also prevents ghost switches from debounced restart signals

### Residual Gap: Watchdog Does Not Stop Running Engine (2026-05-22, session 23:11)

A related but separate issue was identified and left incomplete at session end: the `TunnelController` switching watchdog fires on timeout and resets `_switching` to null, but does NOT call `stop()` on the currently running engine. Consequence: if switching from ByeDPI → WARP and ByeDPI's `chainOrchestrator.stop()` hangs (observed to take >4s), the watchdog clears the switching state (log: `warn: switching watchdog timeout`) but ByeDPI continues running. When WARP's start sequence fires in parallel, both engines are active → traffic conflict → no data flow. Fix not yet applied as of 2026-05-22 23:11.

## Sources

- [[daily/2026-05-22.md]] — Session 11:59: chip shows WARP while tunnel connected to URnetwork; `Connected(X)` unconditional clear of switching → desync; fix: clear only if `sw.to == null || sw.to == X`; `restartVpnIfConnected` preserves `switching.to`; old test rewrote from bug-guard to correct-behavior sentinel. Session 23:11: watchdog resets `_switching` but doesn't stop engine → ByeDPI survives → WARP starts in parallel → conflict; fix pending
