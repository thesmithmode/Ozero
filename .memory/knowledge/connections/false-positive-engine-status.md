---
title: "Connection: False-Positive Engine Status Signals"
connects:
  - "concepts/warp-false-connected-no-handshake"
  - "concepts/health-monitor-p2p-mismatch"
  - "concepts/android-vpn-self-traffic-bypass"
sources:
  - "daily/2026-05-07.md"
  - "daily/2026-05-08.md"
created: 2026-05-07
updated: 2026-05-07
---

# Connection: False-Positive Engine Status Signals

## The Connection

Three independent mechanisms in Ozero produce false-positive signals about VPN engine connectivity. Each operates at a different layer (tunnel handle, health monitoring, traffic routing) but all create the same user-facing symptom: the UI reports a functioning VPN while traffic is not flowing correctly. Together, they define a class of diagnostic traps where trusting any single status indicator leads to wrong conclusions.

## Key Insight

The non-obvious relationship is that VPN connectivity is a multi-layer property that no single metric can capture:

1. **WARP false-connected** ([[concepts/warp-false-connected-no-handshake]]): `awgTurnOn` returns a valid handle → engine reports `Connected` → but no WireGuard handshake has occurred. Tunnel exists at the OS level but no packets traverse it. The status signal (valid handle) is a necessary but not sufficient condition for connectivity.

2. **HealthMonitor false-degraded** ([[concepts/health-monitor-p2p-mismatch]]): Probe-based health check reports `DEGRADED` for a P2P engine with 7 active peers and working traffic. The status signal (probe RTT > threshold) is calibrated for single-path engines and produces false results for multi-hop P2P. This is the inverse false-positive: the engine works but the monitor says it does not.

3. **IP checker self-bypass** ([[concepts/android-vpn-self-traffic-bypass]]): In-app IP check shows the device's real ISP IP, suggesting the VPN is not working. But the VPN app's own traffic is exempt via `addDisallowedApplication` or `protect()` sockets. External tools confirm the VPN is routing correctly. The status signal (own IP unchanged) is measured on a traffic path that intentionally bypasses the tunnel.

Each false-positive has a different mechanism but the same consequence: the developer or user takes a corrective action (restart engine, investigate tunnel config, file a bug) based on incorrect status information. The corrective action either wastes time or introduces new bugs.

## Evidence

From Ozero v0.0.5 through v0.0.7:

- **Session 2026-05-07 15:11**: WARP showed "Connected" for minutes with zero traffic on Russian ISP. Developers investigated DNS, routing, and stats before discovering the handshake never completed due to TSPU blocking vanilla WireGuard. The `Connected` status delayed root cause discovery by at least one session.

- **Session 2026-05-08 14:00**: HealthMonitor showed "Соединение нестабильна" while URnetwork had 7 active peers and was routing traffic correctly. User reported the discrepancy as a bug.

- **Session 2026-05-08 18:27**: In-app IP checker showed "Leninogorsk" (real ISP city) while browser whoer.net confirmed correct VPN endpoint. Users interpreted this as "VPN not working."

In all three cases, the first hypothesis based on the status signal was wrong. The correct diagnosis required cross-referencing multiple independent signals:
- WARP: status + `last_handshake_time_sec` + packet counters
- HealthMonitor: probe result + engine peer count + traffic flow
- IP checker: in-app result + browser result + `addDisallowedApplication` audit

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] - Tunnel handle valid but no handshake — OS-level false positive
- [[concepts/health-monitor-p2p-mismatch]] - Probe thresholds wrong for engine type — monitoring-level false positive
- [[concepts/android-vpn-self-traffic-bypass]] - Self-traffic exempt from tunnel — routing-level false positive
- [[concepts/android-silent-crash-diagnosis]] - Related diagnostic principle: absence of evidence (no log) is not evidence of absence (no crash); same epistemology applies to status signals
