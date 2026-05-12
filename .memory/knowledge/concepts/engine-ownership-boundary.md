---
title: "Engine Ownership Boundary: VpnService as Sole Lifecycle Owner"
aliases: [engine-ownership, bridge-access-gate, ui-bridge-sigabrt]
tags: [architecture, vpn, engine, crash, urnetwork, gotcha]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Engine Ownership Boundary: VpnService as Sole Lifecycle Owner

`OzeroVpnService` is the sole owner of engine lifecycle (start/stop/restart). UI components (ViewModels, settings screens) must NOT call engine bridge JNI methods directly — they observe state via `TunnelController.state` only. Violating this boundary causes SIGABRT when UI polling flows continue JNI calls through the bridge while the engine is tearing down, corrupting Go runtime GC state.

## Key Points

- `OzeroVpnService` = sole lifecycle owner: only it calls `engine.start()`, `engine.stop()`, `bridge.start()`, `bridge.stop()`
- UI → engine state: ONLY through `TunnelController.state` (read-only StateFlow), never through bridge method calls
- `UrnetworkEngineSettingsViewModel` had 3 polling flows (peerCount/2s, unpaidBytes/30s, subscriptionBalance/60s) calling bridge JNI during teardown → Go GC SIGABRT
- `WhileSubscribed(5000)` does NOT stop polling while user is on settings screen — race window during engine switch
- Fix: all bridge methods gated by `running.get()` (AtomicBoolean); VM observes TunnelController.state and stops polling when not Connected

## Details

### The SIGABRT Mechanism

`UrnetworkEngineSettingsViewModel` launched three polling coroutines in `viewModelScope`:
- `peerCount` every 2 seconds via `sdkBridge.peerCount()`
- `unpaidBytes` every 30 seconds via `sdkBridge.unpaidBytes()`
- `subscriptionBalance` every 60 seconds via `sdkBridge.subscriptionBalance()`

Each call crossed JNI into the URnetwork Go SDK (`libgojni.so`). When the user switched engines (URnetwork → WARP), `OzeroVpnService.stopVpn()` began tearing down URnetwork's Go SDK state. The VM's polling flows — still active because the user was on the settings screen — continued calling bridge JNI methods against a partially-torn-down Go runtime. The Go GC encountered corrupted heap state from these concurrent accesses, triggering SIGABRT in `runtime.gcWriteBarrier`.

The `WhileSubscribed(5000)` strategy was insufficient because:
1. The 5-second stop timeout means flows stay active for 5s after the last collector unsubscribes
2. The user was still on the settings screen (collector active), so the flow never stopped
3. Engine teardown happens immediately on switch, not after 5 seconds

### The Architectural Principle

The fix establishes a clear boundary:

```
┌─────────────────────────────────────────┐
│ UI Layer (ViewModels, Compose screens)  │
│   reads: TunnelController.state         │
│   writes: TunnelController.connect()    │
│   NEVER: bridge.anyMethod()             │
└────────────────┬────────────────────────┘
                 │ StateFlow (read-only)
┌────────────────┴────────────────────────┐
│ VpnService Layer (OzeroVpnService)      │
│   owns: engine lifecycle                │
│   calls: bridge.start(), bridge.stop()  │
│   calls: engine.start(), engine.stop()  │
└─────────────────────────────────────────┘
```

Bridge methods that UI needs (peer count, balance, location) should be exposed through `TunnelController` as StateFlows populated by the VpnService, not through direct bridge access from ViewModels.

### Defensive Gate Pattern

As an immediate fix before the full architectural refactor, all bridge JNI methods are gated by `running.get()`:

```kotlin
class RealUrnetworkSdkBridge : UrnetworkSdkBridge {
    private val running = AtomicBoolean(false)
    
    override fun peerCount(): Int {
        if (!running.get()) return 0
        return nativePeerCount()
    }
}
```

The VM additionally gates its polling on `TunnelController.state`:

```kotlin
viewModelScope.launch {
    tunnelController.state.collect { state ->
        if (state is TunnelState.Connected && currentEngine == EngineId.URNETWORK) {
            // start polling
        } else {
            // stop polling
        }
    }
}
```

### Discovery Context

Three bugs were diagnosed simultaneously in v0.0.12, all stemming from Engine Ownership Boundary violations:

1. **SIGABRT** (Bug 1): UrnetworkEngineSettingsViewModel polling bridge during teardown
2. **Reorder not restarting** (Bug 2): `EngineSettingsRestartObserver.Snapshot` missing `engineAutoPriority` — reordering engines didn't trigger restart because the snapshot didn't capture priority changes
3. **Split tunnel filter** (Bug 3): `AppListProvider.isUserVisibleApp` excluded system services with INTERNET permission — separate root cause (see [[concepts/split-tunnel-internet-permission-filter]])

## Related Concepts

- [[concepts/engine-switch-chain-cascading-failures]] - Prior engine-switch bugs; ownership boundary violation is a new failure class discovered in v0.0.12
- [[concepts/warp-handle-leak-sigabrt]] - Related Go GC SIGABRT from unpaired handles; same crash signature, different trigger (handle leak vs concurrent JNI access)
- [[concepts/dual-go-runtime-eager-loading]] - Go runtime lifecycle management; ownership boundary prevents UI-triggered concurrent access during teardown
- [[concepts/go-runtime-process-isolation]] - Process isolation eliminates cross-runtime SIGABRT but ownership boundary is still needed for single-runtime engines

## Sources

- [[daily/2026-05-11.md]] - Session 14:12: 3 bugs traced to Engine Ownership Boundary violation; UrnetworkEngineSettingsViewModel polling bridge during teardown → SIGABRT; EngineSettingsRestartObserver.Snapshot missing engineAutoPriority; AppListProvider filter wrong
