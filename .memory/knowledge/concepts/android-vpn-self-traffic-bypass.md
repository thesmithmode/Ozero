---
title: "Android VPN App Self-Traffic Bypass"
aliases: [vpn-self-check-bypass, ip-checker-vpn-bypass, vpn-self-exemption]
tags: [android, vpn, networking, debugging, gotcha]
sources:
  - "daily/2026-05-08.md"
  - "daily/2026-05-20.md"
created: 2026-05-08
updated: 2026-05-20
---

# Android VPN App Self-Traffic Bypass

HTTP/HTTPS requests made by an Android VPN app itself (e.g., to check the current external IP or verify tunnel health) may not route through the VPN tunnel, instead using the device's direct network connection. This creates a false diagnostic: the in-app IP checker sees the device's real ISP IP, while external tools confirm the tunnel is routing correctly. Root causes are VPN service self-exemption via `addDisallowedApplication` or protected-socket HTTP clients.

## Key Points

- `addDisallowedApplication(context.packageName)` in `VpnService.Builder` explicitly exempts the VPN app's own traffic from the tunnel — IP checkers inside the app then see the real ISP IP
- `VpnService.protect(socket)` marks a socket to bypass the TUN; if the HTTP client used for IP checking opens protected sockets, it also bypasses the tunnel
- Symptom: in-app IP checker shows the device's real geolocation (e.g., the user's city on their ISP); browser-based external checker shows the correct VPN endpoint location
- The asymmetry (in-app vs external) is the diagnostic signal — if both show wrong IP, the tunnel itself is broken; if only in-app shows wrong, self-bypass is the cause
- **Fix evolved over time** — this article's original advice ("verify app package is NOT in `addDisallowedApplication`") was **superseded**. Later work (2026-05-09 → 2026-05-15) established that `addDisallowedApplication(packageName)` (i.e. `excludeSelf = true`) is **required unconditionally** for all engines (Go SDK and SOCKS) to prevent routing loops — see [[concepts/tun-self-exclusion-sdk-engines]]. Three regressions confirmed that removing self-exclusion breaks the engines. The correct IP-check fix is therefore **NOT** to remove self-exclusion but to add an explicit `ipProbeRoute()` that routes only the IP-checker socket through the TUN while keeping the rest of the app self-excluded — see [[concepts/ip-probe-route-architecture]]. The HTTP-client `protect()`-leak point below remains valid.

## Details

### The Self-Bypass Mechanisms

Android VPN services intercept traffic by establishing a TUN interface and routing device traffic through it. However, the VPN app process can be explicitly or implicitly excluded from this routing:

**Explicit exemption via `addDisallowedApplication`**: If the VPN app adds its own package name to `VpnService.Builder.addDisallowedApplication()`, all its outbound traffic bypasses the TUN. This is sometimes done intentionally to prevent routing loops (e.g., if the VPN tunnels traffic to a remote server and the app itself has persistent connections), but it unintentionally exempts all self-traffic including IP checkers.

**Protected sockets**: `VpnService.protect(socket)` marks a specific socket to use the underlying physical network, bypassing the TUN. This is used by VPN apps to reach their own backend servers (e.g., the WARP config generator) without going through the tunnel. If the HTTP client used for IP verification reuses a protected socket or a socket factory that applies `protect()`, the IP check bypasses the tunnel.

**Connection timing race**: Sockets established before the TUN interface is fully up may be bound to the physical network and remain on that path even after VPN activation. An eager IP-checker that fires on `onConnectClick` before `VpnService.Builder.establish()` completes can hit this race.

### The Ozero Discovery

In Ozero v0.0.5, the in-app IP checker consistently displayed "Leninogorsk" — the user's actual city via their Russian ISP — despite the VPN being active. `whoer.net` accessed through the browser confirmed the correct VPN endpoint location. This asymmetry immediately indicated self-bypass rather than a tunnel routing defect: the tunnel was routing browser traffic correctly, but the app's own IP checker was bypassing it.

The investigation identified two candidates: `addDisallowedApplication` in `OzeroVpnService.Builder` configuration, or a `VpnService.protect()`-aware HTTP client being reused for the IP check. The fix path is to audit all `VpnService.Builder` calls for the app's own package name, and audit the HTTP client used for self-checks to ensure it does not apply socket protection.

### Diagnosis Protocol

1. While VPN is active, note the IP shown by the in-app IP checker
2. Access an external IP checker (whoer.net, ipinfo.io) via the device browser
3. If they differ → self-bypass confirmed; if both show wrong IP → tunnel routing broken
4. `addDisallowedApplication(packageName)` is **expected** (required by [[concepts/tun-self-exclusion-sdk-engines]]). Verify instead that the IP-checker socket goes through `ipProbeRoute()` per [[concepts/ip-probe-route-architecture]] — without it, every self-issued probe sees the real ISP IP by design
5. Search for `VpnService.protect()` calls — trace which HTTP clients are passed protected sockets
6. Verify IP check is not initiated before `VpnService.Builder.establish()` returns

### Correct IP Checker Pattern

The IP checker for VPN status verification should (as superseded by [[concepts/ip-probe-route-architecture]]):
- Route only the IP-checker socket through the TUN via `ipProbeRoute()` — leave self-exclusion intact for the rest of the app
- Use a plain HTTP client with no socket factory customization for this probe
- Be invoked only after `VpnService.Builder.establish()` succeeds (TUN fd obtained)
- NOT reuse any HTTP client instance that has called `VpnService.protect()` on its sockets

If the VPN app has an HTTP client for reaching its own backend (e.g., WARP config mirrors), that client should be a separate instance from any client used for self-diagnostics.

## Related Concepts

- [[concepts/android-vpn-traffic-stats]] - Related self-traffic issue: /proc/net/dev also invisible to app sandbox; TrafficStats as correct alternative
- [[concepts/vpnservice-builder-traps]] - `addDisallowedApplication` is a Builder API with non-obvious routing side-effects
- [[concepts/warp-config-generator-api]] - WARP uses a separate HTTP client to reach config mirrors; protect() calls on that client must not leak to diagnostic clients
- [[concepts/android-silent-crash-diagnosis]] - Same principle: asymmetric behavior between app-internal and external is the diagnostic signal

## Sources

- [[daily/2026-05-08.md]] - Session 18:27: IP checker showed "Leninogorsk" (real ISP city) while browser whoer.net confirmed correct VPN IP; diagnosed as addDisallowedApplication or protect()-aware HTTP client in IP checker path; investigation pending
- [[daily/2026-05-20.md]] - KB audit (18:43): confirmed self-exclusion removal approach as deprecated/wrong; canonical pattern is ipProbeRoute() + always excludeSelf=true (three regressions confirmed removing self-exclusion breaks engines); `addDisallowedApplication(packageName)` expected not suspect
