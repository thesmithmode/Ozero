---
title: "Connection: Engine-Specific Failure Patterns Require Per-Engine Diagnostics"
connects:
  - "concepts/warp-preflight-dns-exhaustion"
  - "concepts/urnetwork-peer-watchdog-recovery"
  - "concepts/byedpi-strategy-runtime-disconnect"
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# Connection: Engine-Specific Failure Patterns Require Per-Engine Diagnostics

## The Connection

Three engines (WARP, URnetwork, ByeDPI) in Ozero exhibit three structurally distinct failure patterns. WARP fails at DNS preflight (network layer), URnetwork loses peer discovery over time (P2P mesh layer), and ByeDPI fails at native proxy startup or strategy application (binary/config layer). A single generic diagnostic flow ("is the VPN working?") cannot distinguish between these, requiring per-engine diagnostic branches.

## Key Insight

The non-obvious relationship is that all three engines present the same user-visible symptom — "VPN connected but nothing works" or "VPN won't connect" — but the root causes and recovery paths are completely different:

1. **WARP DNS exhaustion** ([[concepts/warp-preflight-dns-exhaustion]]): Failure before tunnel establishment. Recovery requires DNS bypass (DoH, hardcoded IP). The engine never reaches `awgTurnOn` — no tunnel state to inspect.

2. **URnetwork peer loss** ([[concepts/urnetwork-peer-watchdog-recovery]]): Failure after successful establishment. Peers drain over 4-5 minutes. Recovery via `recover()` — soft peer re-discovery without full restart. The tunnel exists but has no peers to route through.

3. **ByeDPI proxy startup** ([[concepts/byedpi-strategy-runtime-disconnect]]): Failure at native binary level (`jniStartProxy=-1`). No SOCKS proxy, no DPI bypass. Even if the TUN is up, hev-socks5-tunnel has no upstream. Strategy Test winning args are disconnected from runtime.

A unified health check (like HealthMonitor's probe-based approach) sees "no traffic flowing" for all three but cannot prescribe the correct recovery action. WARP needs DNS resolution fix, URnetwork needs peer refresh, ByeDPI needs proxy restart with different args.

## Evidence

From the session 21:19 diagnostic using 6 parallel subagents:

- **WARP agent** found: sequential DNS timeouts for `engage.cloudflareclient.com`, 5s × 3 = 20s exhaustion, no fallback
- **URnetwork agent** found: peer count declining from 7 to 0 over 4-5 minutes, no re-discovery triggered
- **ByeDPI agent** found: `jniStartProxy=-1` in logs, SOCKS port never bound, winning strategy from Test UI not reaching runtime

All three findings came from the same ozero.log analysis session, demonstrating that multi-engine VPN apps need engine-specific diagnostic instrumentation, not just generic health probes.

## Implications for Diagnostic Architecture

The per-engine diagnostic pattern should be exposed through the `EnginePlugin` interface:

| Diagnostic dimension | WARP | URnetwork | ByeDPI |
|---------------------|------|-----------|--------|
| **Readiness signal** | `last_handshake_time_sec > 0` | `peerCount() > 0` | `socksPort > 0` |
| **Health metric** | Handshake age | Active peer count | SOCKS5 handshake probe |
| **Recovery action** | DNS fallback + reconnect | `recover()` soft restart | Proxy restart with fallback args |
| **User message** | "DNS заблокирован" | "Ищем пиров…" | "Прокси не запустился" |

## Related Concepts

- [[concepts/warp-preflight-dns-exhaustion]] - WARP DNS failure pattern
- [[concepts/urnetwork-peer-watchdog-recovery]] - URnetwork peer loss + recovery
- [[concepts/byedpi-strategy-runtime-disconnect]] - ByeDPI proxy startup + strategy gap
- [[connections/false-positive-engine-status]] - Related: four false-positive status signals; this connection adds three false-failure diagnostic traps
- [[concepts/health-monitor-p2p-mismatch]] - HealthMonitor's generic probes cannot distinguish between these engine-specific failures
