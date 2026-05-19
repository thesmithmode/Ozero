---
title: "URnetwork filterLocations() Must Be Called Explicitly After vc.start()"
aliases: [filterlocations-trigger, locations-callback-empty, browse-locations-init]
tags: [urnetwork, sdk, gotcha, locations]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# URnetwork filterLocations() Must Be Called Explicitly After vc.start()

`LocationsViewController.start()` initializes the controller and registers listeners, but does NOT immediately invoke `addFilteredLocationsListener` callbacks with the current regions and cities. The callback only fires when `filterLocations(query)` is called. Without the explicit `filterLocations("")` call after `start()`, the location picker shows only countries (which arrive via a separate initialization path) — regions and cities never appear.

## Key Points

- `vc.start()` alone: only country-level data arrives in callbacks
- `filterLocations("")` must be called explicitly after `vc.start()` to trigger region+city data delivery
- This matches upstream `BrowseLocations.kt` behavior — URnetwork's own app always calls `filterLocations("")` after controller init
- Query delegation: `setSearchQuery(query)` → `vc.filterLocations(query)` (not a local filter over a cached list)
- The empty-string call is a "load all" trigger, not a search — passing `""` means "show everything"

## Details

### Symptom

Location picker showed only countries after `UrnetworkLocationsViewModel.refreshOnce()`. Regions and cities were absent despite the URnetwork SDK having location hierarchy support. The code correctly subscribed to `addFilteredLocationsListener`, but the callback was never invoked for sub-country levels.

### Root Cause

`LocationsViewController` uses a lazy-delivery model. The controller tracks the current filter query internally. On `start()`, it initializes but does not replay the filter state. Only an explicit `filterLocations(query)` call causes the controller to evaluate the filter and deliver results to all registered listeners.

The upstream URnetwork `BrowseLocations.kt` always calls:
```kotlin
vc.start()
vc.filterLocations("")  // explicit trigger
```

### Fix in UrnetworkLocationsViewModel

```kotlin
private fun refreshOnce() {
    val vc = locationsVc ?: return
    vc.start()
    vc.filterLocations("")  // required: triggers addFilteredLocationsListener with regions+cities
}

fun setSearchQuery(query: String) {
    locationsVc?.filterLocations(query)  // delegate directly, no local cache filter
}
```

### Why Not a Local Filter

`filterLocations` is not a client-side filter over a pre-fetched list. It sends the query to the SDK's internal delivery mechanism. The SDK may perform server-side filtering, apply cached results, or use a different delivery path per query type. Always delegate to `vc.filterLocations()` rather than maintaining a local copy and filtering it.

## Related Concepts

- [[concepts/urnetwork-location-hierarchy-migration]] — `setPreferredLocation` migration and `ConnectLocation` hierarchy
- [[concepts/urnetwork-sdk-integration]] — URnetwork SDK integration overview; VC lifecycle patterns
- [[concepts/hiltviewmodel-toomanyfunctions-decomposition]] — the VM split that isolated this VC lifecycle into `UrnetworkLocationsViewModel`

## Sources

- [[daily/2026-05-19.md]] — Session v0.1.5: root cause: `vc.start()` alone never triggers `addFilteredLocationsListener` with regions+cities; fix: `filterLocations("")` after `vc.start()` in `refreshOnce()`; `setSearchQuery()` delegates to `filterLocations(query)` directly
