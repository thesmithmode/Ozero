---
title: Exit node probe no direct fallback
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Exit node probe no direct fallback

## Key Points
- Exit IP should be measured through the active engine route, not inferred from profile `serverAddress`.
- For proxy-chain engines, a failed SOCKS/proxy probe must not fall back to direct HTTP from the app process.
- sing-box needs a dedicated local SOCKS inbound for IP-probe so the probe traverses the whole outbound graph.
- Engines without safe route proof should expose provider/location-only state rather than a possibly wrong public IP.

## Details
The sing-box IP issue showed that direct IP checks can bypass the active engine route in TUN/per-app/excludeSelf scenarios and display the device's real public IP. The accepted fix direction is to probe through a local sing-box SOCKS inbound dedicated to exit IP detection, matching the principle observed in Karing: determine exit by real traffic through the current proxy/outbound, not by parsing profile fields.

The broader contract is defensive. If a routed probe fails, showing an error or provider/location-only state is preferable to falling back to direct HTTP. WARP can safely show a provider label until a routed WARP probe exists, and URnetwork can continue showing location-only data when the SDK provides country/flag but not a verified public IP.

## Related Concepts
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[concepts/exit-node-strategy-resolver-contract]]
- [[concepts/ip-probe-route-architecture]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-29]] records the decision to route sing-box exit IP checks through a dedicated local SOCKS inbound.
- [[daily/2026-05-29]] records the architectural rule that direct fallback can leak or display the real device IP and should be forbidden for proxy-based routes.
