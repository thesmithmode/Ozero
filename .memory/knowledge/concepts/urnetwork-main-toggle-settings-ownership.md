---
title: "URnetwork main toggles moved to engine settings ownership"
sources:
  - "daily/2026-05-18.md"
created: 2026-06-12
updated: 2026-06-12
---
# URnetwork main toggles moved to engine settings ownership

## Key Points
- URnetwork `fixedIp` and enhanced-anonymization toggles belong to the engine settings owner, not `MainViewModel`.
- Duplicating the same config writes in MainScreen and engine settings creates a race-prone dual source of truth.
- Simple mode should not expose advanced URnetwork controls; Expert mode should keep the main screen dense.
- Peer count belongs inline in the exit-node/IP card when it describes the selected URnetwork endpoint.

## Details

The initial URnetwork toggle implementation placed `fixedIp` and enhanced-anonymization controls on the main screen. That made Simple mode noisy and consumed Expert mode space. More importantly, it gave both `MainViewModel` and `UrnetworkEngineSettingsViewModel` write access to the same config store fields.

The refactor removed `UrnetworkMainToggleSection` and the corresponding MainViewModel state/setters. `UrnetworkEngineSettingsScreen` now owns the compact toggles, while `IpInfoCard` receives optional URnetwork peer count/search duration data for inline display. This keeps advanced engine configuration inside the per-engine UI contract described by [[concepts/per-engine-ui]].

This is a UI ownership rule, not only a layout cleanup. `MainScreen` should present current runtime status and high-frequency actions; engine settings own engine-specific configuration. The result aligns with [[concepts/engine-runtime-config-provider-boundary]] and reduces the risk of conflicting writes from two view-models.

## Related Concepts
- [[concepts/per-engine-ui]]
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/urnetwork-control-network-modes]]
- [[concepts/urnetwork-location-stability-privacy-icons]]

## Sources
- [[daily/2026-05-18]]: Session "URnetwork toggles relocate" records removal of main-screen URnetwork toggles and moving `allowDirect`/fixed IP controls into engine settings.
- [[daily/2026-05-18]]: Session 22:39 records the single-owner rationale: `MainViewModel` no longer injects `UrnetworkConfigStore`, and engine settings owns the toggles.
