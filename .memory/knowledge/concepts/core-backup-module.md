---
title: "Core Backup Module Design"
aliases: [ozero-backup, settings-export-import, core-backup]
tags: [architecture, backup, android, storage]
sources:
  - "daily/2026-05-05.md"
created: 2026-05-05
updated: 2026-05-05
---

# Core Backup Module Design

Ozero's `:core-backup` module provides export/import of all application settings across all engines in a single JSON file via Android's Storage Access Framework (SAF). The decision to create this module emerged from an adversarial review of the WARP slot store, which identified that slot corruption would lose all user configurations without a recovery path.

## Key Points

- Scope: ALL engine settings in one file — all WARP slots, URnetwork config, ByeDPI args, other engine configs
- Format: JSON (human-readable, no binary encoding)
- Delivery mechanism: SAF file picker for both export (write) and import (read)
- WireGuard private keys appear in plaintext in the exported file — a non-blocking warning is shown on the export screen; user is responsible for file security
- No file-level encryption or password protection — explicitly out of scope
- Threat vector mitigated by existing `allowBackup=false` + `data_extraction_rules` in manifest — system auto-backup already excluded; SAF export is intentional and user-initiated

## Details

### Motivation: Corrupt JSON Slot Loss

The WARP multi-slot system stores all configuration slots as a JSON array in a single DataStore key. If JSON becomes corrupt (e.g., partial write, migration error, or SDK bug), the entire array becomes unreadable. Without a backup mechanism, the user loses all named WARP configurations permanently. The `:core-backup` module is the recovery path.

The immediate code-level fix (per-slot try/catch in `DataStoreWarpConfigSlotStore.parseSlots`) addresses partial corruption — individual corrupt slots are skipped, preserving the rest. But full JSON corruption or accidental deletion cannot be recovered without an external backup.

### SAF Integration

SAF (`android.provider.DocumentsContract` + `Intent.ACTION_CREATE_DOCUMENT` / `Intent.ACTION_OPEN_DOCUMENT`) allows writing/reading files to user-chosen locations (Downloads, Google Drive, USB OTG) without requiring `WRITE_EXTERNAL_STORAGE` permission. The export flow:

1. User taps "Export settings" → Intent launches SAF file picker
2. User chooses save location → SAF returns URI
3. Module serializes all DataStore keys across all engines → writes JSON to URI
4. Confirmation shown with file path

Import mirrors this: user picks file → module deserializes → writes to DataStore keys with validation.

### WireGuard Key Warning

WireGuard private keys (generated in `:engine-warp` for AmneziaWG) are stored in DataStore and included in the export. The decision was to include them (required for full restore) and show a non-intrusive warning on the export screen rather than blocking export or requiring additional authentication. This is consistent with how password managers handle "show password" operations — the user is informed, not blocked.

The existing `allowBackup=false` in AndroidManifest and `data_extraction_rules` already prevent system-initiated backup of DataStore files (which would be invisible to the user and unencrypted). SAF export is intentional and user-visible.

### Exclusions

Not included in export scope:
- Active VPN session state
- Crash logs / boot logs (diagnostic artifacts, not configuration)
- Runtime cache

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] - The WARP engine whose slot configuration triggered this module's design
- [[concepts/warp-slot-corrupt-json-resilience]] - The immediate fix for partial slot corruption; backup module is the recovery path for total loss
- [[concepts/vpnservice-builder-traps]] - Historical context on why careful storage design matters for engine configs

## Sources

- [[daily/2026-05-05.md]] - Session 12:01: adversarial review found corrupt JSON → all slots lost; decision to build core-backup module with SAF export/import, JSON format, WG key warning non-blocking, no encryption
