---
title: Imported WARP and WireGuard slots must preserve unmodeled raw peer fields
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Imported WARP and WireGuard slots must preserve unmodeled raw peer fields
## Key Points
- The modeled `WarpConfig` does not represent every peer field present in imported raw INI data.
- Saving an imported slot must preserve raw peer lines that the model cannot round-trip.
- Fields such as `PresharedKey` can be lost if the save path rebuilds the entire peer block from modeled data alone.
- The fix path in the log was to merge or preserve raw INI content instead of discarding it.
## Details
The daily log records a review finding where re-saving an imported WARP/WireGuard configuration could lose peer information that was not modeled in `WarpConfig`. That makes the save path lossy even when the user only changes seemingly unrelated values such as name, DNS, or endpoint.

The key constraint is that the app must preserve what it cannot faithfully parse. If the implementation cannot model a raw peer field, it has to carry that field through the round trip unchanged. That makes this concept a close sibling of [[warp-awg-field-preservation-contract]] and [[backup-awg-field-roundtrip-loss]].
## Related Concepts
- [[warp-awg-field-preservation-contract]]
- [[backup-awg-field-roundtrip-loss]]
- [[warp-config-import-naming-dedup]]
- [[warp-slot-corrupt-json-resilience]]
## Sources
- `daily/2026-06-03.md`: said that saving an imported WARP/WireGuard raw INI could drop unmodeled peer fields such as `PresharedKey`.
