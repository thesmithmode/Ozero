---
title: "URnetwork Fixed IP and Enhanced Anonymization Main Screen Toggles"
aliases: [fixed-ip-toggle, enhanced-anonymization, allow-direct, urnetwork-main-toggles]
tags: [urnetwork, ui, architecture, android, compose]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# URnetwork Fixed IP and Enhanced Anonymization Main Screen Toggles

Two URnetwork settings are exposed directly on the main screen (both Simple and Expert modes): "Фиксированный IP" (`fixedIpSize`) and "Усиленная анонимизация" (`enhancedAnonymization = !allowDirect`). These map to SDK's `PerformanceProfile` fields and are visible only when URnetwork is the active engine (manual selection or active connection). A hidden bug in `applyPerformanceProfile` was discovered: AUTO mode with `allowDirect=false` was silently skipped.

## Key Points

- `enhancedAnonymization` = `!allowDirect` — mirrors bringyour `ConnectActions.kt:251`; disables direct peer connections, forcing multi-hop
- `fixedIpSize` = `windowSizeMin=1, windowSizeMax=1` — single peer per session = stable exit IP
- Toggles visible via `isUrnetworkVisibleInMain(state, manualEngine)` — true when `manualEngine=URNETWORK` OR `state.engineId=URNETWORK` (Probing/Connecting/Connected/Failed)
- Hidden bug found: `applyPerformanceProfile` skipped ALL `AUTO` WindowType profiles regardless of `allowDirect` value — `allowDirect=false` with `AUTO` was silently ignored
- DataStore key: `urnetwork_allow_direct` (default `true`); `fixedIpSize` already existed
- 11 behavioral sentinels in MainViewModelTest + 11 UI sentinels in MainScreenUrnetworkTogglesSentinelTest

## Details

### The allowDirect Bug

`applyPerformanceProfile` in `EngineUrnetwork.start()` had a guard:

```kotlin
if (windowType == AUTO) return  // skip profile application entirely
```

This was correct for the case where WindowType=AUTO means "let SDK decide everything." But when the user enabled "Усиленная анонимизация" (sets `allowDirect=false`), the AUTO guard prevented the profile from being applied — `allowDirect` remained at its default `true`, defeating the anonymization toggle.

The fix: apply the profile whenever `allowDirect != true` OR `fixedIpSize`, regardless of WindowType:

```kotlin
if (windowType == AUTO && allowDirect && !fixedIpSize) return
val profile = PerformanceProfile()
profile.windowType = when (windowType) { ... }
profile.allowDirect = allowDirect
if (fixedIpSize) {
    val sizes = WindowSizeSettings()
    sizes.windowSizeMin = 1
    sizes.windowSizeMax = 1
    profile.windowSize = sizes
}
device.performanceProfile = profile
```

### Visibility Gating

The toggles must only appear when URnetwork is relevant to the current session:

```kotlin
internal fun isUrnetworkVisibleInMain(
    state: TunnelState,
    manualEngine: EngineId?
): Boolean = manualEngine == EngineId.URNETWORK ||
    state.engineId == EngineId.URNETWORK
```

This covers: user selected URnetwork manually (even before connecting), or the current tunnel state shows URnetwork as the active engine (during probing, connecting, connected, or failed states).

### Probing Label Nullable EngineId Regression

During the same session, a regression was found from StatusLabel decomposition (commit a9bfad06): `probingLabelRes(engineId: EngineId, ...)` expected non-null, but `TunnelState.Probing.engineId` is `EngineId?` (null = unknown engine during probing). Fix: accept `EngineId?` with null → fallback to generic `main_status_probing`. Sentinel `StatusLabelProbingNullSentinelTest` added.

### Reference Implementation

Enhanced Anonymization maps directly to bringyour's `ConnectActions.kt:251`:
- `allowDirect = true` (default): SDK can connect directly to exit peers — faster but less private
- `allowDirect = false` ("Усиленная анонимизация"): forces multi-hop routing through relay peers — slower but traffic cannot be attributed to a single peer

Fixed IP maps to `WindowSizeSettings(min=1, max=1)`: the SDK assigns exactly one exit peer per session. The external IP remains stable for the session duration, useful for services that bind sessions to IP addresses.

## Related Concepts

- [[concepts/urnetwork-window-type-modes]] - WindowType (Auto/Web/Streaming) is the third profile dimension alongside allowDirect and fixedIpSize
- [[concepts/urnetwork-control-network-modes]] - ControlMode (AUTO/ALWAYS) and NetworkMode (WIFI/ALL) — separate from performance profile settings
- [[concepts/per-engine-ui]] - Toggles on main screen complement the dedicated UrnetworkEngineSettingsScreen

## Sources

- [[daily/2026-05-18.md]] - Session 16:09: allowDirect=false bug found in applyPerformanceProfile (AUTO guard skipped all profiles); Session 18:55: implementation complete — probingLabelRes nullable fix, 11+11 sentinel tests, committed 229cd275
- [[daily/2026-05-18.md]] - Session 16:30: CI fix for probingLabelRes regression from StatusLabel decomposition
