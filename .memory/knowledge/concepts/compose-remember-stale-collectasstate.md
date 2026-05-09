---
title: "Compose remember Stale Initial Value with collectAsStateWithLifecycle"
aliases: [remember-stale-state, compose-remember-key, collectasstate-stale]
tags: [android, compose, gotcha, ui, state-management]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-05-08
---

# Compose remember Stale Initial Value with collectAsStateWithLifecycle

When a Composable uses `remember { mutableStateOf(settingsState.field) }` where `settingsState` comes from `collectAsStateWithLifecycle`, the `remember` block captures the initial value once and never re-initializes when the upstream StateFlow emits a new value. The UI shows stale data until the user manually edits the field. The fix is to use a keyed `remember(settingsState.field)` that re-creates the state when the source value changes.

## Key Points

- `remember { mutableStateOf(x) }` captures `x` at first composition — subsequent recompositions do NOT re-evaluate the initializer
- `collectAsStateWithLifecycle` correctly updates `settingsState` on new Flow emissions, but `remember` ignores the update because its key hasn't changed
- Symptom: text field shows old value from DataStore/repository; editing works but navigating away and back shows stale data again
- Fix: `remember(settingsState.apiUrl) { mutableStateOf(settingsState.apiUrl) }` — the key forces re-creation when the source value changes
- This trap is specific to editable UI fields backed by Flow state — read-only displays using `settingsState.field` directly are not affected (they recompose naturally)

## Details

### The Stale Capture Mechanism

Jetpack Compose's `remember` stores a value across recompositions. When called without keys (`remember { ... }`), the initializer lambda runs exactly once — on the first composition of that Composable. On subsequent recompositions (triggered by state changes, including new emissions from `collectAsStateWithLifecycle`), `remember` returns the cached value without re-evaluating the lambda.

This creates a problem for editable fields (e.g., `TextField`) that need both:
1. An initial value from a Flow/StateFlow (loaded from DataStore, Room, or network)
2. Local mutable state for user editing (the `mutableStateOf` wrapper)

The pattern `remember { mutableStateOf(settingsState.apiUrl) }` satisfies requirement (2) but breaks requirement (1): after the first composition, `settingsState.apiUrl` may change (e.g., user switches WARP slot, or DataStore emits a new value), but the `mutableStateOf` still holds the old value.

### The Ozero Discovery

In Ozero v0.0.7, `UrnetworkSettingsScreen.kt` line 159 used `remember { mutableStateOf(settingsState.apiUrl) }` to initialize an editable URL field. The `settingsState` came from `viewModel.settingsState.collectAsStateWithLifecycle()`. When the user changed settings in another screen and navigated back, the URL field still showed the old value — the `remember` block had captured the initial emission and ignored all subsequent ones.

A code review caught this before user-facing impact. The fix was minimal:

```kotlin
// BROKEN: stale after first composition
val apiUrl = remember { mutableStateOf(settingsState.apiUrl) }

// CORRECT: re-creates when source value changes
val apiUrl = remember(settingsState.apiUrl) { mutableStateOf(settingsState.apiUrl) }
```

The keyed `remember(settingsState.apiUrl)` tells Compose to discard the cached `mutableStateOf` and re-run the initializer whenever `settingsState.apiUrl` changes. This synchronizes the local editable state with the upstream Flow without losing the ability to edit locally.

### When This Trap Does NOT Apply

- **Read-only displays**: Using `settingsState.apiUrl` directly in a `Text()` composable works correctly — no `remember` involved, recomposition handles updates naturally
- **ViewModel-owned state**: If the ViewModel exposes a `MutableStateFlow` for editing and the screen collects it, there is no local `mutableStateOf` — the source of truth is always the ViewModel
- **One-shot screens**: If the screen is always freshly composed (e.g., dialog that opens/closes), the first-composition value is always current

The trap specifically affects screens that:
1. Stay composed across navigation (e.g., in a `NavHost` with `rememberSaveable`)
2. Use local `mutableStateOf` for editable fields
3. Receive updates from an external Flow after initial composition

### General Rule

Any `remember { mutableStateOf(externalValue) }` where `externalValue` comes from a Flow, LiveData, or other observable source must use `remember(externalValue)` with the external value as a key. This is a compile-time invisible bug — no warning, no error, just silently stale UI.

## Related Concepts

- [[concepts/viewmodel-stateflow-test-race]] - Related state timing issue: ViewModel collects StateFlow before test sets state; both are about lifecycle ordering causing stale/wrong state observation
- [[concepts/compose-launchedeffect-crash-invisibility]] - Another Compose runtime trap; different failure mode (crash vs stale data) but same theme of non-obvious Compose lifecycle behavior
- [[concepts/per-engine-ui]] - The per-engine settings screens where this pattern appears; each screen has editable fields backed by DataStore Flows

## Sources

- [[daily/2026-05-08.md]] - Session 20:41: code review found `remember { mutableStateOf(settingsState.…) }` in UrnetworkSettingsScreen.kt line 159; stale initial value from collectAsStateWithLifecycle; fix = `remember(settingsState.apiUrl)` with key
