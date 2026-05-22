---
title: "URnetwork provideMode Override — Regions/Cities Visibility Fix"
aliases: [urnetwork-providemmode, urnetwork-regions-cities-empty, urnetwork-providemode-always]
tags: [urnetwork, sdk, providemmode, regions, cities, fresh-user, gotcha]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# URnetwork provideMode Override — Regions/Cities Visibility Fix

Fresh URnetwork users receive `localState.provideMode = ProvideModeNone (0)` from the SDK. When `ensureDeviceOnMain` applies device fields without overriding this, `filterLocations("")` callbacks return empty regions and cities lists — the SDK hides provider locations when `provideMode = None`. Fix: always override `provideMode` to `ProvideModePublic` in `applyDeviceFields`, matching the reference implementation `DeviceManager.kt:105`.

## Key Points

- `ProvideModeNone (0)` causes SDK to return empty `regions` and `cities` in `filterLocations` callback
- Reference: `DeviceManager.kt:105` — `provideMode = if (provideControlMode == ALWAYS) Sdk.ProvideModePublic else localState.provideMode` (always ALWAYS → always Public in practice)
- Fix: extracted `applyDeviceFields(device, localState)` helper applying all 13 device fields in one place; `provideMode` overridden to `ProvideModePublic` unconditionally
- `provideControlMode` normalized via `UrnetworkProvideControlMode.fromRaw` before applying; raw SDK int may be outside enum bounds
- `filterLocations("")` must be called explicitly after `vc.start()` — without it, no callback fires at all (see [[concepts/urnetwork-filterlocations-trigger]])
- This fix is **separate** from `filterLocations("")` call — both are required for locations to appear

## Details

### Root Cause

The `ensureDeviceOnMain` function applied device fields from `localState` without overriding `provideMode`. For fresh users, `localState.provideMode` is `0 = ProvideModeNone` because they have never set a provider preference. The URnetwork SDK interprets `ProvideModeNone` as "this device does not provide bandwidth" and consequently excludes provider-only locations (regions, cities) from `filterLocations` results.

The reference implementation in `DeviceManager.kt:105` always forces `ProvideModePublic` when `provideControlMode == ALWAYS`. In Ozero, `provideControlMode` is always `ALWAYS` by design (we want users to participate in the network). Therefore the override is unconditional.

### applyDeviceFields Helper

Refactored: all 13 device fields moved to a single `applyDeviceFields(device, localState)` helper, creating a single source of truth for both `runStartOnMain` and `ensureDeviceOnMain` call-sites. Previously, fields were scattered across the two call-sites and could drift out of sync.

The 13 fields include: `provideMode`, `provideControlMode`, `locationId`, `connectLocationId`, `routeLocal`, `allowLocalNetworkAccess`, `providePaused`, `ipV6`, `dns`, `blockQuic`, `receiveOrders`, and two others. Missing any field in one call-site causes silent behavioral divergence between initial connect and subsequent reconnect.

### Sentinel

Sentinels assert:
- `applyDeviceFields` helper is the single source of truth for `runStartOnMain` + `ensureDeviceOnMain`
- `ProvideModePublic` override appears in `applyDeviceFields`
- `UrnetworkProvideControlMode.fromRaw` is called before assigning `provideControlMode`

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — full URnetwork integration; ensureDeviceOnMain 12-field symmetry fix from 2026-05-20 preceded this 13-field parity
- [[concepts/urnetwork-filterlocations-trigger]] — `filterLocations("")` must be called explicitly after vc.start()
- [[concepts/urnetwork-location-hierarchy-migration]] — `setPreferredLocation(ConnectLocation?)` migration; location picker population
- [[concepts/urnetwork-filteredlocations-bestmatches]] — FilteredLocations 5-list SDK structure; bestMatches section in picker

## Sources

- [[daily/2026-05-21.md]] — Task15: fresh users got `ProvideModeNone` → empty regions/cities; reference `DeviceManager.kt:105` explicit; `applyDeviceFields` 13-field helper extracted; commit `d953d56d`
