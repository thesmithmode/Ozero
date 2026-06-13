---
title: sing-box active SOCKS port failure reset
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# sing-box active SOCKS port failure reset

## Key Points
- `activeSocksPort` must represent a successfully started sing-box probe inbound, not a reserved or half-started port.
- Failed start paths must reset the probe port to `0` so later exit-node probes cannot route through stale state.
- This protects [[concepts/singbox-exit-ip-probe-chain-socks]] from becoming a false positive after partial startup.
- The same pattern belongs to the broader stale-state family described in [[connections/stale-engine-signals-cross-engine-failures]].

## Details

The 2026-05-29 review found that `SingboxEngine` could assign `activeSocksPort` before startup had fully succeeded. If a later failure path did not clear it, the UI/resolver could believe that a safe local SOCKS probe route still existed.

That state is dangerous because sing-box exit IP display depends on a dedicated local SOCKS inbound for probe traffic. A stale port may produce wrong diagnostics, failed probes, or fallback pressure in the exit-node display layer. The correct invariant is simple: a probe port is observable only after the sing-box runtime is ready, and all failed-start paths clear it.

## Related Concepts
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/exit-node-strategy-no-direct-leak-sentinel]]
- [[connections/engine-exit-node-safe-routing-contract]]
- [[connections/stale-engine-signals-cross-engine-failures]]

## Sources
- [[daily/2026-05-29]] records the review finding that `SingboxEngine` sets `activeSocksPort` before successful startup and does not clear it on several failure paths.
- [[daily/2026-05-29]] ties this risk to the new sing-box SOCKS-based exit IP probe and the need to avoid wrong exit-node display.
