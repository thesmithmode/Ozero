---
title: "LocationToken bestAvailable Field Serialization Gap"
aliases: [locationtoken-bestavailable, best-available-lost-navigation, connectlocation-serialization-gap]
tags: [urnetwork, serialization, bug, navigation, gotcha]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# LocationToken bestAvailable Field Serialization Gap

`LocationToken` did not serialize the `bestAvailable` field. When a user selected the "Best Available" location, the SDK returned `ConnectLocation(bestAvailable=true)`, but `LocationToken.fromConnectLocation` lost this field during conversion. After navigating away and back to the location screen, `selectedLocation != null` but `bestAvailable=false`, causing `isBestAvailable=false` — the UI showed a specific location as selected instead of "Best Available".

## Key Points

- `LocationToken.fromConnectLocation(ConnectLocation)` did not copy `bestAvailable` — the field was silently dropped
- After navigation round-trip, `selectedLocation.bestAvailable == false` even when user originally selected "Best Available"
- `isBestAvailable = selectedLocation?.bestAvailable == true` → `false` → UI desynced
- Fix: add `bestAvailable: Boolean = false` field to `LocationToken`, update `fromConnectLocation` and `toConnectLocation`
- Backward compat: Kotlinx JSON deserializes missing `bestAvailable` field as `false` (the default) — no migration needed

## Details

### The Navigation Bug

The location picker UI uses `LocationToken` as the serializable state object for `savedStateHandle` (or equivalent navigation argument). `LocationToken` encodes the selected location so it survives navigation back stacks, process death, and activity recreation.

`ConnectLocation` is the SDK type with multiple selection modes:

```kotlin
data class ConnectLocation(
    val locationId: String? = null,
    val countryCode: String? = null,
    val bestAvailable: Boolean = false,
    // ...
)
```

The conversion `LocationToken.fromConnectLocation(loc)` copied `locationId` and `countryCode` but omitted `bestAvailable`. When the SDK returned `ConnectLocation(bestAvailable=true)` (the user's "Best Available" selection), the token stored `bestAvailable=false` (the default). On navigation back, `toConnectLocation()` reconstructed a `ConnectLocation` with `bestAvailable=false` — pointing to no specific location but not flagged as "Best Available" either.

### The Fix

```kotlin
data class LocationToken(
    val locationId: String? = null,
    val countryCode: String? = null,
    /** True when user selected "Best Available" (SDK ConnectLocation.bestAvailable). Default false for backward compat. */
    val bestAvailable: Boolean = false,
) {
    companion object {
        fun fromConnectLocation(loc: ConnectLocation) = LocationToken(
            locationId = loc.locationId,
            countryCode = loc.countryCode,
            bestAvailable = loc.bestAvailable,
        )
    }

    fun toConnectLocation() = ConnectLocation(
        locationId = locationId,
        countryCode = countryCode,
        bestAvailable = bestAvailable,
    )
}
```

The KDoc on `bestAvailable` explains the field semantics — it is the bridging field between SDK's `ConnectLocation.bestAvailable` and `LocationToken`'s serialized form.

### Backward Compatibility

Kotlinx serialization with `@Serializable` and a default value (`bestAvailable: Boolean = false`) deserializes JSON objects that lack the field as `false`. Existing stored `LocationToken` JSON (from before this fix) will deserialize with `bestAvailable=false`, which is correct — those tokens represented specific location selections, not "Best Available".

### Root Cause Pattern

This is a class of bug where a new field is added to the source type (SDK `ConnectLocation`) but the conversion/mapping layer (`fromConnectLocation`) is not updated. The compiler does not warn about this because the mapping uses named parameters — adding a new field to the target class with a default value compiles correctly with the old mapping code. Tests that only check the non-null/non-default fields pass without detecting the omission.

Prevention: when adding a field to a type that has `from*` conversion methods, grep for all `from*(` usages and verify each one copies the new field.

## Related Concepts

- [[concepts/urnetwork-filteredlocations-bestmatches]] - "Best Available" selection in the URnetwork location picker
- [[concepts/urnetwork-location-token-best-available]] - Existing article on best-available token (may have earlier version of this concept)
- [[concepts/urnetwork-location-hierarchy-migration]] - Location model changes context

## Sources

- [[daily/2026-05-13.md]] - `LocationToken.fromConnectLocation` lost `bestAvailable` field; after navigation `isBestAvailable=false`; fix = add `bestAvailable: Boolean = false` to `LocationToken` + update `fromConnectLocation`/`toConnectLocation` + KDoc + Kotlinx JSON backward compat default=false
