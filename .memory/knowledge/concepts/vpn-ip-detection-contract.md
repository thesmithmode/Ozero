---
title: "Unified VPN IP Detection Contract"
aliases: [ip-detection-contract, ip-checker-architecture, vpn-ip-check]
tags: [vpn, architecture, networking, ip-detection]
sources:
  - "daily/2026-05-09.md"
created: 2026-05-09
updated: 2026-05-09
---

# Unified VPN IP Detection Contract

IP detection in a VPN app must account for four distinct failure modes discovered across Ozero v0.0.5–v0.0.8: self-traffic bypass (shows real IP), `protect()` socket contamination (shows real IP), premature fetch before handshake (shows real IP or fails), and vendor EPERM on diagnostic sockets (fails entirely). The unified contract specifies: no `protect()`, no `addDisallowedApplication` for the IP checker path, readiness signal from engine before fetch, EPERM catch with user-facing fallback, and SOCKS proxy routing for engines that exclude the app from TUN.

## Key Points

- HTTP client for IP check must NOT use `protect()` on its sockets — protected sockets bypass TUN → show real ISP IP
- App package must NOT be in `addDisallowedApplication` for the IP check path — or use SOCKS proxy routing instead
- IP fetch must wait for engine readiness signal (WARP: `last_handshake_time_sec > 0`; URnetwork: SOCKS5 accepting connections) — not just `Connected` state
- EPERM on `bindSocket` must be caught → fallback "проверьте в браузере" (vendor ROM specific, see [[concepts/vendor-bindsocket-eperm]])
- For self-excluded engines (URnetwork, ByeDPI): route IP check through engine's SOCKS proxy — avoids TUN self-bypass entirely

## Details

### The Four Failure Modes

Each failure mode was discovered independently across multiple release cycles, but they share a common theme: the IP detection path must be explicitly designed to route through the VPN tunnel, not around it.

**1. Self-traffic bypass** ([[concepts/android-vpn-self-traffic-bypass]]): `addDisallowedApplication(context.packageName)` exempts all app traffic from TUN, including IP checker. In-app shows real ISP IP (e.g., "Leninogorsk"), browser shows correct VPN IP. Fix: ensure IP checker HTTP client is not in the excluded set.

**2. `protect()` socket contamination**: If the HTTP client used for IP detection shares a connection pool or socket factory with a client that calls `VpnService.protect()` (e.g., the WARP config fetcher), its sockets may bypass TUN. Fix: separate HTTP client instance for IP detection, no socket protection.

**3. Premature fetch** ([[concepts/warp-false-connected-no-handshake]]): `awgTurnOn` returns valid handle → engine reports `Connected` → IP fetch fires → but no WireGuard handshake yet → fetch either fails (no route) or shows real IP (TUN not yet forwarding). Fix: wait for engine-specific readiness signal before fetching.

**4. Vendor EPERM** ([[concepts/vendor-bindsocket-eperm]]): Nubia/RedMagic ROMs throw EPERM on `bindSocket` for diagnostic sockets during full-tunnel VPN. Fix: catch EPERM, show fallback message.

### Per-Engine IP Detection Strategy

| Engine | Self-Excluded | IP Check Route | Readiness Signal |
|--------|--------------|----------------|-----------------|
| WARP/AWG | No | Direct through TUN | `last_handshake_time_sec > 0` |
| URnetwork | Yes | SOCKS proxy (port 10810) | SOCKS5 accepting connections |
| ByeDPI | Yes (localhost) | SOCKS proxy (port 1080) | SOCKS5 accepting connections |

For SOCKS-based engines, IP detection through the engine's own SOCKS proxy is both correct (shows VPN exit IP) and EPERM-safe (no `protect()` needed). The SOCKS client connects to localhost, which is always permitted.

### Architectural Contract

The IP detection module must:
1. Use a dedicated HTTP client with no `protect()` calls on its sockets
2. NOT share connection pools with any `protect()`-aware HTTP client
3. Accept an engine-specific readiness signal before initiating the fetch
4. Accept an optional SOCKS proxy address for self-excluded engines
5. Catch `EPERM` and degrade gracefully (user message, not crash)
6. Implement cancellation-aware fetch that survives engine restarts (debounce rapid switching)

Point 6 addresses the engine-switch chain problem ([[concepts/engine-switch-chain-cascading-failures]]): rapid switching cancels the 8-second warmup timer repeatedly, and the IP fetch never fires. The fix is to debounce the warmup timer or use a stable coroutine scope that survives individual engine restarts.

### Research Gaps

- Karing app's approach to IP detection is unknown (no data in knowledge base)
- SOCKS proxy routing for IP detection is architecturally sound but not yet implemented in Ozero
- The `VpnService.SocketFactory` approach (deferred) would provide a more general solution for all diagnostic network operations, not just IP detection

## Related Concepts

- [[concepts/android-vpn-self-traffic-bypass]] - Failure mode 1: self-traffic bypass via addDisallowedApplication
- [[concepts/warp-false-connected-no-handshake]] - Failure mode 3: premature fetch before handshake
- [[concepts/vendor-bindsocket-eperm]] - Failure mode 4: vendor EPERM on diagnostic sockets
- [[connections/false-positive-engine-status]] - All four failure modes contribute to false-positive VPN status signals
- [[concepts/engine-switch-chain-cascading-failures]] - IP warmup cancellation during rapid switching → empty display

## Sources

- [[daily/2026-05-09.md]] - Session 19:41: synthesized 4 IP detection failure modes from knowledge base research; per-engine strategy table; unified architectural contract; SOCKS proxy routing as fix for self-excluded engines
- [[daily/2026-05-09.md]] - Session 13:12: IP warmup cancellation during engine-switch chain identified as missing from prior analysis
- [[daily/2026-05-09.md]] - Session 18:38: EPERM workaround committed; full SocketFactory fix deferred
