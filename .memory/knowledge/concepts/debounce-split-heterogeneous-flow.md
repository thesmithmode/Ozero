---
title: "Debounce Split for Heterogeneous Event Flows"
aliases: [debounce-split, mixed-urgency-debounce, fast-path-debounce, prev-tracking-flow]
tags: [architecture, android, ui, state-management, pattern]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Debounce Split for Heterogeneous Event Flows

A single debounced flow handling events from sources with different urgency levels creates a UX mismatch: critical user actions (manual engine selection) feel sluggish because they share the same debounce window as batch settings updates. The fix is to split the flow into a fast-path for user-initiated critical actions (no debounce) and a debounced path for ancillary changes. Additionally, the fast-path requires explicit prev-tracking to avoid triggering on the initial emission (which has no "previous" value to compare against).

## Key Points

- One debounce on a flow with heterogeneous sources = mismatch: critical action delayed, batch update correctly batched
- Split: `manualEngine` change → instant fast-path (no debounce), other settings → debounced (e.g., 4s)
- Initial emission edge case: first value has no `prev` → without explicit prev-tracking, it may trigger the fast-path incorrectly, causing spurious VPN restart → SIGABRT on Nubia
- Fix: explicit `prev` variable in the flow collector, emit only when `prev != null && prev != current`
- The pattern generalizes: any StateFlow-driven observer that triggers side-effects (VPN restart) must distinguish initial emission from genuine change

## Details

### The Single-Debounce Problem

In Ozero's `EngineSettingsRestartObserver`, settings changes trigger VPN restart. A 4-second debounce window batches rapid changes (toggling multiple settings quickly) into a single restart. However, when the user selects a different engine via the manual engine chip, the same 4-second debounce applies — the UI turns yellow ("switching") but the actual restart is delayed by the full debounce window. The user perceives this as lag: "кнопка жёлтая с задержкой."

The root cause is that `manualEngine` changes and batch settings changes flow through the same `combine(settings, splitConfig, manualEngine)` → `debounce(4000)` pipeline. The debounce cannot distinguish between "user tapped engine chip" (should restart immediately) and "settings screen changed 3 toggles in 2 seconds" (should batch).

### The Split Architecture

The fix separates the flow into two paths:

```kotlin
// Fast-path: manual engine changes (no debounce)
viewModelScope.launch {
    settingsRepo.manualEngine.collect { engine ->
        if (prev != null && prev != engine) {
            triggerRestart("manualEngine changed")
        }
        prev = engine
    }
}

// Debounced path: other settings changes
viewModelScope.launch {
    combine(settingsRepo.settings, splitConfig) { s, sc -> Snapshot(s, sc) }
        .debounce(SETTINGS_DEBOUNCE_MS)
        .collect { snapshot ->
            if (prevSnapshot != null && prevSnapshot != snapshot) {
                triggerRestart("settings changed")
            }
            prevSnapshot = snapshot
        }
}
```

The manual engine path fires instantly; the settings path batches. Both use explicit prev-tracking to avoid false triggers on initial emission.

### The Initial Emission Trap

Without prev-tracking, the first emission from `manualEngine` (when the observer starts) triggers a restart — even though the engine hasn't changed. This happens because:

1. Observer starts → `manualEngine.collect` emits current value
2. No `prev` to compare → observer sees "new value" → triggers restart
3. On Nubia devices, this restart during `libam-go` cleanup caused SIGABRT

The explicit `prev` variable (initialized to `null`, set after each emission) gates the restart: only emit when both `prev != null` (not initial) and `prev != current` (actually changed). This also fixed a related bug where the first non-manual change could "proскочить" through the fast-path if the observer used an initial-emission edge case instead of explicit prev-tracking.

### Generalization

This pattern applies to any observer that:
- Combines multiple StateFlows with different change frequencies
- Triggers expensive side-effects (restart, network call, file write)
- Has sources where some changes are user-initiated (urgent) and others are system-initiated (batchable)

The split approach preserves responsiveness for user actions while preventing restart storms from batch changes. The 12-second watchdog timeout on the `_switching` state (via coroutine `Job.cancel()`, not `Handler.postDelayed`) provides defense against an eternal yellow button if the VPN restart fails silently.

### Why Not Optimistic UI

An alternative — showing connected state immediately (optimistic UI) while the restart happens in background — was rejected as a "костыль" (crutch). The split debounce is a structural backend fix: the restart actually happens faster for manual engine changes, not just the UI pretending it did.

## Related Concepts

- [[concepts/engine-switch-chain-cascading-failures]] - The engine-switch chain where debounce mismatch was discovered as Bug 6
- [[concepts/engine-ownership-boundary]] - VpnService owns restart; observer signals intent but doesn't call bridge directly
- [[concepts/viewmodel-stateflow-test-race]] - Related StateFlow lifecycle issue: initial emission timing affects test correctness; same prev-tracking discipline applies

## Sources

- [[daily/2026-05-11.md]] - Session 20:41: 4s debounce identical for manual engine chip and settings batch → split: manualEngine instant fast-path, rest debounced; explicit prev-tracking flow to avoid initial-emission false trigger; SIGABRT on Nubia from initial emission restart; optimistic UI rejected; 12s watchdog timeout on _switching state
