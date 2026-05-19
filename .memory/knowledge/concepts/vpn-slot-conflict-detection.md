---
title: "VPN Slot Conflict: establish() Null Means Another App Owns the Slot"
aliases: [vpn-slot-taken, establish-null, vpn-coexistence-crash]
tags: [android, vpn, gotcha, architecture]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# VPN Slot Conflict: establish() Null Means Another App Owns the Slot

`VpnService.Builder.establish()` returns `null` (without throwing) when another application currently owns the Android VPN slot. This is a normal system condition, not a bug. Silent `null` → Idle transition causes user confusion: they see the VPN as stopped with no explanation and spam "Connect" 7+ times. The correct handling is to transition to a `Failed` state with a descriptive message before stopping.

## Key Points

- `VpnService.prepare(ctx)` → `null` means current app already has permission (rare path to null)
- `Builder.establish()` → `null` means another app holds the VPN slot; Android system gave our app permission but a concurrent VPN revoked it before establish
- `establish()` does NOT throw — null return is the only signal; no exception, no log from Android
- Previous behavior: log "permission revoked?", transition to Idle silently → user retries 7+ times
- Correct behavior: call `tunnelController.onEngineDied(engineId, "VPN slot занят — выключите другой VPN")` BEFORE `stopVpnRequest`
- Diagnostic: `OzeroVpnService.logActiveExternalVpn()` via `ConnectivityManager.getActiveNetwork()` identifies which external VPN holds the slot

## Details

### Root Cause from ozero.log

Log lines L34381–34540: external VPN app captured Android VPN slot → our `onRevoke()` fired → state → Idle. Next user click: `prepare()` returned null (system still remembers our consent), but `Builder.establish()` returned null because the slot was actually held by another UID.

Prior code in `StartSequenceCoordinator.establishTunForEngine`:
```kotlin
val tun = builder.establish()  // returns null — slot taken
if (tun == null) {
    Log.w(TAG, "establish() returned null — permission revoked?")
    return  // silent Idle
}
```

### The Fix

```kotlin
val tun = try {
    builder.establish()
} catch (e: Throwable) {
    tunnelController.onEngineDied(engineId, "Ошибка создания VPN tunnel: ${e.message}")
    stopVpnRequest()
    return
}
if (tun == null) {
    logActiveExternalVpn()  // diagnostic: which app holds the slot
    tunnelController.onEngineDied(engineId, "VPN slot занят — выключите другой VPN")
    stopVpnRequest()
    return
}
```

The `onEngineDied` call triggers `Failed` state in `TunnelController`, which the UI renders as a dismissible error message. User sees the reason and knows to close the conflicting app.

### Diagnostic Helper

```kotlin
private fun logActiveExternalVpn() {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return
    val vpnNetworks = cm.allNetworks.filter { 
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true 
    }
    vpnNetworks.forEach { net ->
        Log.w(TAG, "Active VPN network: $net caps=${cm.getNetworkCapabilities(net)}")
    }
}
```

### detekt ReturnCount CI Failure (Related)

The fix added an extra return path to `StartSequenceCoordinator.awaitEngineReady` and related methods, pushing `ReturnCount` above the detekt limit of 8. Two `pick == null` early-return branches were merged into a single `if/else` block to reduce return count without changing semantics.

### Android VPN Slot Semantics

Android allows only one active VPN at a time per device (not per user session). The `VpnService.prepare(ctx)` intent flow revokes any existing VPN and grants permission to the requesting app. However, if the user grants permission to app A but then immediately starts app B's VPN (which also called `prepare()`), app A's `onRevoke()` fires. When app A tries `establish()` next, null is returned because app B's VPN is active.

## Related Concepts

- [[concepts/vpnservice-builder-traps]] — related Builder API gotchas (setBlocking, IPv6 routes, etc.)
- [[concepts/tun-self-exclusion-sdk-engines]] — the broader coexistence picture with SDK engines
- [[concepts/engine-switch-chain-cascading-failures]] — multiple rapid VPN start attempts chain

## Sources

- [[daily/2026-05-19.md]] — Session 16:23: root cause in ozero.log L34381-34540 (external VPN captured slot → establish() null → silent Idle); fix: `onEngineDied("VPN slot занят")` before `stopVpnRequest`; `logActiveExternalVpn()` diagnostic; detekt ReturnCount CI fail resolved by merging null branches
