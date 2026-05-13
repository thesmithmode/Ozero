---
title: "WARP Per-Slot DoH Configuration"
aliases: [warp-doh-per-slot, doh-provider-per-config, warp-dns-per-slot]
tags: [warp, dns, doh, architecture, refactor]
sources:
  - "daily/2026-05-13.md"
created: 2026-05-13
updated: 2026-05-13
---

# WARP Per-Slot DoH Configuration

DNS-over-HTTPS (DoH) provider selection for the WARP engine was initially implemented as a global setting (`WarpDoHStore` DataStore key). This was incorrect: different WARP configuration slots may need different DNS strategies (e.g., one slot using Cloudflare DoH for maximum speed, another using AdGuard DoH for ad blocking). The fix moves `doHProvider: DoHProvider` into `WarpConfig` as a per-slot field, serialized alongside other slot configuration.

## Key Points

- `WarpDoHStore` (separate global DataStore key) removed; `doHProvider: DoHProvider = DoHProvider.SYSTEM` added to `WarpConfig` directly
- Backward compat: `optString("doHProvider", "SYSTEM")` in `DataStoreWarpConfigSlotStore.parseSlots` — missing field in old JSON → defaults to `DoHProvider.SYSTEM`
- `WarpEngineSettingsViewModel`: `doHProvider` moved from constructor injection to `WarpEditDraft`; `selectedDoHProvider` StateFlow derived from `uiState.editDraft?.doHProvider`
- `WarpDoHSection` in UI: moved from `SlotListContent` (global UI) to `WarpEditScreen` (per-slot edit UI)
- `EngineWarp`: reads `resolvedConfig?.doHProvider ?: DoHProvider.SYSTEM` directly — no constructor lambda injection

## Details

### Why Per-Slot

The WARP engine supports multiple named configuration slots (e.g., "Work WARP", "Personal WARP", "Locked-down"). Each slot points to different Cloudflare credentials and potentially different endpoints. A slot used in corporate environments might require system DNS (to respect corporate DNS policies), while a personal slot might use Cloudflare DoH for censorship resistance. A global DoH setting cannot satisfy both simultaneously.

The initial global implementation (in `WarpDoHStore`) was a pragmatic first cut that didn't account for this use case. The architectural fix aligns with Ozero's data model where `WarpConfig` is the authoritative per-slot configuration object.

### Serialization Backward Compatibility

`DataStoreWarpConfigSlotStore` serializes `WarpConfig` to JSON and stores all slots in a single DataStore key. Adding `doHProvider` to `WarpConfig` requires backward-compatible deserialization: existing stored JSON (before the migration) lacks the `doHProvider` field. The parser uses `optString("doHProvider", "SYSTEM")` — if the field is absent, it defaults to `DoHProvider.SYSTEM` (pass-through to system DNS, preserving prior behavior).

This pattern follows the established approach from the WARP 8→19 key migration ([[concepts/amnezia-wg-warp-migration]]) where new fields were added with defaults.

### ViewModel Refactoring

Before the change, `WarpEngineSettingsViewModel` had:
- Constructor injection: `private val doHStore: WarpDoHStore`
- Separate StateFlow: `val selectedDoHProvider = doHStore.provider.stateIn(...)`

After the change:
- No `doHStore` constructor parameter
- `doHProvider` is part of `WarpEditDraft` (same object that holds name, publicKey, etc.)
- `selectedDoHProvider` reads from `uiState.editDraft?.doHProvider`
- `onSetDoHProvider(provider)` updates the draft in-place

This simplifies the ViewModel significantly — one fewer injected dependency, one fewer StateFlow, consistent save/discard lifecycle with other draft fields.

### The WhileSubscribed Regression

During implementation, `selectedDoHProvider` was initially declared with `stateIn(WhileSubscribed(0))`. Tests reading `.value` without a subscriber saw `null` instead of the slot's actual `doHProvider`. Fixed by changing to `stateIn(Eagerly)`. See [[concepts/stateIn-eagerly-test-trap]] for the general pattern.

## Related Concepts

- [[concepts/warp-slot-corrupt-json-resilience]] - Same slot store layer; per-slot fields require backward-compat parsing
- [[concepts/amnezia-wg-warp-migration]] - Prior WarpConfig expansion (8→19 keys) using same backward-compat pattern
- [[concepts/stateIn-eagerly-test-trap]] - The WhileSubscribed test trap encountered during this refactor

## Sources

- [[daily/2026-05-13.md]] - Session evening Task #5: `WarpDoHStore` deleted; `doHProvider` added to `WarpConfig`; `WarpDoHSection` moved to per-slot edit screen; backward compat via `optString("doHProvider","SYSTEM")`; `WhileSubscribed(0)` → `Eagerly` fix
