---
title: ByeDPI YouTube device verification boundary
sources:
  - daily/2026-05-21.md
created: 2026-06-12
updated: 2026-06-12
---
# ByeDPI YouTube device verification boundary

## Key Points
- A code-level ByeDPI YouTube hypothesis is not a proven fix without device verification and logs.
- The reported case was CMD mode failing YouTube while Instagram worked, UI mode partially worked, and original ByeByeDPI worked with the same strategy.
- `-Ku` and `default_params.udp` were ruled out as the root cause because original and Ozero native code matched.
- Killswitch-related `setUnderlyingNetworks(null)` was only relevant when killswitch was enabled; it did not prove the user's killswitch-off case.

## Details

The 2026-05-21 investigation went through several hypotheses for ByeDPI CMD YouTube failure. A commit around startup ordering was pushed, but the log explicitly marks the core issue as not proven because the user's real case still needed device verification. The acceptable proof was a latest CI APK, ByeDPI CMD mode, killswitch off, YouTube/video behavior, thumbnail behavior, and `ozero.log`.

The investigation established negative evidence. Both original ByeByeDPI and Ozero had `default_params.udp = 0`, and `-Ku` toggled `params.udp` in both implementations. That ruled out the simplistic "Ozero UDP default differs" theory. The remaining root cause had to be in pipeline, init order, HEV/DNS behavior, build pin drift, or another integration layer, and could not be declared fixed from code inspection alone.

## Related Concepts
- [[concepts/byedpi-cmd-verbatim-pipeline]]
- [[concepts/byedpi-youtube-quic-probe-domain-contract]]
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]]
- [[connections/runtime-symptom-fix-vs-reference-proof-loop]]

## Sources
- [[daily/2026-05-21.md]] records that the ByeDPI YouTube root cause was not confirmed and required device verification with logs.
- [[daily/2026-05-21.md]] records that `default_params.udp` and `-Ku` behavior matched between original ByeByeDPI and Ozero.
