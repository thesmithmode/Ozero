---
title: WARP Proxy Config wgQuick Precedence
sources:
  - daily/2026-05-31.md
created: 2026-06-01
updated: 2026-06-01
---
# WARP Proxy Config wgQuick Precedence

## Key Points
- PORTAL WG chooses `wgQuick` before `amQuick` for Cloudflare WARP proxy configs.
- Ozero choosing `amQuick` first can import AWG or obfuscation fields that differ from the reference path.
- A WARP leak where PORTAL WG works but Ozero fails should compare config extraction before changing routes blindly.
- The suspected fix point is `ProxyWarpAutoConfig.extractFromJson()` plus a regression test for precedence.

## Details

After commit `ce9e3ff5`, the user reported that PORTAL WG WARP configs opened resources that Ozero still leaked or failed to reach. The investigation shifted to comparing Ozero against the PORTAL WG reference implementation rather than patching routing from symptoms.

One concrete drift was identified: PORTAL WG prefers `wgQuick` before `amQuick`, while Ozero's `ProxyWarpAutoConfig.extractFromJson()` selected `amQuick` first. For Cloudflare WARP, that can choose a different config flavor with AWG-specific fields or obfuscation settings. This extends [[concepts/warp-vpn-mode-exit-ip-proof-boundary]] and [[concepts/regression-diagnostics-real-path-grounding]]: the first root-cause check is reference config selection, then TUN routes, DNS, IPv6, app filters, `setUnderlyingNetworks(null)`, and socket protection.

## Related Concepts
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
- [[concepts/regression-diagnostics-real-path-grounding]]
- [[concepts/warp-ipv6-fail-closed-routing]]
- [[concepts/warp-allowedips-tun-routing]]

## Sources
- [[daily/2026-05-31]]: User reported PORTAL WG WARP configs worked where Ozero leaked or failed.
- [[daily/2026-05-31]]: Initial comparison found PORTAL WG prefers `wgQuick` before `amQuick`, while Ozero chose `amQuick` first in `ProxyWarpAutoConfig.extractFromJson()`.
