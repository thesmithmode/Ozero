---
title: Exit-node display ViaSocks and known-IP contract
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# Exit-node display ViaSocks and known-IP contract
## Summary
Ozero exit-node display must be driven by engine strategies, not UI guesses: URnetwork remains country-only, while engines that can prove a routed IP source should return `ViaSocks` or a known `ip + countryCode`.
## Key Points
- `MainScreen` already supports both country-only and country-plus-IP modes; the source contract belongs in `ExitNodeStrategy` and `ExitNodeResolver`.
- URnetwork stays `LocationOnly(country,countryCode)` and intentionally shows only flag plus country.
- WARP/FPTN should show flag plus IP only through a safe engine-owned route, such as `ViaSocks(127.0.0.1, port)` or a known resolved server IP.
- Direct HTTP fallback is unsafe for proxy/TUN engines because it can display the device IP instead of the exit node.
## Details
The Karing comparison clarified the architectural difference. Karing uses one direct WAN-IP path through `NetworkUtils.getOutletIp(...)` and renders `countryFlag + ip` in a single card. Ozero instead has a per-engine strategy contract. `ViaSocks` means the resolver can safely fetch through an engine-owned local SOCKS route; `LocationOnly` and `ProviderLabel` normally do not provide an IP.

The chosen Ozero contract keeps the UI stable and moves responsibility to the engine/resolver boundary. URnetwork remains location-only because that is the intended product behavior. WARP should expose `ViaSocks` when a working local SOCKS port is available, and fall back to the previous label/unavailable behavior otherwise. FPTN can provide an already resolved `ip + countryCode` through the provider-label path when the engine has a trustworthy server IP, without making a direct network request.

The security constraint is central: a generic direct IP request is not valid proof of an exit-node IP unless the route is known to pass through the selected engine. This extends the earlier no-direct-fallback rule from sing-box and proxy engines to the broader exit-node widget contract.
## Related Concepts
- [[concepts/exit-node-strategy-resolver-contract]]
- [[connections/engine-exit-node-safe-routing-contract]]
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
## Sources
- [[daily/2026-05-30]]: Karing was found to use `_updateWanIP()` through `NetworkUtils.getOutletIp(...)` and render flag plus IP.
- [[daily/2026-05-30]]: Ozero was diagnosed as strategy-driven through `ExitNodeStrategy` and `ExitNodeResolver`, not a single UI fetch.
- [[daily/2026-05-30]]: The accepted product contract keeps URnetwork as flag plus country only.
- [[daily/2026-05-30]]: The implementation direction rejected direct HTTP fallback for proxy/TUN engines without proof of routed traffic.
