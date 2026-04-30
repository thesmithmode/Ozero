---
title: "VpnService.Builder Configuration Traps"
aliases: [builder-traps, vpn-builder-pitfalls, tun-configuration-traps]
tags: [android, vpn, networking, gotcha]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# VpnService.Builder Configuration Traps

Android's `VpnService.Builder` has several configuration options that interact non-obviously with the underlying tunnel implementation and device ROM behavior. During Ozero v0.0.1 development, three Builder calls caused distinct failure modes: `setBlocking(true)`, unconditional IPv6 routes, and missing `setMetered(false)`. Each diverged from the ByeByeDPI reference implementation.

## Key Points

- `setBlocking(true)` freezes libhev's `epoll`-based async I/O event loop → traffic counter stuck at 0 despite tunnel being up
- Unconditional IPv6 routes (`addAddress(fd00::1/128)` + `addRoute("::", 0)`) cause silent packet drops when the proxy engine (byedpi) lacks v6 outbound support
- `setMetered(false)` (API Q+) prevents Nubia/RedMagic ROM from throttling VPN traffic marked as metered
- `setMtu()` with values exceeding cellular path MTU causes fragmentation storms (covered separately in [[concepts/tun-mtu-dual-layer]])
- General rule: match ByeByeDPI/ByeDPIAndroid Builder configuration exactly; any deviation is a potential bug source

## Details

### setBlocking and epoll Conflict

`VpnService.Builder.setBlocking(true)` sets the TUN file descriptor to blocking I/O mode. libhev-socks5-tunnel internally uses `epoll` for asynchronous multiplexed I/O across the TUN fd and SOCKS5 connections. When the TUN fd is blocking, `epoll_wait` may still return, but subsequent `read()` calls on the TUN fd block the event loop thread, preventing it from servicing SOCKS5 connections or processing responses. The result is a complete I/O deadlock: the tunnel appears established (VPN icon shows, service is running), but zero bytes flow.

ByeByeDPI and ByeDPIAndroid do not call `setBlocking()` at all, leaving the TUN fd in non-blocking mode (the default). This was the first fix applied in the v0.0.1 cycle.

### IPv6 Routes Without Engine Support

Adding IPv6 routes to the TUN interface causes Android's dual-stack networking to route IPv6 traffic through the VPN. When the proxy engine (byedpi) does not support IPv6 outbound connections, these packets enter the tunnel and are silently dropped. Applications then wait for the IPv6 connection to timeout before falling back to IPv4, adding tail latency to every request.

ByeByeDPI conditionally adds IPv6 routes only when `ipv6_enable=true` (default: `false`). Ozero initially added IPv6 routes unconditionally across all `SplitTunnelMode` variants (ALL, BYPASS_LAN, ALLOWLIST, BLOCKLIST). The fix removed all IPv6 routes; conditional IPv6 support is planned for v0.0.2.

### setMetered and OEM Throttling

On API level Q (29) and above, `VpnService.Builder.setMetered(boolean)` controls whether Android treats the VPN connection as metered. Nubia/RedMagic ROMs aggressively throttle background data on metered VPN connections, producing unstable throughput. Calling `setMetered(false)` declares the VPN as unmetered, preventing OS-level throttling.

ByeByeDPI calls `setMetered(false)` on Q+. This is particularly important for Nubia devices but may affect other OEM ROMs with aggressive battery/data management.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - Builder traps were fixes #1, #7, and #8 in the 9-fix chain
- [[concepts/tun-mtu-dual-layer]] - The MTU Builder trap is covered in its own article due to the dual-layer complexity
- [[concepts/nubia-rom-permission-enforcement]] - setMetered fix specifically targets Nubia ROM behavior

## Sources

- [[daily/2026-04-30.md]] - setBlocking (fix #1), IPv6 routes (fix #7), and setMetered (fix #8) identified as Builder configuration traps during v0.0.1 retag cycle
