---
title: "URnetwork Location Picker — LazyColumn Key Collision via identityHashCode"
aliases: [urnetwork-lazycolumn-duplicate, urnetwork-location-key-collision, identity-hashcode-key]
tags: [urnetwork, compose, lazycolumn, gotcha, crash]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# URnetwork Location Picker — LazyColumn Key Collision via identityHashCode

When two `ConnectLocation` objects share the same `name` and `countryCode` (e.g., two Moscow entries), using a composite key of `"${name}_${countryCode}"` across all four LazyColumn sections (bestMatches, countries, regions, cities) produces duplicate keys. Compose throws `IllegalArgumentException: Key already used` at runtime.

## Key Points

- Root cause: two Moscow entries with identical `name + countryCode` → composite string key collides across sections
- Compose `LazyColumn` requires globally unique keys within the entire list, not just per-section
- Fix: `System.identityHashCode(it.location)` as the key — unique per object instance regardless of content equality
- Applied to all 4 sections: `bestMatches`, `countries`, `regions`, `cities`
- `identityHashCode` is suitable here because location objects are SDK-owned value objects that are not mutated; the same logical location always produces the same SDK instance within one filterLocations callback

## Details

### The Collision

The URnetwork location picker renders four sections in a single `LazyColumn`. Each section uses a keyed `items` block:

```kotlin
items(bestMatches, key = { "${it.name}_${it.countryCode}" }) { ... }
items(countries, key = { "${it.name}_${it.countryCode}" }) { ... }
items(regions, key = { "${it.name}_${it.countryCode}" }) { ... }
items(cities, key = { "${it.name}_${it.countryCode}" }) { ... }
```

When `bestMatches` contains a Moscow entry (name="Moscow", countryCode="RU") and `regions` or `cities` also contains Moscow, Compose sees the same key string in the same lazy list → crash.

### Fix

```kotlin
items(bestMatches, key = { System.identityHashCode(it.location) }) { ... }
items(countries,   key = { System.identityHashCode(it.location) }) { ... }
items(regions,     key = { System.identityHashCode(it.location) }) { ... }
items(cities,      key = { System.identityHashCode(it.location) }) { ... }
```

`System.identityHashCode` returns the JVM object identity hash — unique per instance, not per content. For SDK-provided location objects that are distinct instances even when logically equal, this guarantees uniqueness. The trade-off is that Compose cannot reuse composable state when the underlying object is recreated (different instance), but location objects are re-emitted by the SDK on each `filterLocations` callback call, so this is acceptable.

### Why Multiple Sections Can Collide

`FilteredLocations` from the URnetwork SDK returns 5 lists: `bestMatches`, `countries`, `regions`, `cities`, and `providers`. A city like Moscow appears in `bestMatches` (when it's a top search result) AND in `cities`. Since both lists feed into the same `LazyColumn`, the same location name produces a duplicate key even though it logically represents different rows in different sections.

## Related Concepts

- [[concepts/urnetwork-filteredlocations-bestmatches]] — FilteredLocations 5-list SDK structure; bestMatches as top search results
- [[concepts/urnetwork-location-hierarchy-migration]] — `setPreferredLocation(ConnectLocation?)` migration context
- [[concepts/urnetwork-providemmode-regions-cities]] — provideMode fix that enables regions/cities to appear in the picker

## Sources

- [[daily/2026-05-21.md]] — Task14: two Moscow entries with same `name+countryCode` → composite key collision → `IllegalArgumentException`; fix: `identityHashCode(it.location)` for all 4 sections; commit `b14f7c42`
