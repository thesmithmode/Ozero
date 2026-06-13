---
title: Exit node strategy no-direct-leak sentinel
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# Exit node strategy no-direct-leak sentinel

## Summary
Exit node display tests must assert that proxy-route failures never fall back to direct HTTP, because direct fallback can leak or display the device IP as the exit node.

## Key Points
- For proxy engines, failed SOCKS or routed probes should produce an error or unavailable state, not direct IP fetch.
- `serverAddress` from a profile is not a reliable final public exit for chain, auto-select, CDN, Reality, or SNI paths.
- URnetwork should remain location-only when only country/flag is trustworthy.
- WARP should use a provider label until a safe routed probe path is proven.

## Details
The sing-box exit IP work confirmed that the correct signal is a real HTTP request through the active outbound graph. Karing reference behavior supported the same principle: probe through proxy/outbound instead of parsing profile fields.

The broader architectural decision was to introduce an engine-level `ExitNodeStrategy` and `ExitNodeResolver`, moving policy out of `MainViewModel`. The sentinel requirement is the safety boundary: a failed routed probe must not silently degrade to direct HTTP, because showing a precise but wrong device IP is worse than showing no exit IP.

## Related Concepts
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[concepts/exit-node-strategy-resolver-contract]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[connections/engine-exit-node-safe-routing-contract]]

## Sources
- [[daily/2026-05-29]]: The sing-box fix added a dedicated local SOCKS inbound for IP probe so the request traverses the full outbound graph.
- [[daily/2026-05-29]]: The exit-node unification plan required tests that `ViaSocks` failure does not call direct fetch, and that location/provider strategies do not perform HTTP probes.
