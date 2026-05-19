---
title: "URnetwork SDK Go-Runtime Release After Stop"
aliases: [urnetwork-runtime-release, sdk-free-memory, go-runtime-singleton-release]
tags: [urnetwork, native, go, architecture, gotcha]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# URnetwork SDK Go-Runtime Release After Stop

The URnetwork Go SDK maintains a singleton Go runtime that persists after `bridge.stop()`. Without explicit release (`Sdk.freeMemory`, `setActiveNetworkSpace(null)`, nulling manager/space references), the runtime holds native resources that block other Go-based applications — including the original URnetwork app — from initializing their own SDK instance. This creates a symmetrical conflict: Ozero's unreleased runtime prevents URnetwork-app from starting, and vice versa.

## Key Points

- `bridge.stop()` alone does NOT release the Go-runtime singleton — native state persists in the process
- After `stop()`, must call: `Sdk.freeMemory()`, `setActiveNetworkSpace(null)`, null all manager/space references
- Without release, URnetwork-app crashes when opened while Ozero process is alive (even with all engines off) — Go-runtime singleton conflict
- `URN_STOP_TIMEOUT_MS` increased from 5s to 8s to give SDK time for network operation teardown before release
- Pattern is analogous to WARP process isolation solution but for a different failure mode — cross-app rather than cross-engine conflict
- `UrnetworkRuntime.release()` encapsulates the full teardown sequence as a single idempotent call

## Details

### The Singleton Conflict Mechanism

URnetwork's Go SDK initializes a singleton Go runtime via `JNI_OnLoad` when `libgojni.so` is loaded. This runtime manages goroutines, GC, signal handlers, and network state. The `bridge.stop()` method tears down the network connection (IoLoop, peers, tunnel) but leaves the Go runtime and its associated `NetworkSpaceManager`/`NetworkSpace` objects alive. The runtime continues consuming memory and holding OS-level resources (signal handlers, thread pools).

When the original URnetwork Android app launches on the same device, it loads its own `libgojni.so` and attempts to initialize its Go runtime. On Android, separate apps run in separate processes, so this should not conflict. However, within the same process (Ozero's), the singleton Go runtime from a previous `bridge.start()` still holds state. If Ozero's relay coordinator calls `bridge.start()` again (e.g., on engine switch), the SDK finds stale singleton state from the previous session.

The cross-app conflict was discovered when the user reported URnetwork-app crashing whenever Ozero was running (even with VPN disconnected). Ozero's process stayed alive (Android keeps recent apps in memory), and the unreleased Go-runtime singleton held resources that conflicted with URnetwork-app's independent SDK initialization in its own process. The exact conflict mechanism is through shared native state files or sockets, not through in-process singleton collision.

### The Release Sequence

`UrnetworkRuntime.release()` performs the full teardown:

```kotlin
fun release() {
    networkSpaceManager?.close()
    networkSpaceManager = null
    networkSpace = null
    Sdk.setActiveNetworkSpace(null)
    Sdk.freeMemory()
}
```

Order matters: `close()` the manager first (releases network state), null references (prevents stale access), clear active space (SDK-level cleanup), then `freeMemory()` (Go runtime GC hint). Each step is individually safe to call on null/already-released state.

### Stop Timeout Adjustment

`URN_STOP_TIMEOUT_MS` was increased from 5s to 8s because the SDK's network operations (peer disconnection, relay teardown, IoLoop shutdown) take longer than the basic `stop()` call. The sequence is: `bridge.stop()` → wait up to 8s for SDK to finish → `UrnetworkRuntime.release()`. If stop times out, release is still called — partial SDK state is better cleaned up than left indefinitely.

### Relationship to WARP Process Isolation

The WARP engine solved its Go-runtime conflict via process isolation (`android:process=":engine_warp"` + AIDL). URnetwork cannot use the same approach because its SDK requires `DeviceLocal` and `NetworkSpaceManager` instances that are tightly coupled with the main process's context. The release pattern is the correct solution for URnetwork: explicit lifecycle management within a single process, not process separation.

## Related Concepts

- [[concepts/dual-go-runtime-eager-loading]] - Original dual Go runtime problem; URnetwork runtime release is the complementary fix for SDK lifecycle (eager loading solved init timing)
- [[concepts/go-runtime-process-isolation]] - WARP uses process isolation; URnetwork uses explicit release — different solutions for the same class of Go-runtime singleton conflicts
- [[concepts/urnetwork-relay-always]] - Relay coordinator starts/stops the bridge; release must be called in the coordinator's stop path
- [[concepts/relay-coordinator-ownership-transfer]] - relayOwned AtomicBoolean determines WHO calls release; only the owner stops+releases
- [[connections/go-runtime-conflict-resolution-evolution]] - Fourth phase in the evolution: eager loading → guard (wrong) → process isolation (WARP) → explicit release (URnetwork)

## Sources

- [[daily/2026-05-18.md]] - Session 18:52: URnetwork-app crashes when Ozero process alive; root cause = Go-runtime singleton not released after bridge.stop(); fix = UrnetworkRuntime.release() with Sdk.freeMemory + setActiveNetworkSpace(null); URN_STOP_TIMEOUT_MS 5s→8s
