---
title: "URnetwork filterLocations Explicit Trigger for Regions and Cities"
aliases: [filter-locations-trigger, vc-start-callback, locations-regions-cities]
tags: [urnetwork, sdk, gotcha, architecture]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# URnetwork filterLocations Explicit Trigger for Regions and Cities

`LocationsViewController.start()` alone does not trigger the `addFilteredLocationsListener` callback with regions and cities. The URnetwork SDK requires an explicit `filterLocations("")` call after `vc.start()` to populate the listener with the full location hierarchy (countries → regions → cities). Without this call, only the top-level country list is available. Pattern taken from upstream reference app's `BrowseLocations.kt`.

## Key Points

- `vc.start()` initializes the controller but does NOT fire `addFilteredLocationsListener` with regions/cities automatically
- `filterLocations("")` (empty string = no filter) must be called explicitly after `vc.start()` to trigger the callback
- Same pattern applies in `setSearchQuery()` — delegate search queries to `vc.filterLocations(query)` for runtime filtering
- Reference: upstream `BrowseLocations.kt` calls `filterLocations("")` explicitly after controller start
- `UrnetworkLocationsViewModel.refreshOnce()` is the correct place to call `filterLocations("")` post-start

## Details

### The Missing Callback Trigger

The URnetwork SDK's `LocationsViewController` maintains an internal state machine. `start()` initializes the controller and begins fetching available locations from the network. However, the `addFilteredLocationsListener` callback is NOT automatically called after `start()` — the SDK waits for a filter query to determine which locations to surface.

When `filterLocations("")` is called with an empty string (no filter criteria), the SDK interprets this as "show all available locations" and fires the callback with the complete hierarchy: countries at the top level, and for each expanded country, its regions and cities. Without this explicit trigger, the callback fires only with the cached country list from the previous session (or an empty list on first run).

### UrnetworkLocationsViewModel Pattern

The correct initialization sequence:

```kotlin
class UrnetworkLocationsViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
) : ViewModel() {

    fun refreshOnce() {
        val vc = bridge.openLocationsViewController() ?: return
        vc.addFilteredLocationsListener { locations ->
            _uiState.value = LocationsUiState.Loaded(locations)
        }
        vc.start()
        vc.filterLocations("")  // REQUIRED: triggers callback with full hierarchy
    }

    fun setSearchQuery(query: String) {
        currentVc?.filterLocations(query)  // delegate to SDK filter
    }
}
```

### Why Only Countries Appeared Without the Fix

Without `filterLocations("")`, the SDK returns only top-level country entries from its internal cache. Regions and cities are always fetched lazily — they only appear after a filter query signals to the SDK that the caller wants them. This behavior was discovered by comparing the symptom (only countries visible in the picker) against the upstream `BrowseLocations.kt` which had the `filterLocations("")` call.

## Related Concepts

- [[concepts/urnetwork-location-hierarchy-migration]] — broader location hierarchy migration from countryCode string to ConnectLocation; this article covers the SDK trigger required to populate that hierarchy
- [[concepts/urnetwork-sdk-integration]] — parent integration article; this is a runtime SDK behavior gotcha

## Sources

- [[daily/2026-05-19.md]] — Session 15:13: location picker showed only countries despite location hierarchy commit; root cause = missing `filterLocations("")` after `vc.start()`; fix = explicit call in `refreshOnce()` and `setSearchQuery()`; pattern from upstream `BrowseLocations.kt`
