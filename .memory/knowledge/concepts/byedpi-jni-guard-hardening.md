---
title: "ByeDPI JNI Guard Hardening: Ownership, Return Codes, Emergency Reset"
aliases: [byedpi-guard-hardening, jni-guard-busy, g-proxy-running-hardening, emergency-reset]
tags: [byedpi, native, jni, concurrency, architecture]
sources:
  - "daily/2026-05-15.md"
created: 2026-05-15
updated: 2026-05-15
---

# ByeDPI JNI Guard Hardening: Ownership, Return Codes, Emergency Reset

The `g_proxy_running` atomic guard in `native-lib.c` controls exclusive access to the ByeDPI proxy process. A series of hardening fixes (T-19 through T-28) established three invariants: (1) only `jniStartProxy`'s `main()` exit path releases the guard, (2) `jniForceClose` never touches the guard, and (3) a distinct return code `JNI_GUARD_BUSY = -2` separates guard-busy from real proxy failure (`-1`). When the guard becomes wedged (forceClose succeeded but main() never exited), `jniEmergencyReset` + `startProxyWithRecovery()` provides a last-resort escape hatch.

## Key Points

- `g_proxy_running` guard: exclusively released by `jniStartProxy` → `main()` exit path. `jniForceClose` does NOT release — prevents premature release race where new `main()` starts before old `main()` finishes cleanup
- `JNI_GUARD_BUSY = -2` (new): returned when CAS(0,1) fails. Distinct from `-1` (real proxy failure). Kotlin can now distinguish "another instance running" from "proxy binary crashed"
- `jniEmergencyReset`: `atomic_exchange(&g_proxy_running, 0)` — unconditional reset, last resort when guard is wedged after confirmed cleanup. Trade-off: creates a race window, but vs permanent dead engine = acceptable
- `Dispatchers.IO.limitedParallelism(1)` replaces `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` — zero cleanup overhead, no Executor leak in tests
- `argv[0] = strdup("byedpi")` now has NULL-check for OOM — previously would SIGSEGV on allocation failure
- JNI `ExceptionCheck` added after `GetObjectArrayElement` / `GetStringUTFChars` — pre-existing gaps, not regression

## Details

### Guard Ownership Principle (T-21)

Before T-21, `jniForceClose` released `g_proxy_running` (set to 0) as part of cleanup. This created a race:

1. Engine A: `jniStartProxy` running, `g_proxy_running = 1`
2. Engine A: `jniForceClose` called → sets `g_proxy_running = 0`
3. Engine B: `jniStartProxy` CAS(0,1) succeeds → `g_proxy_running = 1`
4. Engine A: `main()` finally exits → sets `g_proxy_running = 0` (premature release!)
5. Engine B: running but guard is 0 → next CAS sees 0, allows concurrent `main()`

The fix: `jniForceClose` sends the shutdown signal (`shutdown(server_fd, SHUT_RDWR)`) but does NOT touch `g_proxy_running`. Only the natural exit path of `main()` inside `jniStartProxy` releases the guard. This serializes access correctly even under rapid engine switching.

Upstream ByeByeDPI 1.7.4 has the same race bug — the `Stop` and `ForceClose` JNI methods both reset the guard. Ozero's fix is objectively better than upstream.

### Return Code Distinction (T-23 rework → ba2f74b5)

Original `jniStartProxy` returned `-1` for both "guard busy" and "real failure." Kotlin-side code could not distinguish between:
- Another proxy instance actively running (guard busy — retry may succeed)
- Native proxy binary failed to start (real failure — retry is futile)

Fix: `return -2` when CAS(0,1) fails. Kotlin maps this to `JNI_GUARD_BUSY` constant and can implement appropriate retry logic vs immediate failure reporting.

### C-Side Retry Removal (ba2f74b5)

An initial T-23 implementation added a 100×10ms spin retry loop in C when CAS fails. Code review found this was a P0 regression: the retry blocked the single-thread `proxyDispatcher` for up to 1 second, serializing all subsequent `start()` calls. The retry was moved to Kotlin-side `startProxyWithRecovery()` where it can be cancelled and doesn't block the dispatcher.

