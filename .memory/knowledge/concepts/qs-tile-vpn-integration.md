---
title: "Quick Settings Tile VPN Integration"
aliases: [qs-tile, quick-settings-tile, ozero-tile]
tags: [android, ui, vpn, tileservice, architecture]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# Quick Settings Tile VPN Integration

Ozero implements an Android Quick Settings (QS) tile via `OzeroQuickTile` (extends `TileService`). The tile uses a "smart toggle" interaction model: if an engine is selected, it toggles VPN on/off; if no engine is selected, it opens the main app. The tile existed as a stub before 2026-05-22 session and was completed in-place rather than recreated.

## Key Points

- Icon: `ozero_logo_white.png` — existing asset, no new icon needed; Android auto-tints by alpha channel
- Interaction: smart toggle — toggle if engine selected, open app if no engine selected (currently opens app; Intent-to-VpnService toggle deferred)
- State sync: `TileService` cannot bind to a service directly → observes `TunnelController.state` flow between `onStartListening`/`onStopListening`
- Always check existence before planning new class — `OzeroQuickTile.kt` was already present as stub
- `TileService` lifecycle: `onStartListening` = tile visible, `onStopListening` = tile hidden; collect flow only within this window

## Details

### Smart Toggle Model

Three possible tile tap behaviors were considered:
1. **Open** — always opens app
2. **Toggle** — toggles VPN regardless of state
3. **Smart** — toggle if engine selected, open if not

Smart was chosen because toggling without an engine selected produces a no-op (no engine = nothing to start). Opening the app guides the user to configure one. After engine selection, subsequent taps become true toggles.

The current implementation taps open the app in all cases. The toggle path (sending an Intent to `OzeroVpnService`) is architecturally straightforward but was not implemented in this session — it can be added as a separate commit.

### TileService State Observation

`TileService` runs in a separate system-level process context — it cannot bind to `OzeroVpnService` or access `TunnelController` via direct reference. The correct pattern is:

1. In `onStartListening`: launch a coroutine that collects `TunnelController.state` and calls `updateTile()` on each emission
2. In `onStopListening`: cancel the coroutine

This ensures the tile reflects the current tunnel state (Active/Inactive/Connecting) without holding system resources when not visible.

### Icon Strategy

`ozero_logo_white.png` is an all-white bitmap with transparency defining the shape. Android's `TileService` framework tints the icon using the system's active/inactive color based on tile state — no per-state icon variants needed. Creating a separate tile icon was explicitly rejected to avoid asset duplication.

### Stub Existence Pattern

`OzeroQuickTile.kt` existed in the codebase as an empty `TileService` subclass with no implementation. This is a standard Android pattern when the manifest entry is added pre-emptively. Discovering the stub before starting work prevented creating a duplicate class with a slightly different name.

## Related Concepts

- [[concepts/vpn-engine-pipeline]] — `TunnelController.state` flow that the tile observes for state sync
- [[concepts/engine-telegram-mtproxy]] — another coordinator that observes `TunnelController.state` via combine; same observation pattern
- [[concepts/vpnservice-god-object-decomposition]] — decomposed VpnService; tile communicates via Intent, not direct binding

## Sources

- [[daily/2026-05-22.md]] — Session 19:25: about_description removal; QS tile smart-toggle implementation; `OzeroQuickTile.kt` stub confirmed; `ozero_logo_white` icon; TunnelController.state collection between onStartListening/onStopListening; toggle-via-Intent deferred
