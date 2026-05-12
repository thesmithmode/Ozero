---
title: "URnetwork ControlMode and NetworkMode Configuration"
aliases: [urnetwork-control-mode, urnetwork-network-mode, provide-control-mode]
tags: [urnetwork, sdk, engine, configuration]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# URnetwork ControlMode and NetworkMode Configuration

URnetwork engine in Ozero exposes two orthogonal configuration dimensions: `ControlMode` (AUTO/ALWAYS) controlling when the P2P engine provides bandwidth to the network, and `NetworkMode` (WIFI/ALL) controlling which network types are used. Defaults are ALWAYS/WIFI matching the upstream SDK behavior. The naming trap: `provideControlMode` in URnetwork upstream corresponds to "provide bandwidth" control, NOT to the `WindowType` modes (Auto/Web/Streaming) which control traffic routing — these are separate configuration surfaces.

## Key Points

- `ControlMode.AUTO` — SDK decides when to provide bandwidth; `ControlMode.ALWAYS` — always provide (default)
- `NetworkMode.WIFI` — only use WiFi for P2P relay (default); `NetworkMode.ALL` — use cellular too (battery impact)
- Naming trap: `provideControlMode` ≠ `WindowType` — former controls bandwidth contribution, latter controls traffic routing quality
- Persisted in `DataStoreUrnetworkConfigStore` with DataStore preferences keys
- Applied in `EngineUrnetwork.start()` during SDK initialization — requires VPN restart on change

## Details

### ControlMode

`ControlMode` determines the URnetwork SDK's bandwidth contribution behavior. In `ALWAYS` mode, the device acts as a P2P relay whenever connected — contributing bandwidth to the URnetwork mesh and earning credits. In `AUTO` mode, the SDK uses heuristics (battery level, network type, device idle state) to decide when to contribute.

For Ozero users, `ALWAYS` is the correct default because URnetwork integration is opt-in (user explicitly selects the engine). Users who select URnetwork expect it to function continuously, not intermittently based on SDK heuristics.

### NetworkMode

`NetworkMode` restricts which network interfaces the P2P relay uses. `WIFI` (default) prevents cellular data usage for relay traffic — important because P2P relay can consume significant bandwidth, and cellular data is metered for most users. `ALL` allows cellular relay, useful for users with unlimited data plans who want maximum P2P participation.

### UI Integration

Both modes are exposed in `UrnetworkEngineSettingsScreen` as toggle/selector components. The ViewModel reads from `DataStoreUrnetworkConfigStore` and writes changes back. Changes require VPN restart because the SDK configuration is set during `EngineUrnetwork.start()` and cannot be hot-reloaded.

### Naming Distinction from WindowType

The upstream URnetwork SDK has two separate configuration surfaces that can be confused:

| Configuration | Values | Controls | Ozero Location |
|--------------|--------|----------|----------------|
| `provideControlMode` | AUTO, ALWAYS | When device provides bandwidth to mesh | UrnetworkEngineSettings |
| `WindowType` | AUTO, QUALITY, SPEED | Traffic routing quality/latency tradeoff | UrnetworkEngineSettings (separate section) |

Both are per-engine settings, but they affect different aspects of the P2P engine behavior. See [[concepts/urnetwork-window-type-modes]] for WindowType details.

## Related Concepts

- [[concepts/urnetwork-window-type-modes]] - WindowType (Auto/Web/Streaming) — the other URnetwork configuration surface, distinct from ControlMode
- [[concepts/urnetwork-sdk-integration]] - Parent integration article; ControlMode/NetworkMode are part of the engine configuration surface
- [[concepts/per-engine-ui]] - Settings screens where these modes are exposed

## Sources

- [[daily/2026-05-12.md]] - Session 17:55: URnetwork ControlMode (AUTO/ALWAYS) and NetworkMode (WIFI/ALL) implemented; defaults ALWAYS/WIFI matching upstream; provideControlMode≠WindowType naming trap documented; engineAutoPriority default order changed to WARP/URNETWORK/BYEDPI
