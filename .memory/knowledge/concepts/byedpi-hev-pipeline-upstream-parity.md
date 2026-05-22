---
title: "ByeDPI HevTunnelConfig YAML and IPv6 Blackhole Upstream Parity"
aliases: [hev-yaml-parity, byedpi-ipv6-blackhole-fix, hev-tunnel-config-fix]
tags: [byedpi, hev, vpn, architecture, gotcha]
sources:
  - "daily/2026-05-19.md"
  - "daily/2026-05-22.md"
created: 2026-05-19
updated: 2026-05-22
---

# ByeDPI HevTunnelConfig YAML and IPv6 Blackhole Upstream Parity

> **SCOPE WARNING (2026-05-20):** Этот аудит покрывает ТОЛЬКО HEV YAML и IPv6 blackhole routes. НЕ покрывает VpnService.Builder vs upstream, init order, background pollers, killswitch infrastructure. Полный pipeline diff в [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — там найден реальный root YouTube QUIC (`setUnderlyingNetworks(null)`). YAML parity ниже корректна но недостаточна.

Two ByeDPI pipeline divergences from upstream ByeByeDPI v1.7.4 that caused traffic to not flow through the tunnel despite the engine showing "Connected". First: `HevTunnelConfig.toYaml()` emitted extra YAML fields not present in upstream, including `tunnel.ipv4`, `tunnel.ipv6`, `misc.log-level`, and single-quoted `udp: 'udp'`. Second: `TunBuilderHelper.buildTunBuilder` unconditionally added IPv6 blackhole routes for ByeDPI, causing IPv6 packets to enter hev's fd and be dropped at the SOCKS upstream (ByeDPI has no IPv6 support).

## Key Points

- Upstream reference: `.claude/Контекст/ByeByeDPI-v.1.7.4/ByeDpiVpnService.kt:239-250`
- `HevTunnelConfig.toYaml()` fix: removed `tunnel.ipv4`, `tunnel.ipv6`, `misc.log-level`; changed `udp: 'udp'` → `udp: udp` (single quotes invalid in hev YAML parser)
- `TunBuilderHelper.buildTunBuilder` fix: `blackholeIpv6()` call removed for ByeDPI when `ipv6Enabled=false`; blackhole retained in `applyEngineTunSpec` for WARP/URnetwork hardening
- IPv6 blackhole in `buildTunBuilder` caused IPv6 packets to enter hev fd → hev tried to forward via SOCKS upstream (ByeDPI) → ByeDPI has no IPv6 SOCKS support → packets dropped/stuck
- `applyEngineTunSpec` blackhole is separate and remains (kill-switch hardening for WARP/URnetwork)
- Sentinel `OzeroVpnServiceIpv6BlackholeTest.buildTunBuilder` inverted: now requires NO blackhole in `buildTunBuilder`
- Sentinel `HevTunnelConfigTest`: asserts absence of `ipv4:`/`ipv6:`/`log-level:`/`udp: 'udp'` (single-quoted)

## Details

### YAML Fields vs Upstream

ByeByeDPI v1.7.4 generates the following minimal hev-socks5-tunnel YAML:

```yaml
misc:
  task-stack-size: 81920
tunnel:
  mtu: 8500
socks5:
  address: 127.0.0.1
  port: 1080
  udp: udp
```

Ozero's `HevTunnelConfig.toYaml()` was emitting additional fields:

```yaml
tunnel:
  ipv4: 198.18.0.1/15       # ← extra field, not in upstream
  ipv6: fc00::1/18           # ← extra field, not in upstream
misc:
  log-level: 2               # ← extra field, not in upstream
socks5:
  udp: 'udp'                 # ← single quotes: hev YAML parser rejects them
```

The `tunnel.ipv4`/`tunnel.ipv6` fields appear to have been added to explicitly set the hev tunnel interface IPs. However, upstream ByeByeDPI does not set them — hev uses the VPN TUN interface IPs assigned by `VpnService.Builder`. When these fields differ from the TUN interface IPs, hev may reject packets or fail to set up routing correctly.

The `misc.log-level` field caused hev to emit verbose logs to logcat, adding noise without affecting behavior.

The `udp: 'udp'` single-quote issue was the most likely cause of visible packet drops: the hev YAML parser may not accept single-quoted strings for this field, causing it to fall back to a default (often `disabled`), silently dropping all UDP traffic through the tunnel.

### IPv6 Blackhole Scoping

`TunBuilderHelper.buildTunBuilder` previously called `blackholeIpv6(builder)` unconditionally when `ipv6Enabled=false`. This added `::/0` as a VPN route — all IPv6 traffic was attracted into the VPN TUN fd.

For WARP/URnetwork (which have full IPv6 support in their Go runtimes), this is correct behavior: IPv6 traffic should either route through the tunnel or be dropped (not leak to the ISP). The hardening is in `applyEngineTunSpec`, which remains.

For ByeDPI, the hev-socks5-tunnel reads packets from the TUN fd and forwards them via SOCKS5 to the ByeDPI proxy. ByeDPI's SOCKS5 implementation does not support IPv6 destination addresses. IPv6 packets attracted by the blackhole route entered hev's fd, hev tried to CONNECT to the IPv6 destination via SOCKS5, ByeDPI returned an error, and hev dropped the packet.

Fix: `buildTunBuilder` only adds IPv6 blackhole for non-ByeDPI engine types, or equivalently, removes the blackhole call from `buildTunBuilder` entirely and relies on `applyEngineTunSpec` for WARP/URnetwork.

### Parity Validation

After these fixes, `HevTunnelConfig.toYaml()` output matches upstream reference exactly:
- `tunnel.mtu=8500` ✓
- `misc.task-stack-size=81920` ✓
- `socks5.udp=udp` (unquoted) ✓
- No `tunnel.ipv4`, `tunnel.ipv6`, `misc.log-level` ✓

Init order was also compared: Ozero calls `waitSocksReady(port, proxyJob)` before TUN establish — this is *stricter* than upstream (which starts tun2socks without waiting for SOCKS readiness). The stricter order is acceptable.

## Related Concepts

- [[concepts/tun-mtu-dual-layer]] — Builder.setMtu vs YAML mtu distinction; `tunnel.mtu=8500` is the lwIP internal buffer
- [[concepts/byedpi-args-parsing]] — ByeDPI CLI args passed through SOCKS5 to hev; this article covers the hev-side YAML config
- [[concepts/vpnservice-builder-traps]] — TUN builder API traps including IPv6 route configuration
- [[concepts/tun-self-exclusion-sdk-engines]] — excludeSelf=true keeps ByeDPI's own traffic from re-entering the tunnel

### udp:tcp Regression (2026-05-22, v0.1.13)

A regression introduced `udp: tcp` in `HevTunnelConfig.toYaml()` instead of `udp: udp`. The semantics:

- `udp: udp` — hev forwards UDP packets via SOCKS5 UDP ASSOCIATE. ByeDPI without `-Ku` flag rejects the ASSOCIATE request → QUIC fast-fails → browser falls back to TCP → TCP desync strategies work on TCP streams. This is the correct behavior.
- `udp: tcp` — hev routes QUIC (UDP) payloads as TCP connections to the upstream server. The server receives TCP packets with QUIC framing that it cannot parse → connection fails silently.

The regression caused YouTube to fail in both UI and CMD modes. Revert: `udp: tcp` → `udp: udp` restored YouTube in CMD mode (confirmed from `v0.1.13` log: Δrx=1860146B/1451p in 5s ≈ 370KB/s).

### hevLogLevel Added to YAML

`hevLogLevel` was stored in the settings DataStore but not written to `HevTunnelConfig.toYaml()`. Added as `misc.log-level` field. Default changed to `"info"` for next-APK diagnostics (was omitted, hev defaulted to silent). Note: this re-adds the `misc.log-level` field removed in the earlier parity audit — the key distinction is user-controlled value vs hardcoded `2`.

### YouTube CMD Mode Confirmed

ozero.log `v0.1.13` analysis (session 13:22:09–13:22:40) confirmed YouTube traffic in CMD mode: `Δrx=1860146B/1451p` over 5 seconds ≈ 370 KB/s. CMD mode YouTube was previously believed to never have worked in Ozero — the log refuted this. The `udp:tcp` regression was the sole blocking issue.

## Sources

- [[daily/2026-05-19.md]] — v0.1.5-4 session: HevTunnelConfig.toYaml() simplified to upstream ByeByeDPI 1.7.4 parity (removed ipv4/ipv6/misc.log-level, single-quote fix on `udp`); TunBuilderHelper.buildTunBuilder blackholeIpv6 removed for ByeDPI; sentinel OzeroVpnServiceIpv6BlackholeTest inverted; HevTunnelConfigTest updated; reference `.claude/Контекст/ByeByeDPI-v.1.7.4/ByeDpiVpnService.kt:239-250`
- [[daily/2026-05-22.md]] — Session 13:00: udp:tcp→udp:udp revert (QUIC routed as TCP → server parse failure); hevLogLevel added to YAML, default="info"; Session 13:40+: YouTube CMD confirmed 370KB/s from ozero.log v0.1.13
