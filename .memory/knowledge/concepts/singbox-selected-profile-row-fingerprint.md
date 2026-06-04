---
title: Singbox restart fingerprints must include the selected profile row payload
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Singbox restart fingerprints must include the selected profile row payload
## Key Points
- A fingerprint based only on cached DataStore keys can miss edits to the active profile row.
- The selected profile's current `beanBlob` must participate in restart detection.
- DAO row changes and cached selection keys can drift independently.
- If the fingerprint misses the selected row payload, runtime restart is not triggered when the user edits the active profile.
## Details
The log describes a bug in manual Singbox mode where the active profile could be updated in the database without changing the cached selection key. In that situation, a restart fingerprint that looks only at the cached key will stay stale even though the active row contents changed.

The fix direction is to fingerprint the selected row itself, or at minimum its current `beanBlob`, so the restart boundary follows the real data source. This aligns with [[singbox-subscription-architecture]] and the broader restart rules captured by [[engine-runtime-config-restart-observer-stateflow-tests]].
## Related Concepts
- [[singbox-subscription-architecture]]
- [[engine-runtime-config-restart-observer-stateflow-tests]]
- [[post-release-app-test-harness-regression-map]]
- [[runtime-restart-application-scope-observer]]
## Sources
- `daily/2026-06-03.md`: recorded that the Singbox fingerprint must use the selected profile row's current `beanBlob`, not only the cached `BEAN_KEY`.
