---
title: "URnetwork Peer Watchdog Auto-Recovery Pattern"
aliases: [peer-watchdog, urnetwork-peer-loss, peer-discovery-recovery]
tags: [urnetwork, vpn, engine, reliability, pattern]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# URnetwork Peer Watchdog Auto-Recovery Pattern

URnetwork's P2P mesh loses peer discovery after 4-5 minutes of connected operation. The Go SDK's peer table drains and no new peers are discovered, effectively creating a connected-but-non-functional tunnel. The fix introduces a peer-watchdog pattern: `OzeroVpnService` calls `recover()` on the URnetwork bridge before transitioning to Failed state, giving the SDK a chance to re-establish peer discovery without a full VPN restart.

## Key Points

- Peer discovery loss occurs 4-5 minutes after initial connect — peers drain from the mesh table and are not replaced
- Symptom: `Connected` state in UI but zero traffic throughput; peer count drops from 7+ to 0 over 2-3 minutes
- Root cause likely in Go SDK's peer discovery timer or network event handling — not in Ozero's integration layer
- Fix: `recover()` method on `UrnetworkSdkBridge` called by `OzeroVpnService` before `Failed` transition — triggers SDK-level peer re-discovery
- The watchdog pattern is per-engine: only URnetwork needs `recover()` before failure; WARP and ByeDPI have different failure/recovery semantics

## Details

### The Peer Loss Mechanism

After successful URnetwork engine start, the SDK establishes connections to P2P relay peers. Initial peer count (typically 5-7) is healthy and traffic flows. After approximately 4-5 minutes, observed in ozero.log analysis, the peer count begins declining. No new peers are discovered to replace those that disconnect naturally (peer churn is normal in P2P meshes). Within 2-3 minutes of the decline starting, peer count reaches 0 and the tunnel becomes non-functional.

This pattern was observed consistently across multiple test sessions. The timing suggests a periodic discovery mechanism in the Go SDK that either fails to re-trigger or encounters an error that prevents subsequent discovery rounds. The issue is upstream in the URnetwork SDK, not in Ozero's bridge or engine implementation.

### The Watchdog Pattern

Instead of immediately transitioning to `Failed` state when health monitoring or peer count indicates engine dysfunction, `OzeroVpnService` first attempts recovery:

```kotlin
// In OzeroVpnService engine health check
if (currentEngine == EngineId.URNETWORK && peerCount == 0) {
    Log.w(TAG, "URnetwork peer count dropped to 0, attempting recover")
    sdkBridge.recover()  // triggers SDK peer re-discovery
    delay(RECOVER_GRACE_PERIOD_MS)  // give SDK time to find peers
    if (sdkBridge.peerCount() > 0) {
        // recovery succeeded, stay connected
        return
    }
    // recovery failed, transition to Failed
}
```

The `recover()` method on the SDK bridge triggers the Go SDK's internal recovery mechanism — typically re-initializing the peer discovery subsystem without tearing down the entire network stack. This is significantly faster than a full VPN stop/start cycle and avoids the Go runtime teardown that can trigger SIGABRT on multi-engine setups.

### Country Switch UX Integration

The same session introduced a `switchingCountry` flag in the URnetwork ViewModel. When a user selects a different exit country, the VM sets `switchingCountry = true`, the StatusRow displays "Переключение страны…", and the engine performs a soft restart (peer re-discovery targeting the new country's relay nodes) rather than a full VPN restart. The watchdog `recover()` mechanism supports this by providing a non-destructive way to refresh the peer mesh.

### UI Cleanup: Solana/URx/Wallet Removal

The same session removed Solana, URx token, wallet address, and balance UI elements from URnetwork settings. The VM pollers for `unpaidBytes` and `subscriptionBalance` were retained (bridge methods exist) but the UI no longer displays them. This cleanup reflects Ozero's use of URnetwork as a pure P2P VPN engine without the cryptocurrency/payment layer that URnetwork's own app exposes.

### Per-Engine Recovery Semantics

The watchdog pattern does not generalize to all engines:

| Engine | Failure Mode | Recovery | Full Restart Needed |
|--------|-------------|----------|-------------------|
| URnetwork | Peer loss | `recover()` → peer re-discovery | Only if recover() fails |
| WARP | DNS/handshake fail | None — config or network issue | Yes |
| ByeDPI | jniStartProxy=-1 | None — proxy binary issue | Yes |

WARP and ByeDPI failures are typically configuration or binary issues that cannot be resolved by a soft recovery. URnetwork's peer loss is a transient network condition that the SDK can address through re-discovery.

## Related Concepts

- [[concepts/health-monitor-p2p-mismatch]] - HealthMonitor false DEGRADED for P2P engines; watchdog provides actual recovery where HealthMonitor only reports
- [[concepts/urnetwork-sdk-integration]] - Parent integration article; peer watchdog is the latest addition to the integration
- [[concepts/engine-ownership-boundary]] - VpnService owns recovery decision; VM observes state only
- [[concepts/engine-switch-chain-cascading-failures]] - Watchdog avoids full restart, reducing cascading failure risk from rapid stop/start

## Sources

- [[daily/2026-05-12.md]] - Session 21:19: URnetwork peer discovery lost after 4-5 min; recover() added to OzeroVpnService before Failed transition; country switch UX with switchingCountry flag; Solana/URx/wallet UI removed
