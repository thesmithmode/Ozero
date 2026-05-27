---
title: "VPN Slot Self-Detection False Positive"
aliases: [isExternalVpnActive-owneruid, vpn-self-detect, external-vpn-false-positive]
tags: [vpnservice, android, routing, lifecycle, bug]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# VPN Slot Self-Detection False Positive

`isExternalVpnActive()` in v0.1.11 checked for VPN network capabilities without verifying `ownerUid`. When Ozero's own VPN slot was in a dying state (stopped but not yet cleaned up by Android), `isExternalVpnActive()` detected it as an active external VPN — triggering a 750ms startup delay and `protect()` EPERM conflicts, causing a reconnect loop. Fixed in v0.1.12 (commit `c1123b04`) by filtering out networks owned by `Process.myUid()`.

## Key Points

- `isExternalVpnActive()` without `ownerUid` check → own dying VPN slot detected as "external VPN"
- Trigger: `Transports: CELLULAR|VPN` capability present on own just-stopped slot
- Effect: 750ms delay at VPN start + `protect()` EPERM conflict → repeated reconnect timeouts
- Diagnostic log pattern: `external VPN active at start — caps=[ Transports: CELLULAR|VPN` with no actual 3rd-party VPN running
- Fix: compare `network.ownerUid` against `Process.myUid()` before reporting as external; skip own slot
- API 29+ (Android 10 Q) requirement for `ownerUid` access

## Details

### The Failure Sequence

In v0.1.11, when the VPN reconnects or restarts:

1. `OzeroVpnService` calls `stopVpn()` → TUN fd released, VPN slot marked as stopping
2. Shortly after, `startVpn()` is called
3. At step 3, the Android VPN slot from step 1 is still visible to `ConnectivityManager` with `Transports.TRANSPORT_VPN` capability
4. `isExternalVpnActive()` iterates active networks, finds one with `TRANSPORT_VPN`, returns `true`
5. Service waits 750ms (external VPN backoff delay) then tries again
6. Each retry sees the same dying slot → loop continues

The 750ms backoff was designed for genuine external VPNs (third-party apps like WireGuard, OpenVPN). It was never intended to fire on Ozero's own lifecycle transitions.

### Diagnostic Pattern

In ozero.log, the false positive appears as:

```
external VPN active at start — caps=[ Transports: CELLULAR|VPN ... ownerUid=<our own uid>]
```

When this line appears and no third-party VPN is installed, the ownerUid matches the app's own UID — the dying slot from the previous connection.

### Fix

```kotlin
fun isExternalVpnActive(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val myUid = Process.myUid()
    return cm.allNetworks.any { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@any false
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@any false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.ownerUid != myUid  // skip our own VPN slot
        } else {
            true  // pre-Q: can't distinguish, assume external
        }
    }
}
```

`ownerUid` is available from API 29 (Android 10). On older devices, the guard is skipped and the behavior reverts to the pre-fix logic.

### Observed Symptoms (v0.1.11)

- VPN cycling on both WiFi and mobile (2021-05-21 log, 21:47–21:51)
- TX rising (packets sent) but RX=0 (no response received) — initially misdiagnosed as Cloudflare-side issue
- Single WireGuard handshake event (rx=92 bytes) confirming tunnel was briefly established before restart loop triggered

## Related Concepts

- [[concepts/vpn-slot-conflict-detection]] - Broader VPN slot conflict detection patterns at service start
- [[concepts/vpn-slot-coexistence-crash]] - Related: two VPN slots from different apps conflict
- [[concepts/vpnservice-double-shutdown-guard]] - Another false-positive lifecycle trap: stopping flag reset enables double shutdown

## Sources

- [[daily/2026-05-22.md]] - Session 19:32: ozero.log v0.1.11 analysis; cycling VPN on WiFi+mobile; isExternalVpnActive() without ownerUid check → own dying slot detected as external → 750ms delay + protect() EPERM loop; fix: ownerUid != myUid guard (API 29+); commit c1123b04 in v0.1.12; TX up + RX=0 initially misread as Cloudflare side, actually our reconnect loop
