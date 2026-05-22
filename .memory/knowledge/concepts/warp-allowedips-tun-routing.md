---
title: "WARP AllowedIPs TUN Routing vs Full-Tunnel"
aliases: [warp-allowedips, awg-selective-routing, warp-tun-routes]
tags: [warp, amneziawg, routing, vpn, tun]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# WARP AllowedIPs TUN Routing vs Full-Tunnel

When an AmneziaWG config with selective `AllowedIPs` (~300 specific networks) is used, Ozero's `addRoute("0.0.0.0", 0)` in the TUN builder attracts all traffic into the VPN fd — but AWG only routes traffic for its declared `AllowedIPs`. Traffic destined for any IP not in the `AllowedIPs` list is dropped by AWG rather than forwarded. The fix is to add the config's AllowedIPs as TUN routes instead of always using `0.0.0.0/0`, mirroring PORTAL_WG's approach.

## Key Points

- Standard Cloudflare WARP conf uses `AllowedIPs = 0.0.0.0/0, ::/0` → full tunnel → `addRoute("0.0.0.0", 0)` is correct
- `amneziawg.conf` imported via `rawIniOverride` uses selective `AllowedIPs` (~300 nets) → split-tunnel config
- Ozero TUN builder always calls `addRoute("0.0.0.0", 0)` regardless of config contents → routes all traffic to VPN fd
- AWG peer processes only packets matching its `AllowedIPs`; non-matching packets are dropped at AWG layer
- Result: services outside the ~300 AllowedIPs nets appear broken despite "Connected" state
- PORTAL_WG parses `AllowedIPs` from the config and adds exactly those routes to TUN

## Details

### The Mismatch

Ozero's `TunBuilderConfigurator` (extracted from `OzeroVpnService`) always applies `addRoute("0.0.0.0", 0)` for WARP. This is correct for Cloudflare's generated configs which declare `AllowedIPs = 0.0.0.0/0, ::/0` (full tunnel). When a user imports an `amneziawg.conf` via `rawIniOverride`, the config may contain a selective list:

```ini
[Peer]
AllowedIPs = 1.1.1.0/24, 8.8.8.0/24, ... (~300 subnets)
```

The TUN attracts all traffic (because TUN route = `0.0.0.0/0`), but AWG only forwards packets matching its declared AllowedIPs. Traffic to any other IP — including most consumer services — enters the VPN fd and is dropped by AWG without being forwarded.

Diagnosis: `iniLen=2722` (large rawIniOverride) → Connected ✓, PSK present ✓, but many services unreachable. `iniLen=362` (standard Cloudflare.conf) → handshake timeout = network block, not routing.

### PORTAL_WG Reference Behavior

PORTAL_WG parses the `AllowedIPs` field from the WireGuard config and adds each subnet as a VPN route:

```kotlin
// PORTAL_WG approach
config.peers.forEach { peer ->
    peer.allowedIps.forEach { subnet ->
        builder.addRoute(subnet.address, subnet.prefix)
    }
}
```

This ensures TUN routing matches AWG routing exactly — packets only enter the fd for subnets that AWG will forward.

### Compatibility Constraint

Any change to TUN routing must not break the standard Cloudflare WARP case (where AllowedIPs = full tunnel). The safe implementation: parse `AllowedIPs` from the active config; if it equals `0.0.0.0/0`, fall back to `addRoute("0.0.0.0", 0)`; otherwise add per-subnet routes. This is a backwards-compatible behavioral change gated on config content.

The `rawIniOverride` pathway (see [[concepts/amnezia-wg-warp-migration]]) is the primary entry point for non-standard configs. Standard Cloudflare-generated configs via mirror API always produce full-tunnel AllowedIPs.

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] — rawIniOverride is how non-standard AWG configs enter Ozero
- [[concepts/warp-awg-obfuscation-russian-isps]] — Context for why custom AWG configs (with different AllowedIPs) are used
- [[concepts/tun-self-exclusion-sdk-engines]] — excludeSelf=true keeps self-traffic from VPN fd; TUN route scope affects this
- [[concepts/byedpi-vpn-pipeline-upstream-divergence]] — Similar pattern: YAML/Builder divergence from upstream reference causes traffic drops

## Sources

- [[daily/2026-05-22.md]] — Session 17:51: amneziawg.conf (selective AllowedIPs ~300 nets) + always addRoute("0.0.0.0",0) → traffic outside list dropped by AWG; PORTAL_WG adds AllowedIPs as TUN routes; iniLen=2722 → Connected but services broken; action item: add AllowedIPs routes to TUN for non-full-tunnel configs
