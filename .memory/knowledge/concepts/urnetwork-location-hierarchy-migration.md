---
title: "URnetwork Location Hierarchy: Country to ConnectLocation Migration"
aliases: [urnetwork-location-migration, setPreferredLocation, findBestMatch, connect-location-hierarchy]
tags: [urnetwork, sdk, architecture, refactoring, gotcha]
sources:
  - "daily/2026-05-18.md"
  - "daily/2026-05-22.md"
created: 2026-05-18
updated: 2026-05-22
---

# URnetwork Location Hierarchy: Country to ConnectLocation Migration

URnetwork SDK uses `ConnectLocation` as a universal entity representing country, region, or city. Ozero's initial integration stored only `urnetworkCountryCode: String?` in `EngineConfig`, losing region/city granularity. The migration replaced `setPreferredCountry(String?)` with `setPreferredLocation(ConnectLocation?)` across Bridge, Engine, ViewModel, and persistence layers. A `findBestMatch` helper provides fuzzy location matching with a critical guard: city names must be filtered by `countryCode` to avoid globally ambiguous matches.

## Key Points

- `ConnectLocation` is universal in URnetwork SDK: same type for country, region, and city — not a three-level hierarchy of separate APIs
- Migration touched 4 layers atomically: Bridge API, `EngineUrnetwork.start`, `UrnetworkEngineSettingsViewModel`, persistence in `DataStoreUrnetworkConfigStore`
- `findBestMatch` helper matches user-selected location against SDK's available locations list with region-to-city fallback
- Critical bug found by code reviewer: `findBestMatch` matched city by name without filtering by `countryCode` — globally ambiguous city names (e.g., "Springfield") could connect to wrong country
- Fix: filter by `countryCode` first, then match city name within that country; return null if no match (not a random same-name city in another country)
- 6 separate `selectedCountry`/`selectedRegion`/`selectedCity` getter/setter pairs consolidated into `UrnetworkLocationSelection` data class (detekt TooManyFunctions 23>20 fix)

## Details

### The Granularity Gap

The original URnetwork integration stored location as a single `urnetworkCountryCode: String?` in the engine config. This was sufficient for country-level selection but lost the user's region and city preferences. When the reference app (`.claude/Kontekst/android/`) was analyzed, it revealed a three-tier location picker: country → region → city, all using the SDK's `ConnectLocation` type. The reference app stores the full `ConnectLocation` (with `connectLocationId`, `name`, `country`, `region`, `city` fields) rather than just a country code.

### Bridge API Change

Before:
```kotlin
interface UrnetworkSdkBridge {
    fun setPreferredCountry(countryCode: String?)
}
```

After:
```kotlin
interface UrnetworkSdkBridge {
    fun setPreferredLocation(location: ConnectLocation?)
}
```

The `ConnectLocation` parameter carries all granularity levels. When `location.city` is non-null, the SDK routes through city-level peers. When only `location.country` is set, the SDK selects any peer in that country. The engine calls `setPreferredLocation` during `start()` to apply the user's saved preference.

### findBestMatch and the City Ambiguity Bug

`findBestMatch` resolves a persisted `UrnetworkLocationSelection` (which may reference a city no longer in the SDK's available list) against the current list of available locations. The matching logic cascades: exact `connectLocationId` match → country+region+city name match → country+region match → country match.

The code reviewer (session 16:59) found that the city name match step did not filter by `countryCode`. City names are globally ambiguous: "Springfield" exists in the US (Illinois, Missouri, Massachusetts, etc.) and potentially in other countries. Without the `countryCode` guard, `findBestMatch("Springfield", countryCode="US")` could return a `ConnectLocation` for a "Springfield" in an unexpected country if that entry appeared first in the available locations list.

Fix (commit `ff7f5044`): city name matching requires `countryCode` match as a precondition. If no city in the correct country matches, return null rather than a same-name city in the wrong country.

### Consolidation into Data Class

The original bridge and config store had 6 separate methods for location components: `selectedCountry()/setSelectedCountry()`, `selectedRegion()/setSelectedRegion()`, `selectedCity()/setSelectedCity()`. This pushed `DataStoreUrnetworkConfigStore` past detekt's `TooManyFunctions` threshold (23 > 20).

The consolidation replaced these with:
```kotlin
data class UrnetworkLocationSelection(
    val countryCode: String?,
    val regionCode: String?,
    val cityName: String?
)
```

One `Flow<UrnetworkLocationSelection>` and one `suspend fun setLocationSelection(UrnetworkLocationSelection)` replaced 6 methods. This also moved `writeOrRemove` from an interface method to a top-level extension function (see [[concepts/extension-function-import-migration-trap]] for the consumer-side import trap this caused).

### Reference Implementation Discovery

The migration was prompted by the user's demand for feature parity with the URnetwork reference app. An analysis agent discovered that the reference app uses `openLocationsViewController()` which is available offline after `DeviceLocal` init (without IoLoop/tunFd). This means location browsing does not require an active VPN connection — a UX improvement that requires a bootstrap coordinator to initialize the SDK bridge early.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - Parent integration article; location hierarchy is part of the engine configuration surface
- [[concepts/urnetwork-location-token-best-available]] - LocationToken persistence; bestAvailable flag roundtrip loss is a related serialization trap
- [[concepts/extension-function-import-migration-trap]] - The writeOrRemove extraction from UrnetworkConfigStore caused import errors in 3 consumer files
- [[concepts/suppress-annotation-decomposition]] - TooManyFunctions fix via data class consolidation rather than @Suppress
- [[connections/self-review-insufficient-code-reviewer-required]] - findBestMatch city ambiguity was a HIGH finding from 5-reviewer code review session

### Offline Location Selection Fix (2026-05-22)

`UrnetworkEngineSettingsViewModel.selectLocation()` had an early return:

```kotlin
fun selectLocation(location: ConnectLocation) {
    if (!isUrnetworkActive) return  // ← blocked offline selection
    bridge.setPreferredLocation(location)
    settingsRepository.setUrnetworkCountryCode(location.country)
}
```

This prevented users from pre-selecting a country while URnetwork was not the active engine. The early return was incorrect — `bridge.setPreferredLocation()` operates on an `AtomicReference` internally and does not require an active VPN connection to store the preference. `settingsRepository.setUrnetworkCountryCode` is a pure DataStore write.

Fix: remove the guard entirely. Both calls are safe to invoke unconditionally. The SDK bridge will apply the stored location preference on the next `EngineUrnetwork.start()` call.

## Sources

- [[daily/2026-05-18.md]] - Session 15:02: Bridge API migrated setPreferredCountry→setPreferredLocation(ConnectLocation?); findBestMatch helper; all call sites atomic migration; .memory files separated from feat commit
- [[daily/2026-05-18.md]] - Session 13:08: hotfix 6c33c98f consolidated 6 selectedCountry/Region/City functions into UrnetworkLocationSelection data class (detekt TooManyFunctions 23>20)
- [[daily/2026-05-18.md]] - Session 16:59: code reviewer found findBestMatch city-by-name without countryCode filter → wrong-country connect risk
- [[daily/2026-05-18.md]] - Session 19:55: fix commit ff7f5044 — findBestMatch filters by countryCode, early return null on no match
- [[daily/2026-05-22.md]] - Session 19:47: `selectLocation` early return `if (!isUrnetworkActive) return` blocked offline country selection; removed — `bridge.setPreferredLocation()` is AtomicReference-safe offline
