---
title: "Engine UI state ownership feedback loop"
sources:
  - "daily/2026-05-18.md"
created: 2026-06-12
updated: 2026-06-12
---
# Engine UI state ownership feedback loop

## Key Points
- Runtime status labels, main-screen summaries, and engine settings controls must each have one owner.
- Moving advanced toggles out of MainScreen removed duplicate config writers while keeping runtime summary visible.
- Nullable probing labels and engine-aware wording show that status UI is not a generic string table problem.
- URnetwork peer count inline display works because it is status data, while fixed IP/enhanced anonymity are configuration data.

## Details

The 2026-05-18 UI work exposed one repeated ownership problem across different surfaces. MainScreen originally mixed runtime status, advanced URnetwork configuration, and engine-specific status badges. That made Simple mode too complex and allowed two view-models to write the same URnetwork settings. The fix split responsibilities: MainScreen reports status and high-level state; engine settings own configuration.

The same boundary appears in status text handling. `TunnelState.Probing` cannot be rendered as one universal label because ByeDPI, WARP, reconnecting flows, and unknown-engine probing have different meanings. The label helper therefore needs both nullable engine handling and engine-aware branches. This connection links [[concepts/urnetwork-main-toggle-settings-ownership]] with [[concepts/tunnelstate-probing-null-engineid-contract]] and [[concepts/per-engine-ui]].

The practical rule is to classify each UI element before placing it: status summaries may live on MainScreen, engine-specific knobs belong in engine settings, and recovery/probing labels belong to the state-label layer with exact engine semantics.

## Related Concepts
- [[concepts/urnetwork-main-toggle-settings-ownership]]
- [[concepts/tunnelstate-probing-null-engineid-contract]]
- [[concepts/per-engine-ui]]
- [[concepts/engine-runtime-config-provider-boundary]]

## Sources
- [[daily/2026-05-18]]: Session 22:39 records the move of URnetwork toggles to engine settings and peer count into `IpInfoCard`.
- [[daily/2026-05-18]]: Sessions 11:54, 16:30, and 18:55 record engine-aware probing label fixes and the nullable `Probing.engineId` regression.
