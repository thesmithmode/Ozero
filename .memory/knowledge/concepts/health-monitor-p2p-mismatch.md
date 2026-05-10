---
title: "HealthMonitor False Positive for P2P Engines"
aliases: [health-monitor-p2p, vpn-health-engine-mismatch, urnetwork-stability-false-positive]
tags: [architecture, health-monitoring, urnetwork, engine, gotcha]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-05-08
---

# HealthMonitor False Positive for P2P Engines

Ozero's `HealthMonitor` uses a probe-based approach to assess tunnel health: it measures whether packets are forwarded and responses arrive within thresholds calibrated for single-path VPN engines (WARP/AWG: ~20-50ms RTT). For URnetwork's P2P mesh engine, identical probes produce false positives — 7 active peers are visible and traffic flows, but the "Соединение нестабильна" banner fires because multi-hop P2P latency exceeds the single-path threshold.

## Key Points

- `HealthMonitor` thresholds are calibrated for WARP/AWG (~20-50ms RTT); P2P multi-hop paths show 100-300ms RTT — threshold crossed → DEGRADED
- With 7 active URnetwork peers and working traffic, HealthMonitor still fires DEGRADED — metric mismatch, not real failure
- DEGRADED state persisted 3+ minutes with no auto-recovery action (no engine restart, no peer refresh)
- Generic "Соединение нестабильна" message is not actionable for URnetwork users who see active peers in the UI
- Separate issue found simultaneously: `EngineUrnetwork.start()` resolves "Connected" while SOCKS5 port 10810 doesn't accept connections for 4+ minutes — false success from premature resolution
- Fix direction: engine-reported health override OR engine-specific thresholds; suppressing banner when engine has active peers is the quickest mitigation

## Details

### The Probe-Based Health Model Mismatch

`HealthMonitor` operates independently of engine state — it probes network health at the IP layer and flags DEGRADED when probe round-trip time or loss exceeds configured thresholds. This model fits single-path engines perfectly:

- **WARP/AWG**: one WireGuard peer, tunnel is binary up/down — probe timeout = tunnel down
- **ByeDPI**: SOCKS5 proxy over TUN, proxy either alive or not — probe timeout = proxy dead

URnetwork is structurally different. Traffic routes through multiple P2P peers simultaneously, each with different latency. Multi-hop paths add 2-5× the latency of a direct WARP endpoint. Peer churn during a session temporarily increases measured loss. None of these conditions mean "connection failed" — they are normal P2P routing characteristics.

The HealthMonitor has no knowledge of the active engine type. It applies the same thresholds regardless. A session where URnetwork has 7 active peers and working traffic triggers DEGRADED because multi-hop RTT exceeds the WARP-calibrated threshold.

### Evidence from ozero.log (Session 14:00, 2026-05-08)

Analysis of 12732 lines of ozero.log revealed:
- HealthMonitor transitioned to DEGRADED and remained there for 3+ minutes
- No recovery action fired — no engine restart, no peer refresh trigger, no timeout-based recovery
- URnetwork's own peer count API showed 7 active peers during the DEGRADED window
- Traffic was flowing (URnetwork SOCKS5 was routing correctly despite HealthMonitor assessment)

A separate but related issue was also observed: `EngineUrnetwork.start()` returned "Connected" state while the SOCKS5 proxy on port 10810 did not accept connections for 4+ minutes after the state change. This suggests `start()` resolves on P2P session establishment, not on proxy readiness — the engine reports success before it is actually usable.

### UX Impact

When HealthMonitor is DEGRADED, the main screen shows "Соединение нестабильна" prominently. For a user who sees "7 активных пиров" in the URnetwork panel, this message is contradictory. The user cannot take any meaningful action — the engine is working normally by its own metrics.

This was confirmed in Session 18:27 when a user reported: "7 пиров видно, всё работает, но плашка 'Связь нестабильна' — логическая ошибка в пороговых условиях HealthMonitor."

Correct URnetwork-specific messaging would show P2P state: active peer count, last-relay elapsed time, or "Ищем пиров" during initial discovery. The single-path "tunnel not responding" metaphor does not apply.

### Fix Directions

Three approaches, in order of architectural quality:

1. **Engine-reported health override** (best): Active engine provides `isHealthy(): Boolean`. If engine returns `true` (e.g., peer count > 0), HealthMonitor suppresses its probe-based DEGRADED state. Preserves monitoring abstraction while allowing engine-specific health semantics.

2. **Engine-specific thresholds** (medium): HealthMonitor queries active engine type and selects a threshold set. P2P engines get higher latency/loss thresholds. Requires HealthMonitor to know engine types.

3. **Suppress banner for P2P in UI layer** (quickest mitigation): UI checks engine type before showing the banner. For URnetwork, substitute a peer-count display. Does not fix the monitoring logic but unblocks UX.

## Related Concepts

- [[concepts/urnetwork-sdk-integration]] - URnetwork engine details; peer grace period and false-start stability issues documented
- [[concepts/vpn-engine-pipeline]] - Engine pipeline; HealthMonitor must align with engine-specific health semantics
- [[concepts/android-vpn-self-traffic-bypass]] - Related false diagnostic: in-app checkers can also show wrong data depending on routing context

## Sources

- [[daily/2026-05-08.md]] - Session 14:00: ozero.log analysis revealed HealthMonitor DEGRADED 3+ min with 7 URnetwork peers active; EngineUrnetwork.start() false-connected pattern; Session 18:27: user confirmed "7 пиров видно, всё работает" + DEGRADED banner — metric mismatch confirmed
