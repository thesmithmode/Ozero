---
title: "Vendor ROM bindSocket EPERM During VPN IP Detection"
aliases: [bindsocket-eperm, vendor-eperm, ip-detection-eperm]
tags: [android, vpn, vendor-rom, gotcha, networking]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# Vendor ROM bindSocket EPERM During VPN IP Detection

On certain vendor ROMs (Nubia/RedMagic), creating a diagnostic socket for IP detection while a full-tunnel VPN is active throws `EPERM` (Operation not permitted) on `bindSocket`. This prevents the in-app IP checker from functioning on these devices when using full-tunnel engines (WARP/AWG, URnetwork). The workaround is a user-facing message "проверьте в браузере" (check in browser); a full architectural fix requires a separate `VpnService.SocketFactory` with `protect()` integration.

## Key Points

- `EPERM` thrown on `VpnService.protect()` + `bindSocket` during IP detection on full-tunnel VPN — vendor ROM specific
- Affects full-tunnel engines (WARP/AWG, URnetwork) where all traffic routes through TUN
- Does NOT affect ByeDPI (SOCKS5 proxy on localhost — IP detection can route through the SOCKS proxy)
- Minimal fix: catch EPERM → show "проверьте в браузере" message instead of crash or error noise
- Full fix (deferred): separate `VpnService.SocketFactory` architecture that manages `protect()` lifecycle for diagnostic sockets
- Vendor-specific: not reproducible on AOSP/Pixel/Samsung — only on ROMs with stricter socket permission enforcement

## Details

### The EPERM Mechanism

Android's `VpnService.protect(socket)` marks a socket to bypass the TUN interface, using the underlying physical network directly. This is how VPN apps reach their own backend servers without routing through the tunnel. On standard Android, `protect()` succeeds for any socket owned by the VPN service process.

On Nubia/RedMagic ROMs, `protect()` or the subsequent `bindSocket` may return `EPERM` under specific conditions:
- The VPN is in full-tunnel mode (all traffic routed through TUN)
- The socket is created outside the main VPN service thread
- The socket creation occurs during an IP detection flow (timing-dependent)

The exact ROM-level enforcement is undocumented. It appears to be a stricter interpretation of socket protection permissions, possibly related to Nubia's battery optimization or security policies.

### Impact on IP Detection

The IP detection module creates an HTTP connection to an external service (e.g., ipinfo.io) to determine the VPN exit IP. For full-tunnel engines, this connection must either:
1. Route through the tunnel (shows VPN exit IP — correct behavior)
2. Use a `protect()`ed socket to bypass the tunnel (shows real ISP IP — incorrect for VPN verification)

Option 1 is correct for IP verification but requires the engine to be fully operational (handshake complete, traffic flowing). Option 2 is the fallback for engines that haven't completed handshake, but fails on Nubia with EPERM.

### Per-Engine IP Detection Strategy

| Engine | IP Detection Method | EPERM Risk |
|--------|-------------------|------------|
| WARP/AWG | Through tunnel after handshake confirmation | Low (but EPERM if using protect() fallback) |
| URnetwork | Through SOCKS proxy on port 10810 | None (SOCKS routes through P2P mesh) |
| ByeDPI | Through SOCKS proxy on port 1080 | None (localhost SOCKS) |

For SOCKS-based engines, IP detection can route through the engine's own SOCKS proxy, completely avoiding the `protect()`/`bindSocket` path. This is both correct (shows VPN exit IP) and EPERM-safe.

### Deferred Architecture

The full fix requires a `VpnService.SocketFactory` that:
1. Creates sockets on the VPN service's main thread (where `protect()` is guaranteed to work on all ROMs)
2. Manages socket lifecycle with proper cleanup
3. Provides a `protect()`-aware HTTP client for diagnostic connections
4. Falls back gracefully when `protect()` fails (EPERM catch → user message)

This is a non-trivial architectural change deferred to a future release.

## Related Concepts

- [[concepts/nubia-rom-permission-enforcement]] - Same vendor ROM family; earlier issues with loadLibrary and metered VPN
- [[concepts/android-vpn-self-traffic-bypass]] - Related: self-bypass via `protect()` is the mechanism that EPERM prevents
- [[concepts/vpn-ip-detection-contract]] - Unified IP detection contract that accounts for EPERM as one failure mode
- [[concepts/warp-false-connected-no-handshake]] - IP detection timing depends on handshake completion; EPERM adds another failure path

## Sources

- [[daily/2026-05-09.md]] - Session 18:38: T6 EPERM on bindSocket during IP detection on full-tun engines; workaround = "проверьте в браузере"; full fix (VpnService SocketFactory) deferred
- [[daily/2026-05-09.md]] - Session 19:41: EPERM integrated into unified IP detection contract as one of 4 failure modes
