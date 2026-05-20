---
title: "WARP False Connected State: awgTurnOn OK Without Handshake"
aliases: [awg-false-connected, warp-no-handshake, connected-but-no-tunnel]
tags: [warp, amneziawg, gotcha, ui, debugging]
sources:
  - "daily/2026-05-07.md"
  - "daily/2026-05-14.md"
created: 2026-05-07
updated: 2026-05-20
---

# WARP False Connected State: awgTurnOn OK Without Handshake

`GoBackend.awgTurnOn` returning a valid (non-negative) tunnel handle does not guarantee that a WireGuard handshake has been completed with the peer. The engine transitions to `Connected` state immediately on a successful `awgTurnOn` return, but the actual WireGuard handshake may not occur for seconds — or may never occur if the endpoint is unreachable, AWG obfuscation parameters are wrong, or TSPU is blocking the handshake. The UI displays "Connected" while no traffic can flow, creating a false-positive status.

## Key Points

- `awgTurnOn` returns a valid handle as soon as the tunnel interface is created and goroutines are started — before any handshake attempt
- Engine state transitions to `Connected` on valid handle return; UI shows connected immediately
- Actual WireGuard handshake requires: endpoint reachable + correct public key + correct AWG obfuscation params (if TSPU active)
- The correct verification signal is `last_handshake_time_sec` from `awgGetConfig()` output — non-zero means a handshake completed
- Without handshake polling, users see "Connected" for minutes with zero traffic, then assume the VPN is broken

## Details

### The False-Connected Mechanism

AmneziaWG's `awgTurnOn(name, fd, config, uapiPath)` performs three operations synchronously before returning:

1. Creates a TUN interface wrapper around the provided file descriptor
2. Parses the WireGuard config (INI format) and sets up peer state
3. Starts goroutines for packet forwarding and handshake initiation

Steps 1-2 are purely local; step 3 starts async goroutines that initiate the handshake. The function returns the tunnel handle immediately after step 3, without waiting for the handshake to complete. In Ozero's `RealWarpSdkBridge`, the valid handle triggers a state change to `Connected` in the `TunnelState` flow, which the UI observes and displays.

The WireGuard handshake is a cryptographic exchange between the local client and the remote peer. It requires the peer to be reachable, the public key to match, and (when AWG obfuscation is active) the Jc/Jmin/Jmax/H-values to be compatible. If any of these conditions fail, the handshake never completes, but the tunnel handle remains valid and the Go runtime continues retrying in the background. No error is surfaced to the JNI caller.

### The Handshake Verification Gap

The WireGuard UAPI exposes `last_handshake_time_sec` per peer in the config dump returned by `awgGetConfig(handle)`. This field is:

- `0` when no handshake has ever completed (initial state, or endpoint unreachable)
- A Unix timestamp when the last successful handshake occurred
- Updated every ~2 minutes on keep-alive re-handshake

Polling `awgGetConfig` and parsing `last_handshake_time_sec` provides ground truth about tunnel connectivity. The correct implementation pattern:

```kotlin
val handle = GoBackend.awgTurnOn(name, fd, ini, uapiPath)
if (handle >= 0) {
    // Tunnel created, but NOT necessarily connected
    state = TunnelState.Establishing  // not Connected yet
    
    // Poll for handshake
    coroutineScope.launch {
        repeat(30) { // 30 attempts × 2s = 60s max wait
            delay(2000)
            val config = GoBackend.awgGetConfig(handle)
            if (parseLastHandshakeTime(config) > 0) {
                state = TunnelState.Connected
                return@launch
            }
        }
        state = TunnelState.Failed("No handshake after 60s")
    }
}
```

### Discovery Context

In Ozero v0.0.5, the WARP engine showed "Connected" immediately after `awgTurnOn` returned a valid handle. On Russian ISPs with TSPU active, the WireGuard handshake was blocked because AWG obfuscation parameters were not yet applied (the vanilla invariant was still in effect at that time). Users saw "Connected" for minutes with zero traffic. External tools confirmed no packets were reaching the Cloudflare endpoint.

The diagnosis was delayed because the `Connected` state created false confidence — developers initially investigated DNS, routing, and traffic stats issues rather than questioning whether the tunnel was actually established. The real issue was that the handshake never completed due to TSPU blocking vanilla WireGuard.

This false-connected pattern was identified in session 15:11 alongside the AWG obfuscation discovery. Handshake polling was documented as a required follow-up but was not implemented in that session.

### Interaction with Mirror DNS

The same session identified suspicious DNS servers in the mirror auto-config response (`176.99.11.77`, `80.78.247.254`) — IPs that do not belong to Cloudflare. If the DNS servers are unreachable or return incorrect answers, applications behind the VPN cannot resolve hostnames even if the handshake succeeds. This creates a second layer of false connectivity: handshake completes but DNS fails.

### Implementation: UAPI Socket Polling (2026-05-14)

The handshake polling was implemented via `WarpHandshakeUapi.kt` using `LocalSocket` to read from the UAPI socket at `$dataDir/ozero-warp.sock`. The key decision: do NOT use `awgGetConfig(handle)` JNI for polling — calling it during partial handshake can SIGSEGV (Go runtime accesses incomplete state). The UAPI socket is a standard Unix domain socket read, no Go runtime involvement.

