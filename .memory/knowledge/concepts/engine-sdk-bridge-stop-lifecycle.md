---
title: "Engine SDK Bridge Stop Lifecycle"
aliases: [sdk-bridge-stop, engine-stop-propagation, sdkbridge-goroutine-leak]
tags: [engine, lifecycle, android, urnetwork, goroutine, gotcha]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# Engine SDK Bridge Stop Lifecycle

When an engine wraps a native SDK through a bridge interface, the engine's `stop()` method must call through to the bridge's `stop()`. Omitting this call leaves the SDK running in memory after the engine reports `Stopped` state. For Go-based SDKs (URnetwork, WARP), this means goroutines, network connections, and the Go runtime's internal scheduler remain allocated, causing resource leaks and duplicate SDK instances on the next `start()`.

## Key Points

- `EngineUrnetwork.stop()` initially set `_state.value = EngineState.Stopped` without calling `sdkBridge.stop()` — a P1 code review finding
- `RealUrnetworkSdkBridge.stop()` does `networkSpaceManager?.close(); networkSpaceManager = null; _isRunning.value = false` — this is the actual SDK teardown
- Without bridge `stop()`, `Sdk.newNetworkSpaceManager()` instance persists; on re-`start()`, a second instance is created alongside the first
- Go runtime goroutines do not stop when Kotlin GC collects the wrapper — they require explicit `close()` calls
- Code review finding category: P1 Critical (leak potential + re-start crash risk)

## Details

### The Disconnect Pattern

Engine state machines have a `stop()` contract that callers (the VPN service, watchdog, ChainOrchestrator) rely on: after `stop()` returns, all engine resources should be released and the engine safe to `start()` again. When the engine implementation delegates to a bridge for SDK lifecycle, the bridge `stop()` must be part of this contract.

The URnetwork case: `EngineUrnetwork` holds a `UrnetworkSdkBridge` dependency. The bridge interface has `start()`, `stop()`, and `isRunning`. The engine's `stop()` was written to update its own state variable but never delegate to the bridge. The bridge's `stop()` implementation closes the `NetworkSpaceManager` (which triggers Go runtime cleanup) and resets the `isRunning` flag. Without this delegation, the Go SDK kept running as a zombie even though Ozero considered the engine stopped.

### Risk on Re-Start

When the engine is restarted (user toggles VPN off/on, engine watchdog triggers, or engine switching), `start()` is called while the previous SDK instance is still running. `RealUrnetworkSdkBridge.start()` calls `Sdk.newNetworkSpaceManager()`, creating a second instance. The two Go runtime instances compete for network resources and goroutine coordination structures. The result is non-deterministic: degraded connectivity, incorrect state, or SIGABRT from the Go runtime's internal consistency checks.

### Fix Pattern

```kotlin
// BEFORE (missing bridge delegation)
override suspend fun stop() {
    _state.value = EngineState.Stopped
}

// AFTER (correct delegation)
override suspend fun stop() {
    sdkBridge.stop()
    _state.value = EngineState.Stopped
}
```

The bridge `stop()` is called first so that SDK cleanup completes before the state transition signals to observers that the engine is stopped. This ordering prevents a race where an observer receives `Stopped` and immediately calls `start()` before SDK teardown finishes.

### Verification Pattern

Checking for this class of bug: for any engine that holds a bridge or SDK handle, verify that `stop()` explicitly calls `bridge.stop()` / `sdk.close()` / equivalent teardown. This is especially important for Go/JNI-backed engines where the runtime has its own lifecycle independent of Kotlin object lifecycle.

## Related Concepts

- [[concepts/urnetwork-runtime-release-lifecycle]] - Go runtime release lifecycle in URnetwork SDK
- [[concepts/dual-go-runtime-eager-loading]] - Related: two Go runtimes loaded simultaneously cause SIGABRT
- [[concepts/urnetwork-networkspace-init]] - The start() side of the NetworkSpaceManager lifecycle
- [[concepts/go-runtime-process-isolation]] - Process isolation strategy to prevent Go runtime conflicts

## Sources

- [[daily/2026-05-02.md]] - Session 11:03 code review: `EngineUrnetwork.stop()` not calling `sdkBridge.stop()` identified as P1 Critical (goroutine leak, re-start crash risk); fix implemented and pushed in commit `46ad17d`