### Emergency Reset (T-28, 9de61de3)

With `jniForceClose` no longer releasing the guard, a new failure mode emerged: if `forceClose` succeeds (native `main()` receives shutdown signal) but `main()` never exits (hangs in cleanup), the guard remains held forever — engine permanently dead.

`jniEmergencyReset` is the escape hatch:

```c
JNIEXPORT void JNICALL jniEmergencyReset(JNIEnv *env, jobject obj) {
    atomic_exchange(&g_proxy_running, 0);
}
```

Kotlin-side `startProxyWithRecovery()` calls it only when:
1. `startProxy()` returns `JNI_GUARD_BUSY` (-2)
2. The engine has already completed full cleanup (stop + forceClose + join)
3. Retry after cleanup still returns -2

This is a trade-off: `emergencyReset` creates a momentary race window (guard=0 while old `main()` might still be running), but the alternative is permanent engine death with no recovery path. The user explicitly approved this trade-off.

### limitedParallelism(1) vs newSingleThreadExecutor

`Executors.newSingleThreadExecutor().asCoroutineDispatcher()` creates a dedicated thread that must be `close()`d explicitly — failure to close leaks the thread. In tests, `@AfterEach` must call `dispatcher.close()`. Missing this causes thread accumulation across test cases (see [[concepts/test-io-thread-zombie-trap]]).

`Dispatchers.IO.limitedParallelism(1)` borrows a thread from the IO pool with parallelism=1 guarantee. No dedicated thread, no close needed, no leak risk. The behavioral guarantee (at most 1 coroutine executing) is identical. This is strictly better for the ByeDPI proxy dispatcher use case.

### JNI Hardening (T-19, ba2f74b5)

Two pre-existing gaps in `native-lib.c` were fixed alongside the guard changes:

1. **argv loop NULL-check**: `strdup("byedpi")` for `argv[0]` can return NULL on OOM. Without check → NULL dereference in `getopt_long`. Fix: check return value, abort gracefully.

2. **JNI ExceptionCheck**: `GetObjectArrayElement` and `GetStringUTFChars` can throw Java exceptions (e.g., `ArrayIndexOutOfBoundsException`, OOM). Without `ExceptionCheck` after these calls, the native code continues with invalid pointers. Fix: check for pending exception after each JNI call, return early if set.

### Sentinel Regex Trap

Sentinel test regex `[^}]*` was used to match multi-line patterns in native-lib.c. This regex does NOT cross inner `{...}` blocks — an `if` statement with braces inside the target function causes the regex to stop at the inner closing brace, producing a false negative. Fix: use `[\s\S]*?` for multi-line patterns or explicit anchor patterns that don't depend on brace counting.

## Related Concepts

- [[concepts/byedpi-native-thread-join-race]] - The Kotlin-side of the same race; proxyJob.cancel() vs native thread exit. This article covers the C-side guard ownership
- [[concepts/byedpi-mock-server-ci-fragility]] - Mock patterns that must match the real JNI blocking behavior; `answers { latch.await() }` simulates the guard-held state
- [[concepts/engine-switch-chain-cascading-failures]] - Rapid engine switching that triggers the guard contention; emergency reset is the recovery for wedge state
- [[concepts/test-io-thread-zombie-trap]] - Thread leak from `Executors.newSingleThreadExecutor` that `limitedParallelism(1)` eliminates

## Sources

- [[daily/2026-05-15.md]] - Session 13:30: T-19 argv NULL-check, T-21 jniForceClose guard ownership, T-20 server_fd race documented (upstream fork required); Session 15:30: code reviewer found P0-1 spin blocking, P0-2 indistinguishable return codes, P1-1 wedge; ba2f74b5 rework removed C retry, added JNI_GUARD_BUSY=-2, limitedParallelism(1); 9de61de3 added jniEmergencyReset + startProxyWithRecovery
