---
title: "Engine Readiness vs False-Connected: awaitReady Unifies the Fix"
aliases: [readiness-false-connected, await-ready-unification]
tags: [architecture, vpn, pattern, connection]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# Engine Readiness vs False-Connected: awaitReady Unifies the Fix

The false-connected problem manifests differently in each engine but has the same root cause: `onEngineStarted()` fires before the engine can actually carry traffic. `awaitReady()` provides a unified architectural solution — each engine implements its own readiness signal, but the VPN service uses a single integration point.

## The Pattern

| Engine | False-Connected Cause | Readiness Signal | Polling |
|--------|----------------------|------------------|---------|
| WARP | `awgTurnOn` returns handle before WG handshake | `last_handshake_time_sec > 0` via UAPI socket | 300ms / 10s |
| URnetwork | SDK `start()` returns before P2P peers connect | `peerCount() > 0` via SDK API | 200ms / 15s |
| ByeDPI | N/A — SOCKS5 probe already gates readiness | `Socks5HandshakeProbe.probe()` success | Built into start sequence |

Each engine's readiness detection uses a different protocol (UAPI socket, SDK polling, TCP handshake), but the `EnginePlugin.awaitReady()` interface abstracts this away. The VPN service calls `engine.awaitReady()` without knowing which mechanism is used.

## Why This Matters

Before `awaitReady()`, four separate false-positive status signals existed (see [[connections/false-positive-engine-status]]): WARP handle, health probe, IP checker, warmup cancellation. The readiness gate eliminates the first one (premature Connected state) and makes the others less likely by ensuring the tunnel is actually functional before any status checks begin.

## Related Concepts

- [[concepts/engine-await-ready-pattern]] - The architectural pattern itself
- [[concepts/warp-false-connected-no-handshake]] - WARP-specific false-connected problem
- [[concepts/warp-uapi-handshake-polling]] - WARP-specific readiness implementation
- [[concepts/urnetwork-peer-watchdog-recovery]] - URnetwork post-connection peer loss (awaitReady covers initial, watchdog covers ongoing)
- [[connections/false-positive-engine-status]] - Four false-positive vectors; awaitReady addresses the first

## Sources

- [[daily/2026-05-14.md]] - Session 16:20-16:50: awaitReady() design and implementation across URnetwork (peerCount) and WARP (UAPI handshake); unified pattern in EnginePlugin interface
