---
title: "HiltViewModel Split for TooManyFunctions Detekt Violation"
aliases: [viewmodel-decomposition, hiltviewmodel-split, toomanyfunctions-viewmodel]
tags: [kotlin, hilt, architecture, detekt, compose]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# HiltViewModel Split for TooManyFunctions Detekt Violation

When a `@HiltViewModel` accumulates unrelated concerns, it triggers detekt `TooManyFunctions` (threshold 20). The correct fix is architectural decomposition into two separate `@HiltViewModel` classes — never threshold bumping, never suppression. Two ViewModels can coexist in a single Compose screen via two `hiltViewModel()` calls.

## Key Points

- detekt `TooManyFunctions` threshold = 20; triggers when function count >= threshold
- Threshold bumping is forbidden (see `feedback_decompose_over_threshold`); decompose the class
- Two `@HiltViewModel` classes in one Composable: `val vm1 = hiltViewModel<VM1>(); val vm2 = hiltViewModel<VM2>()` — clean, no boilerplate
- Shared test helpers across split VMs → `internal` visibility helper file (`UrnetworkTestFakes.kt`)
- Sentinel tests that read source by path must be updated after split — they reference the old file path

## Details

### The Ozero Split (v0.1.5)

`UrnetworkEngineSettingsViewModel` reached 20 functions covering three unrelated concerns:

1. **Location picker lifecycle** — `LocationsViewController` lifecycle, `uiState`, `switchingCountry`, `refresh`, `selectLocation`, `setProvidePaused`
2. **Config settings** — `selectWindowType`, `toggleFixedIpSize`, `toggleAllowDirect`, `selectProvideControlMode`, `selectProvideNetworkMode`
3. **Balance/traffic** — `_balanceState`, balance refresh, cache hydration

Result: three independent `@HiltViewModel` concerns in one class, each with its own dependencies.

### Decomposition

Split into two ViewModels along the dominant responsibility boundary:

**`UrnetworkLocationsViewModel`** (16 functions):
- Owns `LocationsViewController` lifecycle
- `uiState: StateFlow<UrnetworkLocationsUiState>`
- `switchingCountry`, `refresh`, `selectLocation`, `setProvidePaused`
- Injected: `UrnetworkSdkBridge`, `UrnetworkCountryStore`, coroutine scope

**`UrnetworkEngineSettingsViewModel`** (9 functions):
- Config toggles + balance flows
- `selectWindowType`, `toggleFixedIpSize`, `toggleAllowDirect`, `selectProvideControlMode`, `selectProvideNetworkMode`
- Injected: `UrnetworkConfigStore`, `RealUrnetworkBalanceRepository`

### Screen Usage

```kotlin
@Composable
fun UrnetworkEngineSettingsScreen() {
    val locationsVm: UrnetworkLocationsViewModel = hiltViewModel()
    val settingsVm: UrnetworkEngineSettingsViewModel = hiltViewModel()
    // ...
}
```

Both VMs are scoped to the same composable's NavBackStackEntry by Hilt — no lifecycle mismatch.

### Test Structure

- `UrnetworkLocationsViewModelTest` — location VC lifecycle, filterLocations trigger, country persistence
- `UrnetworkEngineSettingsViewModelTest` — config toggles, balance hydration from cache
- `UrnetworkCountryPersistenceTest` — cross-VM persistence contracts
- `UrnetworkTestFakes.kt` — shared fakes (`FakeUrnetworkSdkBridge`, `FakeUrnetworkConfigStore`) with `internal` visibility

### Sentinel Trap After Split

Sentinel tests that assert source structure by reading file content (`File.readText().contains(...)`) will silently pass vacuously after a split if they still point to the old file. Pattern:

```
// Before split — sentinel reads UrnetworkEngineSettingsViewModel.kt
// After split — that file no longer contains location logic
// Sentinel still passes because assertion was "file does NOT contain X"
```

**Rule:** after any VM split, grep all sentinel test files for the old VM class name and update paths.

## Related Concepts

- [[concepts/suppress-annotation-decomposition]] — same principle: never suppress, always decompose
- [[concepts/viewmodel-stateflow-test-race]] — ViewModel in @BeforeEach race; relevant when testing split VMs
- [[concepts/urnetwork-sdk-integration]] — the VM split was part of URnetwork UI evolution

## Sources

- [[daily/2026-05-19.md]] — Session "CI refactor — UrnetworkEngineSettingsViewModel split": 20-function VM decomposed into `UrnetworkLocationsViewModel` (16) + `UrnetworkEngineSettingsViewModel` (9); shared test helpers in `UrnetworkTestFakes.kt`; sentinel path update requirement documented
