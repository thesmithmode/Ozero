---
title: Exit node UI needs engine-declared strategies
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- Exit node display must be driven by an engine-declared strategy, not ad hoc UI branching.
- Proxy engines should probe through their active outbound path and must not fall back to direct HTTP after probe failure.
- URnetwork can be location-only; WARP can use a provider label until a safe routed probe exists.
- Unknown engines should default to unavailable rather than potentially showing the device IP.

## Details
The 2026-05-29 sing-box and exit-node work identified a UI architecture issue: `MainViewModel` knew too much about engine-specific IP discovery. A unified `ExitNodeStrategy` and resolver contract was chosen so each engine declares the safe source for exit-node information.

The central safety rule is that wrong IP is worse than no IP. For sing-box and other proxy-chain engines, the IP probe must traverse the active outbound graph, such as through a dedicated local SOCKS inbound. If that route fails, the UI must show an error or unavailable state instead of making a direct request that can expose and display the device IP.

## Related Concepts
- [[concepts/exit-node-strategy-resolver-contract]]
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[connections/engine-exit-node-safe-routing-contract]]

## Sources
- [[daily/2026-05-29]] records the decision to introduce `ExitNodeStrategy` and `ExitNodeResolver`.
- [[daily/2026-05-29]] records that sing-box exit IP must be probed through a local SOCKS inbound to traverse the full outbound graph.
- [[daily/2026-05-29]] records that URnetwork should remain location-only and WARP should use a provider label until a safe probe route exists.
