---
title: "ByeDPI IPv6 Silent Drop"
aliases: [byedpi-ipv6, vpn-ipv6-drop, dual-stack-vpn-latency]
tags: [byedpi, networking, vpn, gotcha, ipv6]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# ByeDPI IPv6 Silent Drop

ByeDPI (`hufrea/byedpi`) does not support IPv6 outbound connections. Adding IPv6 routes to the TUN interface causes dual-stack Android applications to attempt v6 first, receive no response (silent drop), then fall back to IPv4 — adding latency to every network request. This was a root cause of per-request latency spikes in Ozero v0.0.1.

## Key Points

- ByeDPI only handles IPv4 outbound — IPv6 packets forwarded via SOCKS5 to byedpi are silently dropped with no error
- Android dual-stack apps (browsers, system services) prefer IPv6 when v6 routes exist; each request pays the full v6 timeout before falling back to v4
- ByeByeDPI adds IPv6 TUN routes only when `ipv6_enable=true` (default: false); Ozero initially added them unconditionally
- Symptom: VPN tunnel up, byte stats growing, but per-request latency elevated and inconsistent
- Fix: remove `addAddress(fd00::1/128)` and `addRoute("::", 0)` from all `SplitTunnelMode` variants

## Details

### How the Drop Occurs

When IPv6 addresses and routes are added via `VpnService.Builder`, Android's kernel routes outgoing IPv6 packets through the TUN interface. libhev-socks5-tunnel receives these packets, parses them, and attempts to forward them to the upstream SOCKS5 proxy (byedpi). However, byedpi only binds to an IPv4 address and has no IPv6 outbound path — it silently discards v6 connection attempts without returning an error.

The result is a connection that enters the TUN, gets processed by libhev, reaches byedpi, and disappears. The application's TCP stack interprets this as packet loss, waits for a retransmission timeout (typically 1–3 seconds), then attempts the fallback path: closing the v6 socket and opening a new IPv4 connection. Every network request on a dual-stack system incurs this timeout overhead when the VPN is active.

### Reference Implementation

ByeByeDPI's TUN setup explicitly guards IPv6 routes behind a preference flag:

```kotlin
if (preferences.getBoolean("ipv6_enable", false)) {
    builder.addAddress("fd00::1", 128)
    builder.addRoute("::", 0)
}
```

The default is `false`. This design choice reflects byedpi's IPv4-only nature. Ozero's initial implementation did not include this guard and unconditionally added v6 routes, diverging from the reference.

### Diagnostic Pattern

The symptom is subtle because the tunnel appears healthy: TCP handshakes succeed (SYN/ACK visible in stats), byte counters grow, and some traffic completes correctly (pure IPv4 destinations). The failure manifests as elevated tail latency — median request time looks normal, but P95/P99 are 1–3 seconds higher than expected. Browser loads "work" but feel slow because each domain resolution or initial connection attempt tries v6 first.

The correct fix was verified in Ozero v0.0.1 retag-5: removing all v6 routes eliminated the latency spikes while leaving all IPv4 functionality intact. Conditional IPv6 support via a UI toggle is planned as a future feature.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - IPv6 silent drop was fix #7 in the 9-fix chain; combined with MTU and setMetered into the "burst/drop combo"
- [[concepts/tun-mtu-dual-layer]] - Another TUN configuration trap that caused the burst/drop symptom; fixed in the same retag-5 cycle
- [[concepts/byedpi-args-parsing]] - The args parsing traps that caused the companion DPI bypass failure in v0.0.1
- [[concepts/android-vpn-self-traffic-bypass]] - Related VpnService.Builder configuration traps

## Sources

- [[daily/2026-04-30.md]] - IPv6 silent drop identified during retag-5 debugging: `addAddress(fd00::1/128)` + `addRoute("::", 0)` caused dual-stack timeout latency; ByeByeDPI guards with `ipv6_enable=false` default; fix removed v6 routes unconditionally from all SplitTunnelMode variants
