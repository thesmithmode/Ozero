---
title: "WARP Config Naming and Duplicate Detection"
aliases: [warp-config-dedup, warp-config-naming, warp-fingerprint-dedup]
tags: [warp, config, ux, dedup, fingerprint]
sources:
  - "daily/2026-05-21.md"
created: 2026-05-21
updated: 2026-05-21
---

# WARP Config Naming and Duplicate Detection

Two product decisions for WARP config management: (1) generated config names derive from the endpoint hostname (`substringBeforeLast(':')` from peer endpoint), producing human-readable names like `WARP <endpoint-host>` instead of opaque numeric IDs; (2) duplicate detection uses a content fingerprint (privateKey + peerPublicKey + peerEndpoint), throwing `WarpConfigDuplicateException` if the same logical config is imported twice.

## Key Points

- Generated config name: `"WARP ${peerEndpoint.substringBeforeLast(':')}"` — strips port, keeps host
- Import name: `filename.substringBeforeLast('.')` — uses the `.conf` filename without extension
- Fingerprint: `privateKey + peerPublicKey + peerEndpoint` — identifies a unique WireGuard tunnel; same config from different sources is detected
- `WarpConfigDuplicateException` returned from import, shown in UI as "данная конфигурация уже загружена"
- Up to 100 copies of the same config could previously be loaded — no uniqueness check existed

## Details

### Config Naming

Before this change, auto-generated WARP configs received names based on internal slot numbering (e.g., `Ozero-<numbers>`), which carried no information about the server endpoint. User-imported `.conf` files also lost their filename context.

After: generated configs display the endpoint hostname (IP or domain) without port. Example: a config with `Endpoint = 162.159.193.10:2408` gets name `WARP 162.159.193.10`. Imported configs use the filename: `warp-home.conf` → name `warp-home`. This gives the user a stable, meaningful label.

### Duplicate Detection

Duplicate check is performed at import time by computing a fingerprint from the three fields that uniquely identify a WireGuard peer connection: `privateKey`, `peerPublicKey`, and `peerEndpoint`. The check scans existing slots before insertion. If a match is found, the import is rejected with a typed exception rather than a silent overwrite or silent acceptance.

This prevents the scenario where a user imports the same `.conf` file multiple times (e.g., by accident after re-downloading), accumulating functionally identical slots that waste UI space and rotation budget.

### What Is Not Deduplicated

Different configs that share only a `peerPublicKey` (same WARP backend, different private key rotation) are considered distinct and will both be accepted. The fingerprint requires all three fields to match.

## Related Concepts

- [[concepts/warp-config-generator-api]] — WARP mirror API that generates configs; naming applies to both generated and imported
- [[concepts/warp-slot-corrupt-json-resilience]] — per-slot try/catch preserving valid slots during parse; related config slot management
- [[concepts/amnezia-wg-warp-migration]] — WarpConfig structure with 19 keys including privateKey, peerPublicKey, peerEndpoint

## Sources

- [[daily/2026-05-21.md]] — Session 13:45: user reported opaque generated names and no dedup check; substringBeforeLast name fix + WarpConfigDuplicateException fingerprint implementation
