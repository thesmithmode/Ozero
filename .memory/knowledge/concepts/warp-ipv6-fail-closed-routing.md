---
title: WARP IPv6 fail-closed routing
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# WARP IPv6 fail-closed routing

## Key Points
- Disabling IPv6 in WARP/AWG config is not enough if Android TUN lacks an IPv6 fail-closed route.
- Dual-stack applications can bypass the VPN over real IPv6 when only IPv4 is routed through WARP.
- A fail-closed blackhole route is safer than allowing unowned IPv6 egress.
- WARP leak fixes must be verified against routing behavior, not only reported exit IP.

## Details

On 2026-05-31, the user reported that WARP still leaked the real IP for some services. The recorded fix was to add a fail-closed TUN blackhole route when IPv6 is disabled, because disabling IPv6 in the AWG layer alone can leave Android dual-stack traffic with a real-network IPv6 path outside the VPN.

This concept extends the existing WARP routing contracts: AllowedIPs, TUN routes, and exit-IP display must agree. A service-specific leak can happen even when common probes look correct, because different applications or domains may prefer IPv6. For WARP, the absence of an owned IPv6 route is a security issue, not a harmless missing feature.

## Related Concepts
- [[concepts/warp-allowedips-tun-routing]]
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
- [[concepts/android-vpn-self-traffic-bypass]]
- [[concepts/engine-poisoned-state-recovery-proof]]

## Sources
- [[daily/2026-05-31]]: Session 19:02 records the user report that WARP still leaked real IP for some services.
- [[daily/2026-05-31]]: Session 19:17 records the fail-closed blackhole route decision for disabled IPv6.
- [[daily/2026-05-31]]: Session 20:48 prioritizes fail-closed routing and startup lockdown handling as critical runtime safety work.
