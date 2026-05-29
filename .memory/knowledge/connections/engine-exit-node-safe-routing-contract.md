---
title: Engine exit node safe routing contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Engine exit node safe routing contract

## Summary
Exit-node display must be owned by engine routing strategy because direct fallback, profile parsing, and UI-level engine heuristics can all show the wrong public IP.

## Key Points
- sing-box proxy-chain exit IP should be measured through a local SOCKS inbound that traverses the full outbound graph.
- Direct HTTP fallback after a proxy-probe failure is unsafe because it can expose and display the device IP.
- URnetwork can legitimately provide location-only output when SDK signals country/flag but not a reliable public IP.
- WARP should use a provider label until there is a proven safe route for real exit-IP probing.

## Details
The sing-box issue revealed a broader architecture boundary. The UI cannot infer the exit node from profile fields, engine names, or direct network probes. For chains, auto-select, CDN, Reality, and SNI scenarios, the configured server address is not necessarily the final public exit. The reliable signal is an actual request routed through the engine's active outbound path.

This led to the `ExitNodeStrategy`/`ExitNodeResolver` direction. Engines should declare whether they support SOCKS probing, location-only reporting, provider labeling, or no exit-node data. The resolver can then avoid unsafe fallbacks and provide consistent UI states without adding engine-specific branches to `MainViewModel`.

## Related Concepts
- [[concepts/exit-node-strategy-resolver-contract]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[concepts/ip-probe-route-architecture]]

## Sources
- [[daily/2026-05-29]]: karing reference confirmed exit IP is determined by an HTTP request through proxy/outbound, not by parsing profile server address.
- [[daily/2026-05-29]]: sing-box was changed to use a dedicated local SOCKS inbound for IP probe so proxy chains show the final exit IP.
- [[daily/2026-05-29]]: direct fallback was rejected for proxy-based engines because it can show the real device IP.
