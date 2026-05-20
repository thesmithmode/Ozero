---
title: "Engine Chip Race Observer"
aliases: [chip-race, engine-settings-restart-observer, probing-engine-change]
tags: [engine, ui, race-condition, vpn, restart, observer]
sources:
  - "daily/2026-05-20.md"
created: 2026-05-20
updated: 2026-05-20
---

# Engine Chip Race Observer

`EngineSettingsRestartObserver` previously gated engine-change restarts exclusively on `TunnelState.Connected`. When a user tapped a different engine chip during `Probing` or `Connecting`, the state change was consumed but no restart was triggered — the new engine selection was silently dropped. After the current engine finished connecting, it stayed running, while the UI chip showed a different engine. The fix extends the gate to restart from any active tunnel state when the running engine diverges from the user-selected engine.

## Key Points

- Original gate: restart only on `TunnelState.Connected` — drops engine changes during `Probing`/`Connecting`
- Fix: restart when `engine ≠ manualEngine` from `Probing`/`Connecting` (active states with a known target engine)
- Guard: `Probing(null)` (no target engine known yet) must skip — no valid comparison possible
- Normal flow: `Connected` tapping chips → `engine == manualEngine` → no wasteful restart
- `MainViewModel` posts an optimistic `switching` marker on tap during a race to signal the transition immediately in UI
- 3 sentinel tests for old `NotSupported`/`Failed`→`stopVpn` behavior were inverted after removing that behavior

## Details

### The Race Condition

With the original `Connected`-only gate and a 4s debounce, two conditions combined to silently drop engine changes:

1. **Gate**: observer only acts on `TunnelState.Connected`; `Probing`/`Connecting` states are ignored.
2. **Debounce**: even if `Connected` was reached, 4 seconds pass before the restart fires.

If a user changed the engine chip while the engine was connecting and the engine reached `Connected` during the debounce window, the observer would see `engine == manualEngine` (the original engine) and skip the restart. The new selection was lost.

### The Fix

```kotlin
// In EngineSettingsRestartObserver
combine(tunnelController.state, configStore.config()) { state, config ->
    when (state) {
        is TunnelState.Connected -> {
            // Original path: restart on explicit Connected
            if (state.engine != config.manualEngine) restartWithNewEngine()
        }
        is TunnelState.Probing -> {
            // New path: if target engine is known and doesn't match, restart
            val target = state.targetEngine ?: return@combine  // Probing(null) = skip
            if (target != config.manualEngine) restartWithNewEngine()
        }
        is TunnelState.Connecting -> {
            if (state.engine != config.manualEngine) restartWithNewEngine()
        }
        else -> Unit
    }
}
```

The `Probing(null)` guard is critical: early in the connection sequence before the engine is identified, `targetEngine` is null and there is nothing to compare against. Restarting on `Probing(null)` would cause a restart loop on every connection attempt.

### Sentinel Test Inversions

Three existing sentinel tests protected behavior tied to `NotSupported` and `Failed` states triggering `stopVpn()` calls. When that behavior was removed as part of the chip-race fix (the observer should not call `stopVpn` on terminal states — `TunnelController` handles those), the tests began failing because they asserted the old pattern. The sentinels were inverted to assert the new invariant: `NotSupported` and `Failed` no longer cause `stopVpn` calls from the observer.

### Interaction with Debounce

The 4-second debounce in `EngineSettingsRestartObserver` remains unchanged. It prevents rapid re-restarts when the user is actively tapping chips. The `Probing`/`Connecting` extension only activates when a clear divergence is detected (`engine ≠ manualEngine`), so debounce interaction is safe: the debounce delay produces a transient ~4s UI lag where the chip shows the pending engine, acceptable UX.

### TunnelController.switching Clear

`TunnelController.clearing(switching)` was intentionally NOT modified. Removing the `switching` clear on non-target `Connected` events would break the contract test asserting that a `Connected` event for the wrong engine clears `switching`. The transient UI lag is the accepted trade-off.

## Related Concepts

- [[concepts/engine-switch-chain-cascading-failures]] - The broader set of cascading failures from rapid engine switching; chip race is a UI manifestation
- [[concepts/debounce-split-heterogeneous-flow]] - Debounce in heterogeneous observer context; chip race required extending states not reducing debounce
- [[concepts/vpn-engine-pipeline]] - ManualEngineSource and how engine selection reaches the observer
- [[concepts/viewmodel-stateflow-test-race]] - Related ViewModel state timing issue; sentinels protecting removed behavior is a recurrent pattern

## Sources

- [[daily/2026-05-20.md]] - Session 19:07: EngineSettingsRestartObserver Connected-only gate dropped engine changes during Probing/Connecting; fix: restart from active states when engine ≠ manualEngine; Probing(null) guard; 3 sentinel inversions for removed NotSupported/Failed→stopVpn behavior; TunnelController.switching clear not touched to preserve contract test
