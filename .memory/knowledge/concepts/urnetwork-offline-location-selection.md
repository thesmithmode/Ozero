---
title: "URnetwork Offline Location Selection"
sources:
  - "daily/2026-05-22.md"
created: 2026-06-12
updated: 2026-06-12
---

# URnetwork Offline Location Selection

URnetwork location selection must be allowed while the URnetwork engine is not currently active. The settings screen writes the preferred country into local state and the bridge can accept `setPreferredLocation()` through an `AtomicReference`, so an active tunnel is not required for the user to choose the next startup location.

## Key Points

- `selectLocation` must not return early just because URnetwork is inactive.
- Offline selection should update both the SDK bridge preferred location and `settingsRepository.setUrnetworkCountryCode`.
- The selected location is startup configuration, not only live engine state.
- Country peer counts from the SDK are `providerCount`, meaning registered nodes, not a guaranteed online peer count.
- The privacy icon reflects `isStrongPrivacy=true`, not an infinity or unlimited-capacity marker.

## Details

The bug was an early return in `selectLocation`: `if (!isUrnetworkActive) return`. That made the settings UI look selectable while silently ignoring the user's country choice whenever URnetwork was disconnected. The correct behavior is to persist the preference offline and let the next engine start consume it.

`bridge.setPreferredLocation()` can be called without an active URnetwork session because the bridge stores the preferred location in an atomic holder. Persisting the same choice through the settings repository keeps UI state, startup configuration, and bridge state aligned. This matches the broader engine-settings rule that configuration controls belong to engine settings, not only to currently connected runtime state.

## Related Concepts

- [[concepts/urnetwork-main-toggle-settings-ownership]] - URnetwork configuration controls belong to the engine settings surface.
- [[concepts/urnetwork-explicit-bestavailable-location]] - Default location selection should use an explicit best-available token.
- [[concepts/urnetwork-locvm-bootstrap-race]] - Location UI can flicker or mislead when initialization and state decisions race.

## Sources

- [[daily/2026-05-22]] - Session 19:47: `selectLocation` had an inactive-engine early return; `bridge.setPreferredLocation()` and `settingsRepository.setUrnetworkCountryCode` can be called unconditionally; SDK country numbers are `providerCount`; privacy icon means `isStrongPrivacy=true`.
