---
title: URnetwork picker ProvideMode parity
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# URnetwork picker ProvideMode parity

## Key Points
- Fresh users can have `localState.provideMode = ProvideModeNone`, which hides regions and cities in `filterLocations`.
- The reference `DeviceManager` overrides `provideMode` to `ProvideModePublic` when provide control mode is `ALWAYS`.
- Device-field writing should be centralized so `runStartOnMain` and `ensureDeviceOnMain` cannot drift.
- LazyColumn keys for locations need identity-level uniqueness when duplicate city names exist.

## Details

The URnetwork picker regression came from incomplete parity with the reference device initialization path. Fresh users started with `ProvideModeNone`, so SDK callbacks did not expose regions and cities. The fix extracted a single `applyDeviceFields(device, localState)` helper and applied the reference override: when provide control mode is `ALWAYS`, write `ProvideModePublic`.

The same task family also exposed a Compose key hazard: two Moscow entries had the same name and country code, so composite keys collided. Using `System.identityHashCode(it.location)` for all four sections made the list key stable for duplicate display names.

## Related Concepts
- [[concepts/urnetwork-filteredlocations-bestmatches]]
- [[concepts/urnetwork-lazycolumn-key-collision]]
- [[concepts/urnetwork-provide-data-flow]]
- [[connections/upstream-parity-verification-pattern]]

## Sources
- [[daily/2026-05-21.md]] records the fresh-user `ProvideModeNone` root cause and the reference `DeviceManager.kt:105` override to `ProvideModePublic`.
- [[daily/2026-05-21.md]] records the duplicate Moscow key crash and identity-hash key fix across all picker sections.
