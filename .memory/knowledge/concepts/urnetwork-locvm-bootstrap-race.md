---
title: "URnetwork LocVM Bootstrap Race"
aliases: [locvm-init-race, bootstrapJob-join, urnetwork-notconnected-flash]
tags: [urnetwork, viewmodel, coroutines, race-condition, gotcha]
sources:
  - "daily/2026-05-22 (1).md"
created: 2026-05-22
updated: 2026-05-22
---

# URnetwork LocVM Bootstrap Race

`UrnetworkLocationsViewModel.init` had two `launch` coroutines without synchronization. The second coroutine branched on `active/inactive` state before the first (bootstrap) coroutine had finished loading initial data from the SDK. On screen open, this produced a transient `NotConnected` flash even when the engine was already active.

## Key Points

- `init` block: coroutine A loads bootstrap data; coroutine B observes tunnel state and decides active vs inactive branch
- No `join()` between A and B → B can execute before A completes → sees stale `NotConnected` snapshot
- Fix: `bootstrapJob.join()` before the active/inactive branching in coroutine B
- Pattern name: **inject-before-decide** — guarantee init is complete before making a state decision
- NotConnected flash is a UX regression category: causes section flicker and stale placeholder text on screen open

## Details

### The Race Sequence

```
Coroutine A (bootstrap): fetch SDK locations, populate _locations
Coroutine B (observer):  observe tunnelState → if active → show regions; else show NotConnected
```

Without synchronization, coroutine B can run while coroutine A is still fetching. If `tunnelState` is `Active` but `_locations` is empty (A not done), B sees an empty list and renders the NotConnected placeholder. A completes 100-500ms later, but B has already committed its branch decision.

### The Fix

```kotlin
private val bootstrapJob = viewModelScope.launch { loadInitialLocations() }

init {
    viewModelScope.launch {
        bootstrapJob.join()  // ← wait for init before deciding branch
        tunnelStateFlow.collect { state ->
            when (state) {
                is TunnelState.Connected -> renderActive()
                else -> renderInactive()
            }
        }
    }
}
```

`bootstrapJob.join()` suspends coroutine B until A has populated `_locations`. The observer then starts with correct data regardless of engine state.

### Applicability

This pattern applies to any ViewModel where:
1. Init loads data asynchronously (`bootstrapJob`)
2. A second observer makes UI branching decisions that depend on that data

Forgetting the `join()` always produces a flash/stutter on first render because Android's `LaunchedEffect` and `collectAsStateWithLifecycle` fire immediately on composition. The window between coroutine B start and coroutine A completion is reliably non-zero on real devices.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] — StateFlow emission ordering races in tests; this is the runtime analog
- [[concepts/stateIn-eagerly-test-trap]] — Eagerly-started stateIn has a similar bootstrap visibility problem in tests
- [[concepts/urnetwork-filteredlocations-bestmatches]] — UrnetworkLocationsViewModel context: locations are fetched via SDK callback
- [[concepts/compose-remember-stale-collectasstate]] — Compose stale state captures initial value; bootstrap race feeds stale initial value

## Sources

- [[daily/2026-05-22 (1).md]] — Session 16:02+: UrnetworkLocationsViewModel init had two unsynchronized launches → NotConnected flash on screen open; fix: bootstrapJob.join() before active/inactive branch in observer coroutine; commit e0d53ca4
