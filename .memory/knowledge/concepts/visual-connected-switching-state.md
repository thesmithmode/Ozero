---
title: "Visual Connected State During Engine Switching"
aliases: [visualConnected, switching-ui-state, speed-history-null-transition]
tags: [ui, compose, architecture, vpn, pattern]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Visual Connected State During Engine Switching

The VPN UI must distinguish three states: Idle (no VPN), Connected (VPN active), and Switching (transition between engines). A naive implementation clears the speed chart and shows a disconnected UI the moment `engineStats` becomes `null` during transition — even though the user's connection is only momentarily interrupted. The fix: `visualConnected = isConnected || switching != null` keeps the chart and connected-state UI visible while a switch is in progress, and `SpeedHistory` is not cleared when stats transiently become `null`.

## Key Points

- `isConnected` alone is insufficient for UI: switches cause a brief disconnected state that makes the UI flicker
- `visualConnected = isConnected || switching != null` — while `_switching` is set (VPN restart in progress), treat as visually connected
- `SpeedHistory` must not be cleared when `engineStats == null` during a switch transition — stats are null because the new engine hasn't connected yet, not because the user stopped VPN
- A 12-second watchdog timeout on `_switching` (via coroutine `Job.cancel()`) prevents "eternal yellow" if VPN restart fails silently
- Symptom before fix: chart jumped to zero mid-switch, button flickered yellow then gray then green

## Details

### The Switching-Invisible Problem

When the user selects a different engine, `OzeroVpnService` stops the current engine and starts the new one. During the transition:

1. `TunnelController.state` transitions to `Disconnected` (VPN is actually stopping)
2. `engineStats` becomes `null` (no active engine)
3. The old UI immediately shows "disconnected" state — chart goes to zero, IP disappears, button turns gray
4. A moment later: `state` transitions to `Connected` with the new engine

Without `visualConnected`, the user sees a flash of disconnected UI during every engine switch. For a 3-second switch, this is visually jarring and implies a longer outage than actually occurred.

### The visualConnected Pattern

```kotlin
// In MainViewModel or equivalent
private val _switching = MutableStateFlow<EngineId?>(null)

val visualConnected: StateFlow<Boolean> = combine(
    tunnelController.state,
    _switching
) { state, switching ->
    state is TunnelState.Connected || switching != null
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```

When `_switching` is set (a manual engine change triggered restart), `visualConnected` remains `true` even if `TunnelController.state` momentarily shows `Disconnected`. The UI binds to `visualConnected` instead of raw `state is Connected`.

### SpeedHistory Preservation

A companion fix: `speedHistory` (the rolling list of `SpeedSample` entries for the chart) is not cleared when `engineStats` becomes `null`. Previous implementation cleared on null transition:

```kotlin
// Before fix — clears chart on every switch
val stats = engineStats ?: run { speedHistory.clear(); return@collect }
```

After fix: null stats during transition are simply ignored — no new sample appended, but history preserved:

```kotlin
// After fix — preserves chart during transition
val stats = engineStats ?: return@collect
```

The chart retains the last connected engine's speed data and resumes appending once the new engine connects. The user sees a brief "flat line" period during the switch rather than a chart reset.

### Watchdog Timeout

A 12-second coroutine watchdog cancels `_switching` if VPN restart hasn't completed:

```kotlin
private var switchingWatchdog: Job? = null

fun onManualEngineChange(engineId: EngineId) {
    _switching.value = engineId
    switchingWatchdog?.cancel()
    switchingWatchdog = viewModelScope.launch {
        delay(12_000)
        _switching.value = null  // timeout: stop showing yellow
    }
}
```

This uses `Job.cancel()`, not `Handler.postDelayed()` — the Hilt-injected scope handles cleanup, and the job is cancelled when the actual connection completes. Without the watchdog, a failed VPN restart leaves the button permanently yellow.

### Discovery Context

Discovered during v0.0.12 debugging as "Bug 3" (chart jumping, late yellow). Root cause: the UI did not model the switching intermediate state — it only had Idle and Connected. The switchin state was implicit (inferred from debounce timing) rather than explicit. Making it explicit (`_switching` StateFlow) and deriving `visualConnected` from it eliminated both the chart jump and the yellow-button delay.

## Related Concepts

- [[concepts/debounce-split-heterogeneous-flow]] - The fast-path debounce split that feeds `_switching`; manual engine change triggers both the fast-path restart AND sets `_switching`
- [[concepts/engine-switch-chain-cascading-failures]] - The broader engine-switch bug class that led to this UI fix
- [[concepts/engine-ownership-boundary]] - VpnService owns the actual restart; VM owns the visual state model
- [[concepts/chart-nice-max-dynamic-scaling]] - SpeedHistory and chart rendering that this fix preserves during transitions

## Sources

- [[daily/2026-05-11.md]] - Session 20:41: `visualConnected = isConnected || switching != null`; SpeedHistory not cleared on stats=null during transition; 12s watchdog timeout on `_switching` via coroutine Job.cancel(); chart-jump and late-yellow root causes traced to missing explicit switching state
