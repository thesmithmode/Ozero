---
title: "Engine Status Bar: Symmetric 50/50 Column Split"
aliases: [ipinfocard-split, engine-status-split, status-bar-columns]
tags: [ui, compose, architecture, urnetwork]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-20
---

# Engine Status Bar: Symmetric 50/50 Column Split

`IpInfoCard` on the main screen uses two `Column(weight=1f)` inside a `Row` to create a symmetric 50/50 layout: left column shows "Выходной узел" + IP/flag/loading/error; right column is engine-specific (URnetwork: "Пиры:" + peer count; other engines: `Spacer(weight=1f)`). The right column visibility is gated by `isUrnetworkVisibleInMain(tunnelState, manualEngine)` — not by `manualEngine == EngineId.URNETWORK` — to correctly handle auto-mode where the active engine is URnetwork but `manualEngine` is null.

## Key Points

- Left column: always present — `IpCardExitNodeValue` (IP text, country flag, loading indicator, error state)
- Right column: `IpCardPeerValue` (peer count) only when `isUrnetworkVisibleInMain` returns true; otherwise `Spacer(weight=1f)` to maintain symmetry
- Bug before fix: gated by `manualEngine == EngineId.URNETWORK` → in auto-mode `manualEngine = null` → right column = Spacer → user saw all content centered in a single block
- `isUrnetworkVisibleInMain(tunnelState, manualEngine)` detects URnetwork as the active engine regardless of manual/auto mode
- `ExpertStatusBadges` receives full `tunnelState` to derive URnetwork visibility internally
- Sentinel: `MainScreenUrnetworkTogglesSentinelTest` verifies call-site uses `isUrnetworkVisibleInMain` (not raw engine comparison)

## Details

### Layout Structure

```kotlin
Row(modifier = Modifier.fillMaxWidth()) {
    // Left: exit node (always)
    Column(modifier = Modifier.weight(1f)) {
        Text("Выходной узел", style = ...)
        IpCardExitNodeValue(tunnelState, ...)
    }
    // Right: engine-specific
    val urnetworkActive = isUrnetworkVisibleInMain(tunnelState, manualEngine)
    if (urnetworkActive) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.engine_status_peers_title), style = ...)
            IpCardPeerValue(tunnelState, ...)
        }
    } else {
        Spacer(modifier = Modifier.weight(1f))
    }
}
```

### The Auto-Mode Bug

In manual mode, `manualEngine` is set to whichever engine the user explicitly selected (e.g., `EngineId.URNETWORK`). In auto-mode, `manualEngine = null` — the StrategyEngine selects the best engine automatically. When URnetwork wins the auto-selection, `tunnelState.runningEngine == EngineId.URNETWORK`, but the old guard `manualEngine == EngineId.URNETWORK` evaluated to false. The right column fell back to `Spacer`, and the peer count was invisible to users in auto-mode.

`isUrnetworkVisibleInMain` inspects `tunnelState` to check the *actual running engine*, not the manual selection preference. This correctly handles both modes with a single predicate.

### i18n Keys

Three new string resources added for the right column:
- `engine_status_peers_title` — "Пиры:" (section header)
- `engine_status_peers_searching_value` — "поиск..." (while discovering peers)
- `engine_status_peers_unavailable` — "—" (unknown)

These keys must exist in all baseline locales (`ru`, `en`, `es`, `pt`). Missing from `values-es`/`values-pt` caused `I18nKeyParityTest` CI failure, fixed in session v0.1.5-2.

### Naming Conventions

`IpCardExitNodeValue` and `IpCardPeerValue` are composable functions (not data classes). Names are intentionally parallel to signal symmetric layout roles. Prior naming used generic `IpValue` which obscured the two-column intent.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] — URnetwork peer count comes from SDK; peer discovery state feeds IpCardPeerValue
- [[concepts/per-engine-ui]] — engine-specific UI sections; this status bar is the main-screen counterpart
- [[concepts/vpn-engine-pipeline]] — StrategyEngine / ManualEngineSource wiring determines which engine is active in auto-mode
- [[concepts/urnetwork-filterlocations-trigger]] — filterLocations("") trigger context; URnetwork-specific content patterns

## Sources

- [[daily/2026-05-19.md]] — v0.1.5 follow-up: IpInfoCard refactored to Row with two Column(weight=1f); left=IpCardExitNodeValue, right=IpCardPeerValue for URnetwork; auto-mode bug found (manualEngine==null in auto → right column Spacer); fixed via isUrnetworkVisibleInMain(tunnelState, manualEngine); sentinel added; i18n CI fail for es/pt fixed
