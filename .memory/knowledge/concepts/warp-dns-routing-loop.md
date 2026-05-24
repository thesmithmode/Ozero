---
title: "WARP DNS Routing Loop: Pre-resolve Endpoint Before VPN Establish"
aliases: [warp-dns-loop, vpn-dns-bootstrap-loop, endpoint-pre-resolve]
tags: [warp, dns, vpn, routing, gotcha]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-05-06
---

# WARP DNS Routing Loop: Pre-resolve Endpoint Before VPN Establish

When a VPN tunnel routes all traffic (including DNS) through itself, any DNS lookup that occurs after `VpnService.Builder.establish()` is called will be routed into the tunnel. If the WireGuard/WARP endpoint is specified as a hostname rather than an IP address, and that hostname is resolved after tunnel establishment, the DNS query for the hostname goes into the tunnel — which hasn't fully connected yet because the endpoint is unknown. This creates a DNS bootstrap loop.

## Key Points

- VPN tunnel intercepts all DNS traffic once `Builder.establish()` completes
- Endpoint specified as hostname (e.g., `engage.cloudflareclient.com:2408`) must be resolved to an IP **before** `establish()` is called
- `resolveEndpointHost()` performs the DNS lookup on the pre-VPN network path, then substitutes the resolved IP into the config before tunnel creation
- Failure to pre-resolve results in: timeout waiting for handshake → `awaitReady` fails → engine reports `Failed` state
- The symptom looks identical to a WireGuard key/config problem; DNS loop is invisible without network-level tracing

## Details

### The Loop Mechanism

1. `VpnService.Builder.establish()` returns a TUN fd — from this point, all process traffic goes through the TUN
2. `awgTurnOn(config)` is called with config containing `Endpoint = hostname:port`
3. amneziawg-go needs to resolve the hostname to send the initial handshake
4. The DNS resolution goes through the TUN (because establish() already happened)
5. The TUN device is waiting for a handshake to complete connectivity
6. Result: DNS waits for tunnel, tunnel waits for DNS — deadlock

### Fix: resolveEndpointHost()

The fix is to resolve the endpoint hostname on the pre-VPN network stack before establishing the tunnel:

```kotlin
// Before establish() — still on real network
val resolvedConfig = config.copy(
    endpoint = resolveEndpointHost(config.endpoint)  // hostname → IP
)
// Now establish() — from here all traffic goes through TUN
val tunFd = vpnBuilder.establish()
// awgTurnOn with IP-only endpoint — no DNS needed post-establish
awgTurnOn(name, tunFd, resolvedConfig.toIni(), uapiPath)
```

`resolveEndpointHost()` calls `InetAddress.getByName()` (or equivalent) which uses the current network stack — before the tunnel takes over. The resolved IP is substituted directly into the INI config as `Endpoint = 1.2.3.4:2408`.

### WARP-Specific Aggravation

WARP endpoints (Cloudflare) use hostnames specifically for load balancing and resilience. The endpoint from the provisioning API is always a hostname. Without pre-resolution, WARP would always loop on first connect.

The issue is also relevant to any VPN engine that routes DNS through the tunnel and uses hostname-based peer addresses — WireGuard, AmneziaWG, and WARP all share this constraint.

### Caching Concern

Pre-resolved IPs are session-scoped: the IP resolved before one tunnel establishment may differ from what the provider returns later. For WARP specifically, the provisioned endpoint IP is stable within a config lifetime, making pre-resolution safe to cache for the session.

## Related Concepts

- [[concepts/warp-uapi-handshake-polling]] — Handshake poll is where the DNS loop symptom manifests: no initial handshake packet ever arrives
- [[concepts/android-vpn-self-traffic-bypass]] — Self-traffic bypass is a related routing concern; DNS pre-resolution is the VPN-bootstrap equivalent
- [[concepts/warp-config-generator-api]] — The API that provides hostname-based endpoints requiring pre-resolution
- [[concepts/warp-false-connected-no-handshake]] — DNS loop produces same symptom as false connected: tunnel up but no handshake

## Sources

- [[daily/2026-05-06.md]] — Session 15:01: `resolveEndpointHost()` added to WARP engine to fix DNS routing loop; hostname resolution must occur before `Builder.establish()` to avoid routing DNS through the not-yet-connected tunnel
