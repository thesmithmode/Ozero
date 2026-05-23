---
title: "Switching Watchdog: State Reset Without Engine Stop Causes Parallel Conflict"
aliases: [switching-watchdog-gap, watchdog-parallel-engines, switching-timeout-conflict]
tags: [vpn-engine, state-machine, tunnel-controller, concurrency, architecture, gotcha]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# Switching Watchdog: State Reset Without Engine Stop Causes Parallel Conflict

`TunnelController` has a switching watchdog that fires after a timeout to detect stuck engine transitions. The watchdog correctly resets `_switching` to null (clearing stale UI state), but does NOT call `stop()` on the currently running engine. If the engine's `chainOrchestrator.stop()` is the reason for the hang, clearing `_switching` while the engine continues running creates a window where the start sequence for the new engine fires in parallel with a still-live old engine.

## Key Points

- Watchdog fires → `_switching = null` → UI chip no longer shows switching animation
- The OLD engine (e.g., ByeDPI) continues executing in background — `chainOrchestrator.stop()` observed to take >4s
- NEW engine (e.g., WARP) start sequence is triggered by the `_switching` clear event → both engines active
- Two engines simultaneously active → traffic conflict → neither routes correctly → data flow = 0
- The fix requires watchdog to FORCE-STOP the hanging engine before or alongside resetting `_switching`
- Fix was identified but NOT applied as of 2026-05-22 23:11

## Details

### The Failure Sequence

1. User switches from ByeDPI → WARP: `_switching = SwitchingState(from=BYEDPI, to=WARP)`
2. ByeDPI `stop()` is called; its `chainOrchestrator.stop()` blocks (coroutine join, native thread teardown)
3. Watchdog timeout fires: `Log.w(TAG, "switching watchdog timeout")` → `_switching = null`
4. `TunnelController` emits state change; `StartSequenceCoordinator` observes clear of `_switching` → triggers WARP start
5. ByeDPI native thread finishes teardown 1-2s later; its TCP proxy fd is still bound to port 1080
6. WARP starts, hev-socks5-tunnel tries to connect to SOCKS 1080 → port collision or partial bind
7. Result: WARP shows "Connected" (handshake succeeded) but actual data through tunnel ≈ 0

The root cause is the watchdog's responsibility mismatch: it resets coordination state but not execution state. The engine is still running after the watchdog fires.

### Why Stop Takes >4s

ByeDPI's stop sequence involves:
1. `proxyJob.cancel()` — signals coroutine cancellation
2. `jniForceClose()` — sends shutdown to native C thread via socket close
3. `job.join()` — waits for native thread to acknowledge shutdown and exit

Step 3 can block if the native C thread is inside a blocking syscall (`accept()`, `poll()`). `jniForceClose()` sends a signal via fd close, but the C thread may not wake immediately. This is a known timing issue documented in [[concepts/byedpi-native-thread-join-race]].

### Correct Watchdog Behavior

```kotlin
// Current (incomplete):
private fun onSwitchingTimeout(engineId: EngineId) {
    Log.w(TAG, "switching watchdog timeout for $engineId")
    _switching.value = null  // clears state but engine still runs
}

// Required fix:
private fun onSwitchingTimeout(engineId: EngineId) {
    Log.w(TAG, "switching watchdog timeout for $engineId — force stopping")
    _switching.value = null
    serviceScope.launch {
        // Force-stop the hanging engine before new engine can start
        chainOrchestrator.forceStop(engineId)
    }
}
```

The `forceStop()` path should bypass the normal graceful shutdown sequence if the engine is stuck — e.g., using a shorter timeout before escalating to process-level cleanup.

### Relationship to Chip Desync Fix

This issue is orthogonal to the chip desync fix (see [[concepts/engine-chip-switching-desync]]). The desync fix addressed: `Connected(X)` clearing `_switching` when the switch was to a *different* engine `Y`. The watchdog gap addresses: timeout clearing `_switching` while the source engine is still executing. Both are `TunnelController` state machine bugs but in different transitions.

The chip desync fix was applied (commit on 2026-05-22). The watchdog gap fix was NOT applied (pending as of session end 23:11).

### Detection Signature in Logs

```
W TunnelController: switching watchdog timeout        ← watchdog fired
I WarpEngine: start — turning on AWG                 ← WARP starts immediately after
I ByeDpiEngine: jniForceClose returned               ← ByeDPI finishes 1-2s LATER
E WarpEngine: stats poll: rx_bytes=0 tx_bytes=NNN    ← no traffic through WARP
```

This log pattern (watchdog → WARP start → delayed ByeDPI close → zero rx) is the diagnostic fingerprint.

## Related Concepts

- [[concepts/engine-chip-switching-desync]] — sibling bug: `Connected(X)` clearing wrong switching state; fix applied 2026-05-22
- [[concepts/engine-switch-chain-cascading-failures]] — related: rapid engine switches causing cascading stop/start failures
- [[concepts/byedpi-native-thread-join-race]] — the underlying reason ByeDPI stop can take >4s
- [[concepts/engine-ownership-boundary]] — VpnService must be sole engine lifecycle owner; watchdog violating this by leaving orphan engine
- [[concepts/debounce-split-heterogeneous-flow]] — debounce prevents rapid switch spam; watchdog gap occurs even with proper debounce if stop is slow

## Sources

- [[daily/2026-05-22.md]] — Session 23:11: watchdog resets `_switching` without stopping engine; ByeDPI `chainOrchestrator.stop()` takes >4s; WARP starts in parallel; traffic conflict → no data; fix not applied as of session end; advisory called for architectural solution before session ended
