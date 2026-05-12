---
title: "Go Runtime Process Isolation via android:process + AIDL"
aliases: [warp-process-isolation, engine-warp-aidl, go-runtime-separate-process]
tags: [native, jni, android, go, architecture, crash, warp]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# Go Runtime Process Isolation via android:process + AIDL

Ozero v0.0.12 introduced process isolation for the WARP engine by declaring `android:process=":engine_warp"` on `WarpEngineService` and communicating with it via AIDL IPC. This separates `libam-go.so` (AmneziaWG Go runtime) from `libgojni.so` (URnetwork Go runtime) into different OS processes, eliminating the concurrent Go GC signal handler conflict that caused SIGABRT in `runtime.gcWriteBarrier` during rapid engine switching.

## Key Points

- `android:process=":engine_warp"` in AndroidManifest.xml runs WarpEngineService in a separate Linux process — each process has its own Go runtime, GC, and signal handlers
- AIDL (`IWarpEngineProcess.aidl`) provides the IPC interface between the main app process and the WARP engine process
- `buildFeatures { aidl = true }` required in `engine-warp/build.gradle.kts` — without it, AIDL files are not compiled
- WarpEngineService runs without `AppLogger.attach` → `PersistentLoggers` unavailable in that process → added to `LoggingContractTest` whitelist
- This is the third and final approach to the dual Go runtime problem: eager loading (v0.0.5) → GoRuntimeGuard mutex (v0.0.8, removed v0.0.11) → process isolation (v0.0.12)

## Details

### Why Process Isolation

Two Go runtimes (`libam-go.so` for AmneziaWG and `libgojni.so` for URnetwork) in the same Linux process register competing signal handlers for Go's GC and goroutine scheduler. Rapid engine switching (WARP→URnetwork→WARP) caused one runtime's GC write barrier to encounter heap state modified by the other runtime, triggering SIGABRT in `runtime.gcWriteBarrier`. Prior approaches within a single process were insufficient:

1. **Eager loading** (both in `OzeroApp.onCreate`): prevented concurrent init/teardown SIGSEGV but not GC signal handler conflicts during active use of both runtimes in the same process
2. **GoRuntimeGuard** mutex: attempted to serialize access but created a deadlock when teardown coroutines were cancelled (see [[concepts/engine-switch-chain-cascading-failures]])

Process isolation is the definitive fix: each Go runtime runs in its own process with its own signal table and GC heap. No concurrent access, no shared state, no possibility of cross-runtime GC corruption.

### AIDL Interface

The AIDL interface `IWarpEngineProcess.aidl` defines the cross-process API for WARP engine control. The main process binds to `WarpEngineService` and calls methods over Binder IPC. This adds latency (~1-2ms per IPC call) compared to in-process JNI, but eliminates the SIGABRT crash class entirely.

Key implementation details:
- `RealWarpSdkBridge` constructor must be `public` (not `internal`) for cross-module instantiation in the service process
- AIDL requires explicit `buildFeatures.aidl = true` in the library module's `build.gradle.kts` — missing this causes silent compilation failure (AIDL files ignored, no generated stubs)
- `WarpEngineService.kt` was added to `LoggingContractTest` whitelist because it runs in a separate process where `AppLogger` is not attached and `PersistentLoggers` is not available

### Logging in Separate Process

The WARP engine process does not have access to the main process's `AppLogger` or `BootFileLogger` instances. `PersistentLoggers.warn/error` calls in `RemoteAwgRuntime.kt` were initially `Log.w` but caught by `LoggingContractTest` sentinel and converted to `PersistentLoggers.warn`. However, since the process lacks `AppLogger.attach`, these writes go to a separate log path or are lost. This is an accepted trade-off — critical pre-JNI checkpoints are preserved in the WARP process's own logcat, accessible via `adb logcat --pid=$(pidof ru.ozero:engine_warp)`.

### Asymmetric Bootstrap Guard

Process isolation requires that `OzeroApp.onCreate` loads each Go runtime only in the correct process. The initial implementation still loaded both libraries unconditionally (legacy from the single-process "coexist" approach), defeating the isolation. The fix uses `isEngineWarpProcess()` guard:

- `:engine_warp` process (`OzeroApp.onCreate` branch): `System.loadLibrary("am-go")` + `return` (no further init — the WARP process has no `AppLogger`, no UI, no URnetwork)
- Main process (`OzeroApp.onCreate` default): `System.loadLibrary("gojni")` only

This guard is protected by `OzeroAppProcessIsolationTest` which asserts the asymmetric loading invariant. Without the guard, the main process contained a resident `libam-go.so` — the exact cross-runtime GC conflict that process isolation was meant to eliminate. Six tombstones on Nubia/RedMagic confirmed the SIGABRT persisted until the bootstrap was split.

### Build Configuration

Two build changes were required:
1. `engine-warp/build.gradle.kts`: `buildFeatures { aidl = true }` to enable AIDL compilation
2. `RealWarpSdkBridge`: constructor changed from `internal` to `public` — `internal` blocks cross-module instantiation, and the service process needs to create the bridge instance

## Related Concepts

- [[concepts/dual-go-runtime-eager-loading]] - The prior approach (single-process eager loading) that process isolation supersedes
- [[concepts/engine-switch-chain-cascading-failures]] - GoRuntimeGuard deadlock that motivated the move to process isolation
- [[concepts/warp-handle-leak-sigabrt]] - Go GC SIGABRT from handle leaks; process isolation prevents cross-runtime GC corruption
- [[connections/go-runtime-conflict-resolution-evolution]] - The three-phase evolution from eager loading to process isolation

## Sources

- [[daily/2026-05-11.md]] - Session 19:49: process isolation via `android:process=":engine_warp"` + AIDL IPC; `buildFeatures.aidl=true` required; WarpEngineService added to LoggingContractTest whitelist; RemoteAwgRuntime `Log.w` → `PersistentLoggers.warn`
- [[daily/2026-05-11.md]] - Session 20:41: asymmetric per-process bootstrap guard — am-go only in :engine_warp (return after load), gojni only in main process; old unconditional dual-load was dead code preserving the conflict; 6 Nubia tombstones confirmed SIGABRT until bootstrap split; protected by OzeroAppProcessIsolationTest
