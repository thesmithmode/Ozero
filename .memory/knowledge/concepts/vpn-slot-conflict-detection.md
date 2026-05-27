---
title: "VPN Slot Conflict: establish() Null Means Another App Owns the Slot"
aliases: [vpn-slot-taken, establish-null, vpn-coexistence-crash]
tags: [android, vpn, gotcha, architecture]
sources:
  - "daily/2026-05-19.md"
  - "daily/2026-05-20.md"
  - "daily/2026-05-21.md"
  - "daily/2026-05-22.md"
created: 2026-05-19
updated: 2026-05-22
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

`logActiveExternalVpn()` writes via `PersistentLoggers.warn` because the slot-conflict event is a one-shot pre-failure diagnostic that must survive into `boot.log` for post-mortem (see [[concepts/vpn-slot-coexistence-crash]] for the canonical implementation and [[concepts/persistent-logger-accumulation-trap]] for the PersistentLoggers usage rule — one-shot critical events qualify):

```kotlin
private fun logActiveExternalVpn() {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return
    val vpnNetworks = cm.allNetworks.filter { 
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true 
    }
    if (vpnNetworks.isNotEmpty()) {
        PersistentLoggers.warn(TAG, "Active VPN networks: ${vpnNetworks.map { cm.getLinkProperties(it) }}")
    }
}
```

### detekt ReturnCount CI Failure (Related)

The fix added an extra return path to `StartSequenceCoordinator.awaitEngineReady` and related methods, pushing `ReturnCount` above the detekt limit of 8. Two `pick == null` early-return branches were merged into a single `if/else` block to reduce return count without changing semantics.

### Android VPN Slot Semantics

Android allows only one active VPN at a time per device (not per user session). The `VpnService.prepare(ctx)` intent flow revokes any existing VPN and grants permission to the requesting app. However, if the user grants permission to app A but then immediately starts app B's VPN (which also called `prepare()`), app A's `onRevoke()` fires. When app A tries `establish()` next, null is returned because app B's VPN is active.

### VPN Slot Race: EXTERNAL_VPN_RELEASE_DELAY_MS (2026-05-21)

Task37 (commit `3965b00a`) introduced a symmetric fix for the race at VPN startup when an external VPN is detected active:

- `REVOKE_KILL_DELAY_MS` reduced 2500ms → 1000ms: gives Ozero enough time for log flush + Go goroutine teardown without holding the slot too long for the next app
- New `EXTERNAL_VPN_RELEASE_DELAY_MS = 750ms`: when `isExternalVpnActive()` detects another VPN is active at the time of `startVpn`, the engine waits 750ms before calling `establish()` to give the OS time to complete teardown of the external VPN. Without this delay, `establish()` can return `null` even though the user already granted permission and the other VPN is in the process of shutting down.

```kotlin
if (isExternalVpnActive()) {
    Log.w(TAG, "External VPN active at start — waiting ${EXTERNAL_VPN_RELEASE_DELAY_MS}ms")
    delay(EXTERNAL_VPN_RELEASE_DELAY_MS)  // give OS time for external VPN teardown
}
val tun = builder.establish()
```

### onRevoke Kill for Clean Slot Release (2026-05-20)

`VpnService.onRevoke()` fires when the Android system revokes VPN permission — this happens when the user starts another VPN app. Ozero's VPN process holds the Android VPN slot AND a loaded `libgojni.so` Go runtime (which cannot be unloaded on Android). If the process stays alive after revoke, the slot is technically released by the system, but leftover JNI/Go state can interfere with the next VPN app startup.

Fix: `OzeroVpnService.onRevoke()` calls `Handler.postDelayed({ Process.killProcess(Process.myPid()) }, REVOKE_KILL_DELAY_MS)` with `REVOKE_KILL_DELAY_MS = 2500L`. The 2.5s delay allows:
- Active logging/boot.log flush to complete
- Graceful teardown of active Go goroutines
- Any in-flight AIDL shutdown sequence to finish

