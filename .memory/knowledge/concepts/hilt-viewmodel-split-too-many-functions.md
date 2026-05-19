---
title: "Hilt ViewModel Split for TooManyFunctions Detekt Violation"
aliases: [viewmodel-split-detekt, too-many-functions-viewmodel, hilt-viewmodel-decompose]
tags: [android, hilt, viewmodel, detekt, architecture, pattern]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# Hilt ViewModel Split for TooManyFunctions Detekt Violation

When a `@HiltViewModel` exceeds detekt's `TooManyFunctions` threshold (default 20), the correct fix is architectural decomposition into multiple `@HiltViewModel` classes — NOT threshold bumping or `@Suppress`. Multiple `@HiltViewModel` instances can be used in a single Compose screen via multiple `hiltViewModel()` calls. Test infrastructure must be split to match: shared fakes go into a dedicated helper file with `internal` visibility.

## Key Points

- `@Suppress("TooManyFunctions")` is forbidden — detekt threshold = architectural signal, not a lint annoyance
- Decomposition strategy: identify 2-3 distinct concern groups within the ViewModel (location picking + config settings → 2 separate VMs)
- Multiple `@HiltViewModel`s in one composable: `val locationsVm: UrnetworkLocationsViewModel = hiltViewModel()` + `val settingsVm: UrnetworkEngineSettingsViewModel = hiltViewModel()`
- Test split: `UrnetworkLocationsViewModelTest` + `UrnetworkEngineSettingsViewModelTest` + shared helpers in `UrnetworkTestFakes.kt` (internal)
- Sentinel tests that read source files by path must update their file paths after the split

## Details

### The UrnetworkEngineSettingsViewModel Case

`UrnetworkEngineSettingsViewModel` accumulated 20+ functions spanning two distinct responsibilities:
1. **Location picking**: lifecycle management of `LocationsViewController`, `uiState`, `switchingCountry`, `refreshOnce`, `selectLocation`, `setProvidePaused`
2. **Configuration**: `selectWindowType`, `toggleFixedIpSize`, `toggleAllowDirect`, `selectProvideControlMode`, `selectProvideNetworkMode` + StateFlows

The decomposition separated concerns into `UrnetworkLocationsViewModel` (location-related functions) and `UrnetworkEngineSettingsViewModel` (config-related functions), both under the threshold.

### Compose Integration Pattern

```kotlin
@Composable
fun UrnetworkEngineSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    val locationsVm: UrnetworkLocationsViewModel = hiltViewModel()
    val settingsVm: UrnetworkEngineSettingsViewModel = hiltViewModel()

    val locationsState by locationsVm.uiState.collectAsStateWithLifecycle()
    val configState by settingsVm.configState.collectAsStateWithLifecycle()

    UrnetworkEngineSettingsContent(
        locationsState = locationsState,
        configState = configState,
        onSelectLocation = locationsVm::selectLocation,
        onSelectWindowType = settingsVm::selectWindowType,
    )
}
```

Hilt injects each ViewModel independently via its own factory. Both VMs are scoped to the same NavBackStackEntry, so they share lifecycle but not state.

### Test Infrastructure Split

Shared fakes (FakeUrnetworkConfigStore, FakeUrnetworkSdkBridge, etc.) that both test classes need go into `UrnetworkTestFakes.kt` with `internal` visibility — accessible to both test classes in the same Gradle module without exposing to other modules:

```kotlin
// UrnetworkTestFakes.kt
internal val fakeConfigStore = InMemoryUrnetworkConfigStore()
internal fun fakeLocationsVm() = UrnetworkLocationsViewModel(fakeBridge, fakeConfigStore)
internal fun fakeSettingsVm() = UrnetworkEngineSettingsViewModel(fakeConfigStore)
```

### Sentinel Test Migration

After splitting, sentinel tests that use `substringAfter("fun selectLocation(")` pointing to `UrnetworkEngineSettingsViewModel.kt` will fail vacuously (anchor not found → searches full file). All sentinel file paths must be updated to point to the new files. See [[concepts/sentinel-refactor-batch-audit]].

## Related Concepts

- [[concepts/suppress-annotation-decomposition]] — same principle: decompose instead of suppress; this article is the ViewModel-specific case
- [[concepts/sentinel-refactor-batch-audit]] — sentinel tests break when functions move between files; batch audit required before push
- [[concepts/hilt-assistedinject-mixed-injection]] — related Hilt DI pattern in the same codebase

## Sources

- [[daily/2026-05-19.md]] — CI refactor session: `UrnetworkEngineSettingsViewModel` exceeded `TooManyFunctions` threshold; decomposed into `UrnetworkLocationsViewModel` + `UrnetworkEngineSettingsViewModel`; shared helpers in `UrnetworkTestFakes.kt`; sentinel paths updated; threshold bumping rejected
