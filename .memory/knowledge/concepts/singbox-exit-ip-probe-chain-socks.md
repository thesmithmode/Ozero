---
title: "sing-box exit IP probe through chain SOCKS"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-06-13
---
# sing-box exit IP probe through chain SOCKS
## Key Points
- sing-box exit IP must be measured by a real HTTP probe through the active outbound graph, not by parsing profile `serverAddress`.
- In TUN/per-app/excludeSelf mode, direct app-process probes can show the device IP rather than the engine exit IP.
- A dedicated local SOCKS inbound for IP-probe routes the check through sing-box and supports proxy chains, auto-select, detour, Reality, SNI, and CDN cases.
- Direct fallback after SOCKS probe failure is unsafe for proxy-based engines because it can leak or display the real device IP.
## Details
The 2026-05-29 investigation concluded that the "exit node" shown for sing-box was wrong because the diagnostic probe could bypass the active tunnel/outbound graph. In proxy-chain configurations, the profile's server address is often only an intermediate hop; the final public exit must be observed from traffic that actually traverses the chain.

The chosen fix is a dedicated local SOCKS inbound inside the sing-box runtime only for IP probing. The app then uses `IpProbeRoute.Socks("127.0.0.1", port)` so the HTTP check exits through sing-box. This follows the same principle found in karing reference behavior: determine outlet IP by a real request through proxy/outbound, not by static config parsing.

This concept is a concrete implementation case for [[concepts/exit-node-strategy-resolver-contract]]. The policy consequence is that failure to probe through the engine should produce an error or provider/location-only state, not a direct HTTP fallback.

The sing-box fix was later generalized into engine-owned exit-node strategy. The dedicated SOCKS route remained the concrete sing-box strategy, while UI fallback policy moved out of `MainViewModel` so a failed routed probe could not silently degrade to direct device IP.
## Related Concepts
- [[concepts/exit-node-strategy-resolver-contract]] - General contract for engine-owned exit-node display strategy.
- [[concepts/singbox-chain-dns-hijack-parity]] - Another sing-box chain parity issue.
- [[concepts/singbox-karing-json-import-parity]] - Uses karing/sing-box parity as reference guidance.
- [[concepts/ip-probe-route-architecture]] - Existing IP probe routing architecture.
- [[connections/multi-engine-lifecycle-exitnode-regression-loop]] - Links sing-box IP display to the same staged runtime regression cleanup.
## Sources
- [[daily/2026-05-29]]: records the sing-box issue where "Выходной узел" could show the wrong IP.
- [[daily/2026-05-29]]: records the decision to add a dedicated local SOCKS inbound for the IP probe.
- [[daily/2026-05-29]]: records karing as reference for real HTTP probing through proxy/outbound.
- [[daily/2026-05-29]]: records commit `8bdebb50` as the later unified exit-IP contract change on `dev`.
