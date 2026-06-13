---
title: Exit-node ProviderLabel known-IP contract
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-05-31
---
# Exit-node ProviderLabel known-IP contract

## Key Points
- Exit-node display data must come from engine-owned evidence, not direct HTTP fallback.
- `ProviderLabel` may carry a known IP and country code when the engine already has trustworthy exit evidence.
- FPTN can display a resolved server IP through this path, while WARP VPN mode remains label-only without an engine-owned probe.
- URnetwork stays `LocationOnly`, showing flag and country without IP.
- This refines [[concepts/exit-node-display-viasocks-known-ip-contract]] and [[concepts/warp-vpn-mode-exit-ip-proof-boundary]].

## Details

The 2026-05-30 IP-display work clarified that Ozero differs from Karing by design. Karing updates a single WAN IP path through its proxy port and renders flag plus IP. Ozero uses `ExitNodeStrategy`, so the UI can only show IP when a strategy supplies safe IP evidence, such as `ViaSocks` or a known IP carried by the provider label.

The accepted contract keeps URnetwork as country-only because its location is an engine-specific selection state, not a site-visible IP proof. WARP proxy mode can use `ViaSocks` when a live local SOCKS port exists. WARP VPN mode must not use direct fetch because that can show the device IP rather than the routed egress. FPTN can expose a resolved IPv4 server address and country code through an enriched `ProviderLabel` without issuing a new unsafe HTTP request.

The key boundary is that UI rendering is not the owning layer. The resolver and engine strategies must decide whether the displayed IP is route-owned, known, or unavailable. If no safe route or trusted known IP exists, fallback label/unavailable behavior is preferable to showing a misleading IP.

## Related Concepts
- [[concepts/exit-node-display-viasocks-known-ip-contract]]
- [[concepts/exit-node-strategy-ui-unification]]
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
- [[concepts/fptn-websocket-resolved-ip-readiness]]

## Sources
- [[daily/2026-05-30]]: User requirement was URnetwork flag+country only, while other engines should show flag+IP when safely possible.
- [[daily/2026-05-30]]: The implementation decision was to keep UI unchanged and adjust engine strategies/resolver data.
- [[daily/2026-05-30]]: Direct HTTP fallback was rejected for proxy/TUN scenarios because it can reveal the device IP.
- [[daily/2026-05-30]]: `ProviderLabel` was extended for known IP/country evidence, with FPTN using a resolved IPv4 and WARP VPN mode remaining label-only.
