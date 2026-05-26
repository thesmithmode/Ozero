---
title: "Singbox AIDL Async Silently Swallows Go Runtime Errors"
aliases: [singbox-aidl-async, aidl-runblocking-singbox]
tags: [android, aidl, singbox, coroutines, go]
sources:
  - "daily/2026-05-25.md"
created: 2026-05-25
updated: 2026-05-25
---

# Singbox AIDL Async Silently Swallows Go Runtime Errors

Using `serviceScope.launch` inside AIDL stub methods for the singbox engine silently discards exceptions thrown by the Go runtime. The caller (e.g., `SingboxEngine.attachTun`) receives a successful binder call return, but the actual start/stop operation has already failed internally. This manifests as "binder died" events from the engine process crashing without propagating the error.

## Key Points

- AIDL is fundamentally synchronous from the caller's perspective — the return happens before the coroutine inside `launch` completes
- `serviceScope.launch { singboxRuntime.start(...) }` — any exception inside is swallowed by the coroutine exception handler
- The engine process crashes (SIGABRT) but `attachTun` returns success → `OzeroVpnService` never sees a failure → VPN appears to start but doesn't work
- Fix: use `runBlocking { }` inside AIDL stubs for `startWithConfig` and `stop` — exceptions propagate as `RemoteException` to the caller
- Trade-off: `runBlocking` blocks the Binder thread; acceptable because singbox start is a short-lived operation

## Details

### The Problem Pattern

```kotlin
// SingboxEngineService.kt (broken)
override fun startWithConfig(config: String): Boolean {
    serviceScope.launch {          // ← returns immediately
        singboxRuntime.start(config) // ← exception here is lost
    }
    return true                    // ← always returns true, even if start fails
}
```

`singboxRuntime.start(config)` calls into Go code via JNI. If the Go runtime throws (e.g., due to the `go.Seq` conflict before the fix), the exception propagates into the coroutine but is swallowed by the coroutine exception handler. `serviceScope` has a `CoroutineExceptionHandler` that logs but does not propagate to the AIDL caller.

### The Fix Pattern

```kotlin
// SingboxEngineService.kt (fixed)
override fun startWithConfig(config: String): Boolean {
    return runBlocking {
        try {
            singboxRuntime.start(config)
            true
        } catch (e: Exception) {
            PersistentLoggers.error("SingboxEngineService", "start failed: $e")
            false
        }
    }
}
```

With `runBlocking`, the Binder thread is blocked until `singboxRuntime.start()` completes or throws. The caller in `SingboxEngine.attachTun` receives `false` → can propagate `Failure` to the VPN service → `OzeroVpnService` handles the failure correctly.

### Broader Principle

Any AIDL method that calls into native/Go code and needs the caller to observe success/failure **must** be synchronous. Async AIDL stubs are appropriate only for fire-and-forget operations where the caller doesn't need a result and failures are acceptable to silently drop. For engine lifecycle operations (start, stop, configure), synchronous execution is mandatory.

## Related Concepts

- [[concepts/singbox-engine-design]] - Overall singbox architecture
- [[concepts/engine-sdk-bridge-stop-lifecycle]] - Similar: stop() must propagate to release Go goroutines
- [[concepts/gomobile-go-seq-multi-sdk-conflict]] - The crash that the async pattern was hiding
- [[concepts/combined-aidl-race-elimination]] - Related AIDL design considerations

## Sources

- [[daily/2026-05-25.md]] — Singbox SIGABRT in `:engine_singbox`; `startWithConfig`/`stop` used `serviceScope.launch` → Go runtime errors silently swallowed → binder died without propagating failure; fix: `runBlocking` in AIDL stubs; errors now reach `SingboxEngine.attachTun` as `Failure`
