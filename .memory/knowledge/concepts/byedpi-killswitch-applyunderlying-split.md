---
title: ByeDPI and killswitch applyUnderlying split
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# ByeDPI and killswitch applyUnderlying split

## Key Points
- ByeDPI engine TUN must use `applyUnderlying=false` to preserve upstream routing parity and avoid QUIC/YouTube regressions.
- Killswitch preliminary startup TUN must use `applyUnderlying=true` to keep the lockdown invariant during WiFi↔Mobile transitions.
- Sentinels must cover both call-sites and assert the explicit value for each path.
- A sentinel that searches only for a literal such as `applyUnderlying = false` can become stale after a default-parameter refactor.

## Details

The split contract separates the production ByeDPI tunnel from the killswitch startup tunnel. The ByeDPI engine path keeps `applyUnderlying=false`, while the killswitch preliminary startup path keeps `applyUnderlying=true`. Treating these as one setting breaks either upstream parity or the lockdown startup invariant.

The regression in the daily log was not a prod-code failure: CI was red because `OzeroVpnServiceLockdownKillswitchTest` did not know about the new `applyUnderlying: Boolean = false` parameter and the second call-site. The durable test shape is to assert the function signature default and the pass-through call, then separately check the killswitch path value.

## Related Concepts
- [[concepts/byedpi-udp-quic-routing]]
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]]
- [[concepts/sentinel-fqn-desync]]
- [[connections/sentinel-trap-family]]

## Sources
- [[daily/2026-05-21.md]] records that ByeDPI engine TUN uses `applyUnderlying=false`, killswitch startup TUN uses `applyUnderlying=true`, and the sentinel failed because it only modeled the old single-call-site contract.
- [[daily/2026-05-21.md]] records that substring/literal sentinel assertions should prefer signature plus pass-through checks after default-parameter refactors.
