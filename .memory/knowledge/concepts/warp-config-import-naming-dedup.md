---
title: WARP config import naming and dedup
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# WARP config import naming and dedup

## Key Points
- Imported WARP slot names should follow the imported file name.
- Generated WARP slot names can use an `Ozero-...` convention derived from stable config digits or endpoint data.
- Duplicate imports must be rejected by fingerprint, not allowed as repeated slots.
- A practical fingerprint is `privateKey + peerPublicKey + peerEndpoint`.

## Details

The daily log records two UX requirements for WARP configs: imported config names must correspond to the source file name, and generated configs should use an Ozero-prefixed generated name. This keeps user-visible slots explainable and avoids anonymous numeric duplicates.

Deduplication is a separate invariant. The implemented approach used `WarpConfigDuplicateException` and compared a fingerprint made from private key, peer public key, and peer endpoint. This blocks repeated imports of the same effective config even if the file is loaded multiple times.

## Related Concepts
- [[concepts/warp-config-naming-dedup]]
- [[concepts/warp-config-generator-api]]
- [[concepts/warp-slot-corrupt-json-resilience]]
- [[concepts/release-checks-beyond-ci]]

## Sources
- [[daily/2026-05-21.md]] records the user requirement that imported WARP config names follow file names and generated configs use an `Ozero-...` naming pattern.
- [[daily/2026-05-21.md]] records the fingerprint-based duplicate check using private key, peer public key, and peer endpoint.
