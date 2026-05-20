---
title: "TUN MTU Dual-Layer Architecture"
aliases: [mtu-dual-layer, builder-mtu-vs-yaml-mtu, tun-mtu-trap]
tags: [vpn, native, libhev, networking, gotcha]
sources:
  - "daily/2026-04-30.md"
  - "daily/2026-05-20.md"
created: 2026-04-30
updated: 2026-05-20
---

# TUN MTU Dual-Layer Architecture

Android VPN applications using libhev-socks5-tunnel have two independent MTU settings that operate at different network layers. Confusing them causes fragmentation and retransmit storms on cellular networks. This was a root cause of the burst/drop download pattern in Ozero v0.0.1.

## Key Points

- `VpnService.Builder.setMtu(N)` sets the **link MTU cap** for the TUN interface — Android apps send packets up to N bytes into the TUN
- `socks5-tunnel YAML mtu: N` sets the **lwIP internal buffer cap** — the reassembly segment size inside libhev's TCP/IP stack, never exposed externally
- ByeByeDPI/ByeDPIAndroid do NOT call `setMtu()` (Android defaults to ~1500 link MTU) but set YAML `mtu: 8500` for lwIP performance
- Setting `setMtu(8500)` causes apps to emit 8500-byte packets into TUN; kernel TCP then fragments them for cellular path MTU ~1500 → retransmit storms
- Correct config: omit `setMtu()` entirely, keep YAML `mtu: 8500`

## Details

### The Two MTU Layers

The first layer is the Android TUN interface MTU, controlled by `VpnService.Builder.setMtu()`. This determines the maximum packet size that applications write into the TUN file descriptor. When an app sends data through the VPN, the kernel enforces this MTU on outgoing packets. If not explicitly set, Android uses a sane default (~1500 bytes) matching typical network path MTU.

The second layer is libhev's internal lwIP stack, configured via the `mtu` field in the socks5-tunnel YAML configuration. lwIP is a lightweight TCP/IP implementation that libhev uses to reassemble TUN packets into TCP streams for forwarding through the SOCKS5 proxy. This MTU only affects internal buffer sizing within lwIP — it controls how large the reassembled TCP segments can be before being forwarded. This value never leaves the process; it is purely an internal optimization parameter.

### The Fragmentation Trap

When Ozero initially set both `setMtu(8500)` and YAML `mtu: 8500`, applications began sending 8500-byte packets into the TUN interface. lwIP correctly processed these internally, but when the resulting TCP segments were transmitted over the cellular interface (path MTU ~1500), the kernel had to fragment each packet into 5-6 fragments. Any single fragment drop triggered a full TCP retransmission of the original large segment.

The observable symptom was a distinctive burst/drop download pattern: `Δrx 5MB → 1KB → 6MB → 100KB`. Large bursts succeeded when no fragments were lost, followed by near-zero throughput during retransmission timeouts after fragment loss.

### Reference Implementation

ByeByeDPI and ByeDPIAndroid serve as the reference. They never call `Builder.setMtu()`, letting Android choose the default link MTU matching the underlying network. They do set YAML `mtu: 8500` because larger internal buffers improve lwIP's TCP reassembly throughput without affecting external packet sizes. This is the correct configuration for any libhev-socks5-tunnel integration.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - MTU fix was #6 in the 9-fix chain; removing `setMtu()` eliminated burst/drop pattern
- [[concepts/byedpi-args-parsing]] - Another configuration trap where deviation from ByeByeDPI reference caused failures

### v0.1.6 Refactor Regression

During the v0.1.6 `OzeroVpnService` extraction refactor (`89a6ecf3`), a new class `TunBuilderHelper.kt` was created to isolate TUN setup logic. The refactor incorrectly added `setMtu(HevTunnelConfig.DEFAULT_TUN_MTU)` (8500) to `buildTunBuilder()`. This value was never present in ByeByeDPI or in Ozero v0.1.0 — it was added during the refactor under the mistaken assumption that TUN link MTU and YAML internal MTU should match.

The regression introduced intermittent packet fragmentation on cellular networks, reproducing the original v0.0.1 burst/drop symptom. It was identified during v0.1.8 debugging (2026-05-20) as a bisect regression introduced by the v0.1.6 commit. Fix: remove `setMtu()` from `TunBuilderHelper.buildTunBuilder()`.

**Rule:** A refactor that extracts code into a new class must not introduce new parameters or behaviors not present in the original. Even if a value "seems consistent" with other configs, verify against the reference implementation before adding.

## Sources

- [[daily/2026-04-30.md]] - MTU dual-layer discovered during v0.0.1 retag-5 debugging; burst/drop pattern traced to 8500-byte TUN MTU on cellular
- [[daily/2026-05-20.md]] - v0.1.6 refactor regression: TunBuilderHelper.kt incorrectly added setMtu(8500) during extract; bisected and removed in v0.1.8
