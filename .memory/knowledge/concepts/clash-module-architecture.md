---
title: "Clash Module: Deferred Architecture for SS/VMess/VLESS/Trojan"
aliases: [clash-module, mihomo, clash-proxy, ss-vmess-vless-trojan]
tags: [architecture, future, proxy, subprocess-pattern, decision]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# Clash Module: Deferred Architecture for SS/VMess/VLESS/Trojan

Clash (mihomo fork) was considered as a potential engine for Ozero but deferred. The core decision: Clash adds no value over AWG for WARP YAML configs, but would be the correct foundation for SS/VMess/VLESS/Trojan support and domain-based routing when those protocols become needed.

## Key Points

- **Not useful for WARP YAML**: WARP config is AmneziaWireGuard protocol; reformatting to Clash YAML is "шило на мыло" — same underlying protocol, just different config syntax, no capability gain
- **Valuable for**: Shadowsocks (SS), VMess, VLESS, Trojan protocols + domain-based routing rules (route youtube.com via one proxy, all else direct)
- **Architecture when implemented**: subprocess-pattern identical to `engine-masterdns` — `ProcessBuilder` launching mihomo binary, not `System.loadLibrary`
- **Binary**: mihomo (the active fork of clash-core), distributed as a prebuilt arm64-v8a binary in `jniLibs/`
- **Status**: deferred — implement when SS/VMess/VLESS/Trojan protocol support is required

## Details

### Why Not Now

The immediate trigger for considering Clash was the desire to import WARP configs via Clash YAML format (a user-familiar format for WireGuard-based configs). However, WARP uses AmneziaWireGuard which Clash does not natively support without the AWG patch. More fundamentally, Ozero already has a native AWG engine (`engine-warp`) that speaks AWG directly — wrapping it through a Clash subprocess would add complexity and a process-boundary latency without any protocol gain.

Clash YAML for WireGuard is YAML + AWG = same protocol. Native AWG engine is already fully functional.

### When to Implement

The correct trigger for a Clash module is the need for proxy protocols that have no native implementation in Ozero:

- **Shadowsocks (SS)** — widely deployed on commercial VPN services and self-hosted servers
- **VMess/VLESS** — V2Ray protocol family; high adoption in Russia/China censorship circumvention
- **Trojan** — TLS-disguised proxy popular in high-censorship environments
- **Domain-based routing** — split traffic by destination domain, not by app (Clash's core strength)

### Planned Architecture

When implemented, the Clash module follows the `engine-masterdns` subprocess pattern:

```
engine-clash/
├── EngineClash.kt          — EnginePlugin @IntoSet implementation
├── ClashModule.kt          — Hilt module
├── ClashBridge.kt          — ProcessBuilder wrapper for mihomo
├── ClashConfigBuilder.kt   — generates config.yaml from EngineConfig
└── src/main/jniLibs/
    └── arm64-v8a/
        └── mihomo          — prebuilt mihomo binary (not .so, no loadLibrary)
```

Key design choices:
- `ProcessBuilder` launches mihomo binary directly — same as `libmdnsvpn.so` in engine-masterdns
- `useLegacyPackaging = true` required (see [[concepts/extract-native-libs-legacy-packaging]])
- Clash listens on a local SOCKS5 port; engine routes TUN traffic through it (not a TUN engine itself)
- Config generation: `ClashConfigBuilder` maps `EngineConfig` fields to Clash YAML format

### Routing Integration

Clash in SOCKS5 mode integrates via the existing Ozero VPN pipeline:
- Clash process started with `mixed-port: <port>` SOCKS5 listener
- `EngineClash` implements `IpProbeRoute.Socks(host="127.0.0.1", port=<port>)` for IP detection (see [[concepts/ip-probe-route-architecture]])
- VPN traffic (from hev tunnel) routed to `127.0.0.1:<clashPort>` SOCKS5 endpoint

## Related Concepts

- [[concepts/engine-masterdns]] — subprocess-pattern that Clash module will replicate
- [[concepts/extract-native-libs-legacy-packaging]] — `useLegacyPackaging=true` required for ProcessBuilder-launched binaries in APK
- [[concepts/ip-probe-route-architecture]] — IpProbeRoute.Socks for SOCKS5-mode engines
- [[concepts/per-engine-ui]] — each engine requires a settings screen in `ui/settings/engines/`

## Sources

- [[daily/2026-05-22.md]] — Session 19:47: Clash for WARP YAML = no value (same AWG protocol); Clash valuable for SS/VMess/VLESS/Trojan + domain routing; architecture = subprocess-pattern (engine-masterdns), mihomo binary; deferred decision
