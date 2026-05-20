---
title: "Engine awaitReady() Pattern: Per-Engine Readiness Gates"
aliases: [await-ready, engine-readiness-gate, readiness-polling]
tags: [architecture, vpn, engine, pattern]
sources:
  - "daily/2026-05-14.md"
  - "daily/2026-05-20.md"
created: 2026-05-14
updated: 2026-05-20
---

# Engine awaitReady() Pattern: Per-Engine Readiness Gates

`awaitReady()` is a suspend function in the `EnginePlugin` interface that blocks the VPN start sequence until the engine confirms actual traffic readiness. Default implementation is no-op. Inserted in `OzeroVpnService.runStartSequence()` between `routeTrafficForEngine()` and `onEngineStarted()` — UI shows "Connected" only when traffic actually flows.

## Key Points

- Default no-op in `EnginePlugin` — engines override only when async readiness detection is needed
- URnetwork: polls `peerCount()` every 200ms, timeout 45s — waits for P2P peer negotiation; original 15s too short for slow P2P discovery (bumped v0.1.9)
- WARP: polls UAPI socket for `last_handshake_time_sec > 0` every 100ms, timeout 10s — waits for WireGuard handshake; path `$dataDir/sockets/<tun>.sock` (not `ozero-warp.sock`)
- ByeDPI: no `awaitReady()` needed — SOCKS5 handshake probe already serves as readiness gate
- Uses `withTimeoutOrNull` + `delay` (not `System.currentTimeMillis()`) for `runTest` compatibility with virtual time

## Details

### Problem: False-Connected State

Before `awaitReady()`, all engines transitioned to `Connected` immediately after successful `start()` return. For engines with async peer negotiation (URnetwork P2P, WARP WireGuard handshake), this created a false-positive: UI showed "Connected" but no traffic could flow for 1-15 seconds.

### Architecture

```
routeTrafficForEngine(engine)
    ↓
engine.awaitReady()          ← NEW: blocks until real readiness
    ↓
onEngineStarted()            ← UI → Connected
```

The `awaitReady()` call sits between route setup and the Connected state transition. If it times out, the engine can transition to `Failed` with a descriptive reason.

### URnetwork Implementation

Polls `peerCount()` from the SDK every 200ms. Returns when at least one peer is connected. `peerReadyTimeoutMs` is constructor-injectable for testability. Timeout was originally 15s, bumped to 45s in v0.1.9 after field reports of timeout failures on slow mobile P2P networks — P2P discovery legitimately requires 15-45s on congested/slow ISP connections. A progress log fires every 15 polls to distinguish stuck vs slow discovery.

### WARP Implementation

Reads `last_handshake_time_sec` from UAPI socket at `$dataDir/sockets/<kernel-tun-name>.sock` via `LocalSocket`. Polls every 100ms, timeout 10s. Does NOT use `awgGetConfig()` JNI call — that causes SIGSEGV on partial-handshake handle. Socket path discovery uses `WarpHandshakeUapi.findUapiSocket()` cascade (preferred name → listFiles → legacy fallback). See [[concepts/warp-uapi-handshake-polling]].

### Testability

All polling loops use `withTimeoutOrNull(timeout) { while(true) { ... delay(interval) } }` pattern. In `runTest`, `delay()` advances virtual time and `withTimeoutOrNull` uses `TestCoroutineScheduler` — tests complete in milliseconds regardless of timeout values. `System.currentTimeMillis()` is incompatible with this pattern.

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] - The false-connected problem that awaitReady() solves for WARP
- [[concepts/warp-uapi-handshake-polling]] - WARP-specific UAPI socket readiness detection
- [[concepts/urnetwork-peer-watchdog-recovery]] - URnetwork peer discovery recovery; awaitReady covers initial connection, watchdog covers mid-session peer loss
- [[concepts/byedpi-mock-server-ci-fragility]] - ByeDPI uses SOCKS5 probe as readiness gate; no awaitReady needed

## Sources

- [[daily/2026-05-14.md]] - Session 16:20: awaitReady() design — default no-op in EnginePlugin, URnetwork polls peerCount 200ms/15s, inserted between routeTrafficForEngine and onEngineStarted
- [[daily/2026-05-14.md]] - Session 16:41: WARP awaitReady via UAPI socket, NOT awgGetConfig JNI (SIGSEGV); WarpHandshakeUapi.kt 300ms/10s polling
- [[daily/2026-05-20.md]] - v0.1.9 prep: URnetwork timeout 15s→45s (P2P discovery on slow networks); WARP poll 300ms→100ms + path ozero-warp.sock→sockets/ subdir cascade
