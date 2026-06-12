---
title: "ByeDPI Reference Parity Must Declare Its Scope"
aliases: [byedpi-parity-scope, byedpi-yaml-vs-builder-scope, reference-parity-scope-discipline]
tags: [byedpi, reference, audit, architecture, gotcha]
sources:
  - "daily/2026-05-19.md"
created: 2026-06-12
updated: 2026-06-12
---

# ByeDPI Reference Parity Must Declare Its Scope

ByeDPI parity claims must specify the exact layer being compared. A YAML-level match with ByeByeDPI does not prove parity for `VpnService.Builder`, init order, underlying networks, stats pollers, native wrapper argv, or health-monitor side effects. Treat every "matches upstream" conclusion as scoped evidence, not a blanket proof.

## Key Points

- HEV YAML parity covers `HevTunnelConfig.toYaml()` only; it does not cover Android `VpnService.Builder` behavior.
- Later audits showed that a YAML-only comparison missed other pipeline layers, including init order, DNS count, MTU assumptions, and VPN teardown signals.
- Advisor hypotheses are useful only after primary-source comparison; the 2026-05-19 log records both false and corrected WARP/ByeDPI hypotheses.
- Device-visible traffic failure requires checking the runtime path: argv source, HEV YAML, TUN builder, native fd lifecycle, and external VPN slot state.
- New parity articles should state "scope warning" when they intentionally compare only one layer.

## Details

The 2026-05-19 work repeatedly moved between symptom fixes and reference comparisons. Some fixes were later classified as "крутыли" because they changed a timeout or default args without proving the actual layer. The strongest later findings came from reading the reference implementation and naming exactly what was compared.

The same pattern appeared in ByeDPI. `HevTunnelConfig.toYaml()` could be aligned with upstream while real traffic still failed due to another layer. A scoped parity article remains useful, but only if readers do not generalize it into a claim that the entire VPN pipeline is identical. This discipline links [[concepts/byedpi-hev-pipeline-upstream-parity]] with the fuller divergence map in [[concepts/byedpi-vpn-pipeline-upstream-divergence]].

The rule also protects WARP work. `handle=0` was first misread as invalid from logs, then corrected by primary-source verification of the AWG handle allocator. For both engines, root-cause confidence requires evidence at the owning layer rather than convenient symptom correlation.

## Related Concepts

- [[concepts/byedpi-hev-pipeline-upstream-parity]] - Scoped YAML and IPv6-blackhole parity evidence.
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] - Fuller multi-layer ByeDPI pipeline map.
- [[concepts/warp-awg-handle-zero-valid]] - Example of correcting a false root cause by reading primary source.
- [[connections/byedpi-reference-parity]] - Broader relationship between ByeByeDPI parity and Ozero bugs.

## Sources

- [[daily/2026-05-19.md]] - Sessions v0.1.5-2, v0.1.5-4, v0.1.6, and 22:35: previous WARP/ByeDPI fixes were audited as symptom fixes; `handle=0` interpretation was reversed through primary-source reading; ByeDPI YAML parity was confirmed but later treated as insufficient to prove full pipeline parity.
