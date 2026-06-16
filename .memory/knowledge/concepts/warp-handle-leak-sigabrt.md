---
title: "WARP Handle Leak: Unpaired awgTurnOn Causes Go GC SIGABRT"
aliases: [warp-handle-leak, awgturnon-double-call, go-gc-corruption-sigabrt]
tags: [warp, amneziawg, native, crash, jni, debugging]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-06-12
---

# WARP Handle Leak: Unpaired awgTurnOn Causes Go GC SIGABRT

`GoBackend.awgTurnOn` returns a tunnel handle that must be closed with `awgTurnOff(handle)` before a new `awgTurnOn` is called. Calling `awgTurnOn` without closing the previous handle in the same process accumulates Go runtime resources; after 5-10 cycles the Go GC detects heap corruption and terminates the process with SIGABRT in `runtime.gcWriteBarrier`. The fix is an idempotency guard in `RealWarpSdkBridge.attachTun` that closes any stale handle before opening a new one.

## Key Points

- `awgTurnOn` without paired `awgTurnOff` in same PID leaks Go runtime state; SIGABRT follows after ~5-10 unpaired calls
- Crash signature: `runtime.abort` + `runtime.gcWriteBarrier*` in native backtrace — indicates Go GC heap corruption, not nullptr/segfault
- Root cause in Ozero: `attachTun` overwrote `tunnelHandle = handle` without closing previous → engine restart cycles accumulated leaks
- Fix: idempotency guard at bridge layer — if `tunnelHandle != INVALID_HANDLE` before new `awgTurnOn`, run `runCatching { awgTurnOff(tunnelHandle) }` first
- Diagnosis method: count entry/exit JNI calls in persistent log — entry > exit count reveals unpaired handles; crash always follows the pattern, never on first start
- Rejected hypotheses matter: raw INI passthrough, service shutdown order, and main-thread `loadOnce()` were checked and did not match the crash evidence

## Details

### The Leak Mechanism

`GoBackend.awgTurnOn` internally creates Go goroutines and allocates runtime structures for tunnel management. The returned integer handle is the only reference to this state. `awgTurnOff(handle)` tears down the goroutines and frees the memory. If `awgTurnOn` is called again with the same PID before `awgTurnOff`, the old goroutines remain running and their memory remains allocated but unreachable by the new tunnel context. The Go runtime's GC eventually encounters the corrupted heap state and aborts.

Analysis of 12732 lines of `ozero.log` revealed the pattern: 13 `awgTurnOn entry` log lines vs 11 `awgTurnOff exit` lines — 2 unpaired handles. The crash always occurred after an engine switch sequence (URnetwork→WARP→restart), never on the first start within a process. This is diagnostic: first-start cannot leak by definition (no prior handle), but any subsequent call to `attachTun` without cleanup does.

### SIGABRT Signature

```
Process: ru.ozero, PID: 32444
Signal: SIGABRT
backtrace:
  #00 runtime.abort
  #01 runtime.gcWriteBarrier1
  #02 <Go tunnel goroutine>
```

The `runtime.gcWriteBarrier*` frame indicates the crash happened during a GC write barrier check — a runtime assertion that detects invalid pointer writes, typically caused by writing to memory that has been freed or is in an inconsistent state. This is distinct from:
- `SIGSEGV` (null pointer / unmapped memory access)
- `SIGILL` (illegal instruction)
- Java-thrown exceptions

Any Go runtime SIGABRT with `gcWriteBarrier` in the backtrace should be investigated for resource leaks via handle/goroutine counting.

### Diagnosis Protocol: JNI Entry/Exit Counting

Native Go runtime state is invisible to Java-level instrumentation. The only reliable way to detect unpaired JNI calls is through persistent logging at the bridge layer:

```kotlin
fun attachTun(fd: Int): Int {
    PersistentLoggers.warn(TAG, "awgTurnOn entry fd=$fd")
    val handle = GoBackend.awgTurnOn(...)
    PersistentLoggers.warn(TAG, "awgTurnOn exit handle=$handle")
    return handle
}
```

After a crash, count `entry` vs `exit` occurrences in the log. `entry - exit > 0` = leaked handles = root cause confirmed.

### Idempotency Guard Fix

```kotlin
fun attachTun(fd: Int, config: String): Int {
    if (tunnelHandle != INVALID_HANDLE) {
        // Close stale handle before opening new one
        runCatching { GoBackend.awgTurnOff(tunnelHandle) }
            .onFailure { PersistentLoggers.warn(TAG, "stale turnOff failed", it) }
        tunnelHandle = INVALID_HANDLE
    }
    PersistentLoggers.warn(TAG, "awgTurnOn entry")
    tunnelHandle = GoBackend.awgTurnOn(name, fd, config, uapiPath)
    PersistentLoggers.warn(TAG, "awgTurnOn exit handle=$tunnelHandle")
    return tunnelHandle
}
```

The guard operates independently of whatever caused the double call (engine switch race, reconnect logic, stop/start overlap). Sentinel tests cover: double attach without detach (turnOff count = 1), stale turnOff throws (new turnOn still succeeds), 10-cycle stress test (turnOff count = turnOn - 1).

The same investigation explicitly rejected earlier root-cause theories. Raw INI parsing was not the active cause because the passthrough printed all expected keys including `I1`; service shutdown order was not the active cause because shutdown begin/end ordering was correct; `loadOnce()` thread affinity was not violated. This evidence narrowed the fix to bridge-layer idempotency instead of another higher-level lifecycle patch.

## Related Concepts

- [[concepts/amneziawg-turnon-minus-one]] - Earlier awgTurnOn failure modes (-1 return); this article covers the case where awgTurnOn succeeds but accumulates state
- [[concepts/amneziawg-so-binary-integrity]] - The SO migration that preceded this crash pattern being discovered
- [[concepts/dual-go-runtime-eager-loading]] - Related Go runtime lifecycle management; eager loading prevents a different class of crash
- [[connections/warp-portal-runtime-migration-proof-loop]] - Native runtime migration proof that preceded the handle-leak diagnosis

## Sources

- [[daily/2026-05-08.md]] - Session 14:00: rejected raw INI loss, stopSelf race, and `loadOnce()` thread-affinity hypotheses before fixing bridge-layer stale-handle cleanup.

- [[daily/2026-05-08.md]] - Session 14:00: 12732-line ozero.log analysis; 13 entry vs 11 exit awgTurnOn calls = 2 leaked handles; SIGABRT in gcWriteBarrier (PIDs 32444, 7659); fix = idempotency guard in attachTun committed f7cdcdc