Polling parameters: 300ms interval, 10s timeout. Integrated into `EngineWarp.awaitReady()` — called between `routeTrafficForEngine()` and `onEngineStarted()` in the VPN start sequence. UI shows "Connected" only after `last_handshake_time_sec > 0` confirmed.

See [[concepts/warp-uapi-handshake-polling]] for full implementation details and [[concepts/engine-await-ready-pattern]] for the cross-engine readiness architecture.

## Related Concepts

- [[concepts/amneziawg-turnon-minus-one]] - The complementary failure mode: awgTurnOn returns -1 (complete failure); this article covers the case where awgTurnOn succeeds but connectivity does not follow
- [[concepts/warp-awg-obfuscation-russian-isps]] - AWG obfuscation is required for handshake to succeed under TSPU; without it, this false-connected state is the inevitable result
- [[concepts/health-monitor-p2p-mismatch]] - Another false-positive status signal: HealthMonitor reports DEGRADED for working P2P engine; both are instances of status signals not matching actual connectivity
- [[concepts/warp-handle-leak-sigabrt]] - Unpaired handles from false-connected sessions may accumulate if the bridge does not clean up stale handles
- [[concepts/warp-uapi-handshake-polling]] - Implementation: UAPI socket polling for handshake verification (not JNI)
- [[concepts/engine-await-ready-pattern]] - Cross-engine readiness gate architecture that uses handshake polling as WARP's signal

### Timeout Reduction and Failure Propagation (2026-05-19)

In v0.1.5, the WARP ready timeout was reduced from 10s to 5s (`EngineWarp.WARP_READY_TIMEOUT_MS`) and `TunnelController.SWITCHING_TIMEOUT_MS` from 12s to 6s. More importantly, the timeout behavior was changed: previously timeout → `Connected(WARP)` (false-connected, 0 b/s). Now: timeout → `engineWatchdog.handleEngineFailure` + `chainOrchestrator.stop` + return. No more false-Connected on WARP handshake timeout.

`awaitEngineReady` in `StartSequenceCoordinator` now returns `Boolean`: `true` = ready, `false` = timeout/failure. The false return triggers failure propagation rather than proceeding to `onEngineStarted`.

```kotlin
// Before (wrong): timeout → state = Connected(WARP) → 0 b/s, user confused
// After (correct): timeout → handleEngineFailure → UI shows Failed → user can retry
val ready = startSequenceCoordinator.awaitEngineReady(engineId, timeout)
if (!ready) {
    engineWatchdog.handleEngineFailure(engineId)
    chainOrchestrator.stop()
    return
}
```

**5s timeout revert (v0.1.5-4):** The 5s timeout was too aggressive — Cloudflare WARP handshake on slow/congested networks requires up to 10s. Users on slow connections saw every WARP start immediately fail with the honest-failure path. Reverted to `WARP_READY_TIMEOUT_MS=10s` and `SWITCHING_TIMEOUT_MS=12s`. The honest failure propagation is preserved; only the timeout duration was relaxed.

### handle=0 Misdiagnosis and Revert (2026-05-19)

Log showed `awgTurnOn JNI exit handle=0`. A session incorrectly concluded that `handle=0` was invalid and changed the error guard from `handle < 0` to `handle <= 0` (commit 3a2ba785, v0.1.5.1). This immediately broke all WARP starts: handle=0 is the *valid* first tunnel slot per Go bridge convention (`tunnelHandles` map starts at index 0; `-1` is the only error sentinel). Every clean WARP start was rejected, producing a false-negative `Failed` state.

Reverted in v0.1.5-4 by reading primary source `.claude/Контекст/amnezia-client/client/macos/gobridge/api.go:123-135`. See [[concepts/warp-awg-handle-zero-valid]] for the full analysis.

The actual false-connected root (handshake not arriving) was already addressed by `awaitReady()` returning `false` on timeout → `handleEngineFailure`. No relationship to the handle value.

## Sources

- [[daily/2026-05-07.md]] - Session 15:11: `awgTurnOn OK` + state=Connected does not mean real handshake; need polling `last_handshake_time_sec` from `awgGetConfig`; handshake polling identified as required follow-up; mirror DNS suspicion (176.99.11.77, 80.78.247.254 — not Cloudflare)
- [[daily/2026-05-14.md]] - Session 16:41: handshake polling implemented via WarpHandshakeUapi.kt + LocalSocket (not awgGetConfig JNI — SIGSEGV risk); 300ms/10s; integrated into EngineWarp.awaitReady()
- [[daily/2026-05-19.md]] - v0.1.5 session: `WARP_READY_TIMEOUT_MS` 10s→5s; `SWITCHING_TIMEOUT_MS` 12s→6s; `awaitEngineReady` returns Boolean; timeout → `handleEngineFailure` + `chainOrchestrator.stop` (no more false-Connected on handshake timeout); ozero.log confirmed awaitReady never got handshake in any WARP session; v0.1.5-4: 5s timeout reverted to 10s (too aggressive for slow networks); handle<=0 misdiagnosis found and reverted to handle<0
