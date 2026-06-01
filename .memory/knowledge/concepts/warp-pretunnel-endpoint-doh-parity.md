---
title: WARP pre-tunnel endpoint DoH parity
sources:
  - daily/2026-06-01.md
created: 2026-06-01
updated: 2026-06-01
---
# WARP pre-tunnel endpoint DoH parity

## Summary
WARP endpoint hostname resolution before VPN startup is separate from in-tunnel DNS and can affect which Cloudflare edge IP the engine uses. Ozero should preserve explicit user resolver choices, but its default/fallback pre-tunnel resolver needs PORTAL WG parity when comparing the same WARP `.conf`.

## Key Points
- `DNS = ...` inside a WireGuard config controls DNS available after the tunnel is established, not the pre-tunnel lookup for `Endpoint`.
- Ozero defaulted WARP endpoint resolution to `DoHProvider.SYSTEM`, while PORTAL WG evidence showed Cloudflare DoH `https://1.1.1.1/dns-query`.
- Different pre-tunnel endpoint IPs can change regional egress behavior even when the raw `.conf` is identical.
- Split tunnel `BLOCKLIST` and IPv6 fail-closed policy were rejected as the primary bug when the affected app is intended to be tunneled.
- Explicit user selection of `SYSTEM` must remain respected.

## Details
The June 1 investigation separated three DNS layers: the imported WARP config, the DNS assigned to applications inside the VPN, and the resolver used before startup to resolve a hostname endpoint such as `engage.cloudflareclient.com`. The user clarified that comma-separated `DNS = 1.1.1.1` in UI/config does not control this pre-tunnel endpoint lookup.

The durable contract is that WARP parity investigations must compare the pre-tunnel resolver path, not only cryptographic keys or INI preservation. PORTAL WG behavior pointed to Cloudflare DoH for endpoint resolution, so Ozero's SYSTEM fallback became a confirmed candidate for regional drift. The fix direction was intentionally narrow: change default/fallback endpoint resolve to Cloudflare DoH while preserving an explicit user-chosen SYSTEM resolver.

This concept links to [[concepts/warp-proxy-config-wgquick-precedence]] because both are WARP parity traps where a superficial `.conf` comparison is insufficient. It also links to [[concepts/warp-vpn-mode-exit-ip-proof-boundary]] because direct or pre-tunnel network paths can produce misleading evidence about where traffic really exits.

## Related Concepts
- [[concepts/warp-proxy-config-wgquick-precedence]]
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
- [[concepts/warp-readiness-delayed-handshake-contract]]
- [[connections/engine-exit-node-safe-routing-contract]]

## Sources
- [[daily/2026-06-01]]: The log records that Ozero used `DoHProvider.SYSTEM` for WARP endpoint resolve while PORTAL WG used Cloudflare DoH, and that this can change endpoint IP before the tunnel starts.
- [[daily/2026-06-01]]: The user rejected split-tunnel `BLOCKLIST` as a bug and clarified that in-tunnel DNS is different from pre-tunnel endpoint DNS.
