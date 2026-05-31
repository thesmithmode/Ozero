---
title: WARP IPv6 fail-closed blackhole route
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# WARP IPv6 fail-closed blackhole route

## Summary
When WARP disables IPv6 inside the engine config, Android TUN routing still needs a fail-closed IPv6 route so dual-stack apps cannot bypass WARP through the real network.

## Key Points
- Disabling IPv6 in AWG/WARP config is not enough if Android TUN has no IPv6 route.
- Dual-stack apps may select real IPv6 connectivity when only IPv4 is captured.
- The safe behavior is fail-closed routing for unsupported IPv6 rather than silent bypass.
- IP display fixes must be separated from routing leak fixes.
- This contract extends [[concepts/warp-vpn-mode-exit-ip-proof-boundary]] and [[concepts/warp-allowedips-tun-routing]].

## Details
The 2026-05-31 WARP investigation found that real-IP leakage can remain even after exit-IP display fixes. One recorded fix direction was adding a fail-closed TUN blackhole route when IPv6 is disabled, preventing dual-stack applications from escaping through a real IPv6 path.

This is a routing ownership problem, not only a UI proof problem. Showing the correct IP requires [[concepts/warp-vpn-mode-exit-ip-proof-boundary]], but preventing leaks requires the TUN route table to capture or block families that the engine cannot safely carry.

## Related Concepts
- [[concepts/warp-vpn-mode-exit-ip-proof-boundary]]
- [[concepts/warp-allowedips-tun-routing]]
- [[concepts/android-vpn-self-traffic-bypass]]
- [[connections/engine-exit-node-safe-routing-contract]]

## Sources
- [[daily/2026-05-31]]: session 19:02 records the user report that WARP still leaked the real IP for some services.
- [[daily/2026-05-31]]: session 19:17 records the decision to add a fail-closed IPv6 TUN blackhole route when IPv6 is disabled.
- [[daily/2026-05-31]]: session 19:17 states that disabling IPv6 in AWG without an Android TUN IPv6 route can bypass on dual-stack networks.
