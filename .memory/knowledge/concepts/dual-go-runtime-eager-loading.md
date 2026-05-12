---
title: "Dual Go Runtime Eager Loading to Prevent SIGSEGV"
aliases: [dual-go-runtime, am-go-ur-go-conflict, go-runtime-eager-load]
tags: [native, jni, android, go, crash, amneziawg, urnetwork]
sources:
  - "daily/2026-05-07.md"
  - "daily/2026-05-09.md"
  - "daily/2026-05-11.md"
created: 2026-05-07
updated: 2026-05-11
---

# Dual Go Runtime Eager Loading to Prevent SIGSEGV

Ozero loads two Go-based native libraries in the same process: `libam-go.so` (AmneziaWG) and `libgojni.so` / `libur-go.so` (URnetwork). Rapid stop/start of engines (e.g., switching from WARP/AWG to URnetwork) causes SIGSEGV when one Go runtime is torn down while the other is initializing. The fix is to eager-load both libraries in `OzeroApp.onCreate`, allowing them to coexist for the full application lifecycle rather than loading/unloading on engine start/stop.

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

### GoRuntimeGuard: A Wrong Intermediate Step (v0.0.8–v0.0.11)

After eager loading was implemented, a `GoRuntimeGuard` mutex was added (commit `633304f`) as "defense in depth" to serialize Go JNI access. This guard required `acquire(owner)` before Go JNI calls and `release(owner)` on teardown. However, the guard was fundamentally flawed:

1. **Unnecessary**: Eager loading already solved the concurrent init/teardown problem the guard targeted. Both runtimes were loaded and resident — there was no concurrent `JNI_OnLoad` scenario to guard against.
2. **Introduced deadlock**: During rapid engine switching, the teardown coroutine (which calls `release(owner)`) was cancelled by the new `startVpn()`. The guard's owner remained set to the old engine, blocking the new engine's `acquire()` indefinitely.
3. **Symptom "URnetwork broken after switching" + "WARP won't start" = one root cause**: leaked guard owner from cancelled teardown.

GoRuntimeGuard was removed in v0.0.11. The lesson: adding synchronization to a problem already solved by eager loading creates complexity without value and introduces a new failure mode (deadlock) worse than the original (SIGSEGV, which was already fixed).

### Superseded by Process Isolation (v0.0.12)

Eager loading prevented concurrent init/teardown SIGSEGV but could not prevent Go GC signal handler conflicts during active concurrent use of both runtimes (e.g., UI polling URnetwork bridge while WARP Go runtime is active). In v0.0.12, WARP was moved to a separate process (`android:process=":engine_warp"` + AIDL IPC), making each Go runtime run in its own process with independent GC and signal handlers. See [[concepts/go-runtime-process-isolation]].

### Asymmetric Per-Process Bootstrap (v0.0.12 Fix)

After initial process isolation deployment, `OzeroApp.onCreate` was still loading `libam-go.so` unconditionally in the main process — dead code from the old "coexist" approach that process isolation was supposed to replace. This meant the main process still had `libam-go.so` resident alongside `libgojni.so`, defeating the purpose of process isolation. The fix is a symmetric per-process guard via `isEngineWarpProcess()`:

- **`:engine_warp` process**: loads only `libam-go.so` (with `return` after load — no further initialization)
- **Main process**: loads only `libgojni.so`
- **Coexistence of two Go runtimes in one process is forbidden** — Go GC signal handler conflict → SIGABRT (confirmed by 6 tombstones on Nubia/RedMagic in v0.0.12)

Protected by `OzeroAppProcessIsolationTest`. The lesson: layering process isolation on top of old "coexist" logic without splitting the bootstrap creates dead code that preserves the exact conflict the isolation was meant to eliminate.

## Related Concepts

- [[concepts/amneziawg-relinker-loading-trap]] - The AWG library loading mechanism; eager loading in App.onCreate bypasses the ReLinker issue
- [[concepts/urnetwork-sdk-integration]] - URnetwork is one of the two Go runtimes involved
- [[concepts/nubia-rom-permission-enforcement]] - The v1.0.1 SIGSEGV rule that this exception must not violate
- [[concepts/hilt-di-native-library-failure]] - Related native loading failure pattern; eager loading prevents DI-time failures
- [[concepts/go-runtime-process-isolation]] - Process isolation supersedes single-process eager loading for cross-runtime GC conflicts
- [[connections/go-runtime-conflict-resolution-evolution]] - The three-phase evolution from eager loading to process isolation

## Sources

- [[daily/2026-05-07.md]] - Session 15:11: engine switch SIGSEGV diagnosed as two Go runtimes conflicting on rapid stop/start; fix = eager-load libam-go.so + libgojni.so in OzeroApp.onCreate; both stay resident for app lifecycle
- [[daily/2026-05-09.md]] - GoRuntimeGuard contradiction documented; guard creates deadlock on rapid engine switching
- [[daily/2026-05-11.md]] - Session 11:01: GoRuntimeGuard removed entirely — eager loading already solves the problem; Session 19:49: process isolation supersedes single-process approach; Session 20:41: asymmetric per-process guard — am-go only in :engine_warp, gojni only in main; old unconditional dual-load in onCreate was dead code after process isolation
