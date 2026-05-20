---
title: "URnetwork FilteredLocations bestMatches Field"
aliases: [urnetwork-bestmatches, filteredlocations-sdk, urnetwork-search-results]
tags: [urnetwork, sdk, locations, ui]
sources:
  - "daily/2026-05-20.md"
created: 2026-05-20
updated: 2026-05-20
---

# URnetwork FilteredLocations bestMatches Field

The URnetwork SDK `FilteredLocations` object contains 5 separate lists: `bestMatches`, `countries`, `regions`, `cities`, and `devices`. `bestMatches` is the top search result list — when the user types a query like "Mos", the SDK populates `bestMatches` with the most relevant results (e.g. Moscow). Ignoring `bestMatches` causes empty location search results even when the SDK returns correct data.

## Key Points

- `FilteredLocations` has 5 lists: `bestMatches`, `countries`, `regions`, `cities`, `devices`
- `bestMatches` = top search hits, populated by the SDK based on query string matching
- `updateLocations()` callback must read ALL 5 lists — ignoring any one loses data
- UI must show a "Лучшие совпадения" / "Best Matches" section before the country list
- All 5 lists need representation in the location picker UI

## Details

### The Missing bestMatches Bug

The location picker `updateLocations()` implementation was mapping `filtered.countries`, `filtered.regions`, `filtered.cities`, and `filtered.devices` into UI sections but skipping `filtered.bestMatches` entirely. When a user typed a search query, the SDK performed matching and returned relevant results in `bestMatches`, but the ViewModel discarded them. The UI showed empty results for every search query even though the SDK returned correct data.

The fix adds an `allBestMatches` list in the ViewModel that is populated from `filtered.bestMatches`. The UI renders a "Лучшие совпадения" section at the top of the location list, before countries, only when `allBestMatches` is non-empty.

### SDK Contract

The `filterLocations(query)` call must be invoked explicitly after `vc.start()` to trigger the initial population of all 5 lists (see [[concepts/urnetwork-filterlocations-trigger]]). The `bestMatches` list is the primary intended result for search queries — it is the field the SDK optimizes for relevance ranking. The per-type lists (`countries`, `regions`, `cities`) are secondary browsing lists, not search-result lists.

## Related Concepts

- [[concepts/urnetwork-filterlocations-trigger]] - filterLocations("") must be called after start() to initialize all lists
- [[concepts/urnetwork-location-hierarchy-migration]] - setPreferredLocation migration and ConnectLocation selection
- [[concepts/urnetwork-location-token-best-available]] - bestAvailable flag round-trip loss in DataStore

## Sources

- [[daily/2026-05-20.md]] - Session 13:00: FilteredLocations 5-list structure discovered; bestMatches was being ignored → empty search results; fix: allBestMatches VM field + "Лучшие совпадения" UI section
