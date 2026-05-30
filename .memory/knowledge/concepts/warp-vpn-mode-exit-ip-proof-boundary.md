---
title: WARP VPN-mode exit IP proof boundary
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# WARP VPN-mode exit IP proof boundary

## Summary
WARP VPN mode must not display an exit IP unless the IP was obtained through a proven engine-owned route; direct HTTP probing and peer endpoint labels are not proof of the actual egress IP.

## Key Points
- WARP proxy mode may expose exit IP through `ViaSocks` when a live local SOCKS route is available.
- WARP VPN/TUN mode must not fall back to direct HTTP IP fetch, because that can show the device IP.
- The Cloudflare peer endpoint is not equivalent to the site-visible exit IP and should not be displayed as such.
- `ProviderLabel` or label-only fallback is safer than showing an unproven IP for WARP VPN mode.

## Details
The exit-node UI work on 2026-05-30 confirmed that Ozero's display contract is route-driven, not a simple UI formatting issue. For proxy-based paths, [[concepts/exit-node-display-viasocks-known-ip-contract]] allows IP display when the request is routed through a known local SOCKS port. For WARP VPN mode, that proof is missing unless the engine owns an explicit safe probe.

This boundary protects the same invariant as [[concepts/exit-node-probe-no-direct-fallback]] and [[connections/engine-exit-node-safe-routing-contract]]: exit-node display must never use direct fallback when the result could be the device's real network IP. A WARP peer endpoint can identify a tunnel peer, but it does not prove what public IP a target website observes.

The practical result is asymmetric behavior. WARP proxy mode can return `ViaSocks(127.0.0.1, port)` when a live port exists. WARP VPN mode should keep a label-only or unavailable state until an engine-owned probe can produce verifiable `ip + countryCode`.

## Related Concepts
- [[concepts/exit-node-display-viasocks-known-ip-contract]]
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[connections/engine-exit-node-safe-routing-contract]]
- [[concepts/warp-uapi-handshake-polling]]

## Sources
- [[daily/2026-05-30]]: Review findings about WARP VPN-mode IP display were rejected because direct fetch would show the device IP and peer endpoint is not egress proof.
- [[daily/2026-05-30]]: The exit-node plan kept URnetwork location-only and required WARP/FPTN IP display only through safe engine routes such as `ViaSocks`.
