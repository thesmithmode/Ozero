---
title: "Android VPN Slot Coexistence: External VPN Captures Slot"
aliases: [vpn-slot-conflict, establish-null-vpn, coexistence-crash, external-vpn-steal]
tags: [android, vpn, gotcha, architecture, reliability]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# Android VPN Slot Coexistence: External VPN Captures Slot

Android allows only one active VPN connection per device. When a second VPN app (e.g., the original URnetwork app) establishes a connection while Ozero is running, Android's `VpnService.onRevoke()` fires in Ozero, transitioning state to Idle. On the user's next connect attempt, `Builder.establish()` returns `null` because the VPN slot is held by the external app — previously logged as cryptic "permission revoked?" and silently transitioned to Idle. The fix surfaces this as an explicit `Failed` state with a user-actionable message.

## Key Points

- `VpnService.prepare()` returning `null` means permission not needed (system remembers prior OK) — not an error by itself
- `VpnService.Builder.establish()` returning `null` means the VPN slot is genuinely occupied by another UID — this IS the error
- Prior behavior: `establish()` null → silent Idle → user spams Connect 7+ times → engine-switch cascade
- Fix in `StartSequenceCoordinator.establishTunForEngine`: null/throws → `tunnelController.onEngineDied(engineId, "VPN slot занят — выключите другой VPN")` BEFORE `stopVpnRequest`
- Diagnostic: `OzeroVpnService.logActiveExternalVpn()` writes active VPN networks via `ConnectivityManager` to persistent log
- Root cause found in ozero.log L34381-34540: `onRevoke` triggered by external VPN, subsequent `establish()` null

## Details

### The Slot Capture Sequence

Android maintains a system-level VPN slot per user. The flow when a conflict occurs:

1. Ozero VPN is active (Ozero holds the VPN slot)
2. User opens external VPN app (URnetwork original, or any other VPN)
3. External app calls `VpnService.prepare()` → Android shows permission dialog or auto-grants
4. External app calls `Builder.establish()` → Android captures the slot, fires `VpnService.onRevoke()` on Ozero
5. Ozero's `onRevoke()` → `stopVpn()` → `TunnelState.Idle`
6. User taps Connect in Ozero → `startVpn()` → `StartSequenceCoordinator.establishTunForEngine()`
7. `prepare()` may return `null` (system cached permission OK — not an error)
8. `Builder.establish()` returns `null` — slot is occupied by external UID
9. Prior code: log "permission revoked?" + return → `TunnelState.Idle`
10. User confused, taps Connect again → 7+ rapid `startVpn` → engine-switch cascade

### The Fix

```kotlin
// StartSequenceCoordinator.establishTunForEngine
val tunFd = try {
    vpnServiceRef.buildTunBuilder(engine, config).establish()
} catch (e: Exception) {
    null
}
if (tunFd == null) {
    tunnelController.onEngineDied(
        engineId,
        "VPN slot занят — выключите другой VPN"
    )
    stopVpnRequest()
    return
}
```

Surfacing `Failed` state with a user-readable message stops the connect-spam loop.

### Diagnostic Logging

`OzeroVpnService.logActiveExternalVpn()` queries `ConnectivityManager` for active VPN networks and logs them via `PersistentLoggers.warn`. This captures the conflicting VPN info in `boot.log` for post-mortem analysis:

```kotlin
private fun logActiveExternalVpn() {
    val cm = getSystemService(ConnectivityManager::class.java)
    val vpnNets = cm.allNetworks.filter { net ->
        cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
    if (vpnNets.isNotEmpty()) {
        PersistentLoggers.warn(TAG, "Active VPN networks: ${vpnNets.map { cm.getLinkProperties(it) }}")
    }
}
```

## Related Concepts

- [[concepts/engine-switch-chain-cascading-failures]] — silent Idle from null `establish()` was a trigger for the 7× startVpn cascade pattern
- [[concepts/engine-ownership-boundary]] — VpnService must signal Failed, not silently idle, when slot is unavailable
- [[concepts/tun-self-exclusion-sdk-engines]] — related VPN slot and TUN configuration constraints

## Sources

- [[daily/2026-05-19.md]] — Session 15:13/16:23: root cause found in ozero.log L34381-34540; `establish()` null = external VPN holds slot; fix = `onEngineDied` + user message; `logActiveExternalVpn()` added for diagnostics; prior silent Idle caused 7+ connect attempts