This is exclusive to `onRevoke`. `stopVpn()` and `onDestroy()` do NOT kill the process — those paths mean the user manually stopped the VPN, and the process must stay alive for UI state updates, balance refresh, and reconnect logic. Only `onRevoke` signals "user explicitly chose another VPN and we are no longer needed."

Sentinel: `OzeroVpnServiceLifecycleTest` verifies `onRevoke()` path contains `processKiller.kill` + `postDelayed` + `REVOKE_KILL_DELAY_MS >= 1500`.

```kotlin
override fun onRevoke() {
    super.onRevoke()
    Log.w(TAG, "VPN slot revoked — freeing process in ${REVOKE_KILL_DELAY_MS}ms")
    Handler(Looper.getMainLooper()).postDelayed({
        Process.killProcess(Process.myPid())
    }, REVOKE_KILL_DELAY_MS)
}
```

### isExternalVpnActive() ownerUid False Positive (v0.1.11 Regression)

A related bug found via user log analysis (v0.1.11, 2026-05-21): `isExternalVpnActive()` was checking for any VPN-capable network without filtering by `ownerUid`. When Ozero's own VPN was in a dying/restarting state, the system still reported it as an active VPN network. `isExternalVpnActive()` would detect it as "another VPN is active", triggering:

1. A 750ms delay at engine start
2. A `protect()` call conflict (can't protect sockets through a VPN you own but are tearing down)
3. Repeated timeout → `Connecting` loop
4. User symptom: VPN keeps cycling, never connects on both WiFi and mobile data

Log pattern indicating false positive:
```
external VPN active at start — caps=[ Transports: CELLULAR|VPN
```
When no actual external VPN is running, this line means Ozero detected its own dying VPN slot.

Fix (commit `c1123b04`, shipped in v0.1.12): filter the VPN network check by `ownerUid`:
```kotlin
private fun isExternalVpnActive(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return false
    return cm.allNetworks.any { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@any false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            caps.ownerUid != Process.myUid()  // API 29+: exclude own VPN slot
    }
}
```

**Diagnostic shortcut:** If a user reports continuous VPN cycling without an active external VPN, check the log for `external VPN active at start` → if present, the ownerUid guard was missing or bypassed.

## Related Concepts

- [[concepts/vpnservice-builder-traps]] — related Builder API gotchas (setBlocking, IPv6 routes, etc.)
- [[concepts/tun-self-exclusion-sdk-engines]] — the broader coexistence picture with SDK engines
- [[concepts/engine-switch-chain-cascading-failures]] — multiple rapid VPN start attempts chain
- [[concepts/dual-go-runtime-eager-loading]] — Go runtime cannot be unloaded; process kill is the only clean release mechanism

## Sources

- [[daily/2026-05-19.md]] — Session 16:23: root cause in ozero.log L34381-34540 (external VPN captured slot → establish() null → silent Idle); fix: `onEngineDied("VPN slot занят")` before `stopVpnRequest`; `logActiveExternalVpn()` diagnostic; detekt ReturnCount CI fail resolved by merging null branches
- [[daily/2026-05-20.md]] — Session (VPN slot onRevoke): `onRevoke()` → `postDelayed(Process.killProcess, 2500ms)` releases libgojni + VPN slot cleanly for next app; `stopVpn/onDestroy` do NOT kill — only revoke means "user chose another VPN"
- [[daily/2026-05-21.md]] — Task37: `REVOKE_KILL_DELAY_MS` 2500→1000ms; new `EXTERNAL_VPN_RELEASE_DELAY_MS = 750ms` for external VPN race at startVpn; commit `3965b00a`
- [[daily/2026-05-22.md]] — Session 19:32: user v0.1.11 log — `isExternalVpnActive()` missing `ownerUid` filter detected own dying VPN as external VPN → false positive → cycling loop; fix `c1123b04` (API 29+ ownerUid guard) shipped in v0.1.12
