---
title: "Dual Go Runtime Eager Loading to Prevent SIGSEGV"
aliases: [dual-go-runtime, am-go-ur-go-conflict, go-runtime-eager-load]
tags: [native, jni, android, go, crash, amneziawg, urnetwork, superseded]
sources:
  - "daily/2026-05-07.md"
  - "daily/2026-05-11.md"
created: 2026-05-07
updated: 2026-05-11
---

# Dual Go Runtime Eager Loading to Prevent SIGSEGV

**STATUS: SUPERSEDED v0.0.12.** This approach (single-process eager loading) prevented the *initialization-time* SIGSEGV but did not prevent the *runtime* SIGABRT in `runtime.gcWriteBarrier` caused by competing GC signal handlers across two Go runtimes in the same process. The definitive fix is process isolation via `android:process=":engine_warp"` + AIDL — see [[concepts/go-runtime-process-isolation]]. This article is retained as the v0.0.5–v0.0.11 historical record and to document the asymmetric per-process bootstrap (`isEngineWarpProcess()` guard) that depends on these load-library calls remaining in `OzeroApp.onCreate`, just split by process.

Ozero loads two Go-based native libraries in the same process: `libam-go.so` (AmneziaWG) and `libgojni.so` / `libur-go.so` (URnetwork). Rapid stop/start of engines (e.g., switching from WARP/AWG to URnetwork) causes SIGSEGV when one Go runtime is torn down while the other is initializing. The original fix was to eager-load both libraries in `OzeroApp.onCreate`, allowing them to coexist for the full application lifecycle rather than loading/unloading on engine start/stop. **This bounded the init-time SIGSEGV but the GC-signal-handler conflict during active dual-engine use remained unsolved until process isolation.**

## Key Points

- Two Go runtimes in one Android process (`libam-go.so` + `libgojni.so`) conflict on rapid stop/start — SIGSEGV during concurrent initialization/teardown
- Fix: eager-load both via `System.loadLibrary("am-go")` and `System.loadLibrary("gojni")` in `OzeroApp.onCreate`
- After eager loading, engine lifecycle only calls `awgTurnOn`/`awgTurnOff` (AWG) and SDK start/stop (URnetwork) — the Go runtimes themselves stay resident
- Eager loading in `OzeroApp.onCreate` is an exception to the `loadOnce()`-in-service rule — justified because both runtimes must coexist, and App.onCreate runs on main thread before any VPN operation
- `libam-go.so` size ~10 MB + `libgojni.so` size ~28 MB: ~38 MB resident native code; memory pressure on low-end devices may be a concern

## Details

### The Conflict Mechanism

Go's runtime (goroutine scheduler, GC, signal handlers) initializes once per process via `JNI_OnLoad` when the library is first loaded. When a Go library is unloaded (via class unloading or explicit close), the Go runtime attempts cleanup of goroutines and memory. If a second Go runtime is concurrently loading in another thread at that moment — which happens during engine switching — signal handlers and GC state collide. The result is SIGSEGV inside Go's runtime initialization code, which is difficult to diagnose because the stack trace points into the Go runtime, not application code.

The problem is specific to Ozero's multi-engine architecture: most apps load at most one Go library. Ozero loads two because WARP uses AmneziaWG (Go-based) and URnetwork uses its own Go SDK.

### Eager Loading Pattern

The fix moves both `System.loadLibrary` calls from engine initialization to application startup:

```kotlin
class OzeroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Eager-load both Go runtimes before any engine starts
        // They coexist for the full app lifecycle — never unloaded
        runCatching { System.loadLibrary("am-go") }
            .onFailure { BootFileLogger.error(TAG, "am-go load failed", it) }
        runCatching { System.loadLibrary("gojni") }
            .onFailure { BootFileLogger.error(TAG, "gojni load failed", it) }
    }
}
```

Both `runCatching` blocks are independent — failure to load one does not prevent the other. The persistent logger captures failures for diagnosis.

### Relationship to loadOnce() Pattern

Ozero's CLAUDE.md prohibits `System.loadLibrary` in `Application.onCreate` (the v1.0.1 SIGSEGV rule). The dual Go runtime case is an explicitly documented exception:

- The v1.0.1 rule targeted `libhev-socks5-tunnel.so` on Nubia ROM, where loading in a coroutine worker thread caused a SIGSEGV in `libglnubia.so`
- The dual-runtime case loads both libraries on the main thread in `onCreate`, which satisfies the main-thread constraint
- The goal is not to use them immediately (no VPN starts in `onCreate`) but to ensure both runtimes are initialized before any concurrent engine operations begin

The engines' own `loadOnce()` methods become no-ops after the eager load (the library is already loaded, `System.loadLibrary` for an already-loaded library is idempotent on Android).

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - The definitive fix (v0.0.12) that supersedes this approach; runs each Go runtime in its own OS process
- [[concepts/amneziawg-relinker-loading-trap]] - The AWG library loading mechanism; eager loading in App.onCreate bypasses the ReLinker issue
- [[concepts/urnetwork-sdk-integration]] - URnetwork is one of the two Go runtimes involved
- [[concepts/nubia-rom-permission-enforcement]] - The v1.0.1 SIGSEGV rule that this exception must not violate
- [[concepts/hilt-di-native-library-failure]] - Related native loading failure pattern; eager loading prevents DI-time failures

## Sources

- [[daily/2026-05-07.md]] - Session 15:11: engine switch SIGSEGV diagnosed as two Go runtimes conflicting on rapid stop/start; fix = eager-load libam-go.so + libgojni.so in OzeroApp.onCreate; both stay resident for app lifecycle
