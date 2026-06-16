---
title: Imported WARP and WireGuard slots must preserve unmodeled raw peer fields
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-13
---
# Imported WARP and WireGuard slots must preserve unmodeled raw peer fields
## Key Points
- The modeled `WarpConfig` does not represent every peer field present in imported raw INI data.
- Saving an imported slot must preserve raw peer lines that the model cannot round-trip.
- Fields such as `PresharedKey` can be lost if the save path rebuilds the entire peer block from modeled data alone.
- The fix path in the log was to merge or preserve raw INI content instead of discarding it.
- UI saves that edit only name, DNS, or endpoint still must keep peer secrets from the old raw INI.
## Details
The daily log records a review finding where re-saving an imported WARP/WireGuard configuration could lose peer information that was not modeled in `WarpConfig`. That makes the save path lossy even when the user only changes seemingly unrelated values such as name, DNS, or endpoint.

The key constraint is that the app must preserve what it cannot faithfully parse. If the implementation cannot model a raw peer field, it has to carry that field through the round trip unchanged. That makes this concept a close sibling of [[warp-awg-field-preservation-contract]] and [[backup-awg-field-roundtrip-loss]].

The 2026-06-03 fix direction was to add a preservation mode to the INI builder or an equivalent merge helper. The rebuilt modeled values remain authoritative for fields the app owns, while unmodeled peer lines from the prior raw INI are retained. This keeps imported WARP/WireGuard tunnels stable even when the user performs a normal settings save that does not intentionally touch those peer fields.
## Related Concepts
- [[warp-awg-field-preservation-contract]]
- [[backup-awg-field-roundtrip-loss]]
- [[warp-config-import-naming-dedup]]
- [[warp-slot-corrupt-json-resilience]]
- [[warp-ini-builder-private-branch-coverage]]
## Sources
- `daily/2026-06-03.md`: said that saving an imported WARP/WireGuard raw INI could drop unmodeled peer fields such as `PresharedKey`.
- `daily/2026-06-03.md`: recorded the decision to merge rebuilt config with preserved raw peer lines rather than rebuilding the full peer block from `WarpConfig` alone.
