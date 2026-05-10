---
title: "Android VPN Traffic Stats: /proc/net/dev vs TrafficStats"
aliases: [proc-net-dev-sandbox, traffic-stats-uid, vpn-speed-measurement]
tags: [android, vpn, networking, performance, sandbox]
sources:
  - "daily/2026-05-07.md"
created: 2026-05-07
updated: 2026-05-07
---

# Android VPN Traffic Stats: /proc/net/dev vs TrafficStats

On Android 11+, VPN tunnel interfaces (`tun0`) are not visible to the app sandbox via `/proc/net/dev`. Parsing that file from a VPN client app returns `iface=null` for the tun interface, producing zero speed readings. The correct approach for measuring VPN throughput from within the app process is `TrafficStats.getUidRxBytes(uid)` / `TrafficStats.getUidTxBytes(uid)`, which aggregates all network I/O for the app's UID — which in VPN mode equals tunnel traffic.

## Key Points

- `/proc/net/dev` is readable on Android 11+ but VPN tun interfaces are not surfaced to the app sandbox — returns `iface=null` for tun
- `TrafficStats.getUidRxBytes(Process.myUid())` captures all I/O for the process UID; in VPN mode, this equals tunnel traffic
- `TrafficStats.UNSUPPORTED` (-1) return means the device doesn't support per-UID stats — handle gracefully
- This issue only affects traffic stats read by the VPN client itself; `libhev` native counters (`TProxyGetStats`) still work for hev-based engines
- For WARP/AWG engine: `GoBackend.awgGetConfig()` can provide stats but requires parsing the config string output

## Details

### The /proc/net/dev Sandbox Restriction

`/proc/net/dev` contains per-interface byte and packet counters updated by the kernel. On Android 10 and earlier, VPN apps could read this file and parse the tun interface line to obtain accurate tunnel throughput. Starting with Android 11, Google tightened process namespace isolation: each app's `/proc/net/` view is filtered to exclude interfaces the app did not create through standard Android APIs. A VPN service that calls `VpnService.Builder.establish()` creates the tun interface but cannot reliably see it via procfs due to namespace boundaries.

The symptom in Ozero: a speed measurement implementation reading `/proc/net/dev` worked on Android 10 test devices but returned `iface=null` on Android 11+ production devices, causing the speed display to show 0 B/s perpetually despite active VPN traffic.

### TrafficStats as Fallback

`android.net.TrafficStats` provides per-UID network statistics through Android's kernel accounting layer (`CONFIG_UID_STAT`). The UID traffic counters capture all socket I/O for the app's UID, including the tun socket used by the VPN service. In VPN mode, essentially all network traffic flows through the VPN engine, so UID stats equal tunnel stats.

The implementation pattern:
```kotlin
val uid = Process.myUid()
val rxBytes = TrafficStats.getUidRxBytes(uid)
val txBytes = TrafficStats.getUidTxBytes(uid)
if (rxBytes == TrafficStats.UNSUPPORTED.toLong()) {
    // device doesn't support per-UID stats, show zero or hide widget
}
```

Delta computation (current - previous per polling interval) gives throughput. The same EWMA α=0.4 smoothing from [[concepts/libhev-tunnel-stats]] applies for UI stability.

### Engine-Specific Alternatives

For engines with native stat APIs, prefer those over TrafficStats:
- **libhev (hev-socks5-tunnel)**: `TProxyGetStats()` returns cumulative counters directly from native layer — more accurate, no sandbox restrictions
- **AmneziaWG/GoBackend**: `GoBackend.awgGetConfig()` returns the WireGuard config string which includes `rx_bytes` and `tx_bytes` fields per peer — requires string parsing but avoids procfs

TrafficStats is the universal fallback when engine-specific APIs are unavailable.

## Related Concepts

- [[concepts/libhev-tunnel-stats]] - Native counter approach for hev engine; EWMA smoothing applies equally here
- [[concepts/vpnservice-builder-traps]] - Other Android API restrictions and sandbox behaviors affecting VPN development
- [[concepts/amneziawg-turnon-minus-one]] - AWG engine context where traffic stats alternatives are needed

## Sources

- [[daily/2026-05-07.md]] - Session 15:11: `/proc/net/dev` returns `iface=null` for tun on Android 11+; `TrafficStats.getUidRxBytes` implemented as working fallback for WARP/AWG engine speed display
