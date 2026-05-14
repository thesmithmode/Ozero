---
title: "ByeDPI Native Thread Join Race: proxyJob.cancel() vs C main()"
aliases: [byedpi-join-race, native-thread-race, g-proxy-running-race]
tags: [byedpi, native, jni, concurrency, gotcha]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# ByeDPI Native Thread Join Race: proxyJob.cancel() vs C main()

`proxyJob.cancel()` cancels the Kotlin coroutine wrapping ByeDPI's native `main()`, but the native C thread continues running inside JNI until `forceClose()` triggers exit. Without a second `join()` after `forceClose()`, the old native thread may still be alive when a new proxy starts, zeroing the `g_proxy_running` flag that the new instance set.

## Key Points

- `proxyJob.cancel()` cancels the Kotlin coroutine but does NOT stop the native C `main()` running in JNI — the native thread continues independently
- `forceClose()` signals the native code to exit, but exit is asynchronous — the thread needs time to clean up
- Without second `join()`: old thread exits, zeros `g_proxy_running` → new proxy's CAS 0→1 succeeded but flag gets cleared → `waitSocksReady` sees `!proxyJob.isActive` → `StartResult.Failure`
- Fix: `withTimeoutOrNull(STOP_GRACE_MS) { oldJob.join() }` after `forceClose()` — waits for native thread to fully exit before starting new proxy
- This is correct synchronization, not a workaround — the native thread lifecycle extends beyond the Kotlin coroutine lifecycle

## Details

### The Race Mechanism

ByeDPI engine restart sequence without the fix:

1. `proxyJob.cancel()` — Kotlin coroutine cancelled
2. `forceClose()` — signals native code to exit
3. New `proxyJob = launch { startProxy() }` — starts new native thread
4. New proxy sets `g_proxy_running = 1` via CAS(0, 1)
5. **Old native thread** finally exits and sets `g_proxy_running = 0`
6. New proxy's `waitSocksReady` checks `proxyJob.isActive` → sees active, but the SOCKS5 port may be in an inconsistent state

The fix adds step 2.5: `withTimeoutOrNull(STOP_GRACE_MS) { oldJob.join() }` — waits for the old coroutine (and its native thread) to fully complete before starting the new one.

### Why proxyJob.cancel() Is Insufficient

Kotlin's `cancel()` sets a cancellation flag and interrupts suspending functions. But `startProxy()` calls JNI `jniStartProxy()` which runs native C code in a blocking fashion. The native `main()` loop:

1. Does not check Kotlin cancellation state
2. Runs until `forceClose()` sets an internal C flag
3. Performs cleanup (socket close, memory free) before returning from JNI

The JNI call blocks the coroutine thread. `cancel()` sets the coroutine as cancelled, but the thread remains occupied until the native function returns. The coroutine completes only after the native `main()` returns and the JNI call unwinds.

### Relationship to Mock Pattern

In tests, `mock { every { startProxy } returns 0 }` completes the proxy job instantly, which creates a different problem: `waitSocksReady` sees `!proxyJob.isActive` immediately → early failure. The fix for tests is `answers { latch.await(); 0 }` to simulate blocking native behavior. See [[concepts/byedpi-mock-server-ci-fragility]].

## Related Concepts

- [[concepts/byedpi-mock-server-ci-fragility]] - Mock `answers { latch.await(); 0 }` pattern to simulate blocking native startProxy in tests
- [[concepts/engine-switch-chain-cascading-failures]] - Engine restart cascading failures; this race is one of the failure modes in rapid engine switching
- [[concepts/engine-ownership-boundary]] - VpnService sole engine lifecycle owner; related concurrent access problems

## Sources

- [[daily/2026-05-14.md]] - Session 16:20: proxyJob.cancel() ≠ native thread exit; second join after forceClose() prevents g_proxy_running zeroing by old thread; withTimeoutOrNull(STOP_GRACE_MS) pattern
- [[daily/2026-05-14.md]] - Session 16:41: confirmed as correct synchronization not workaround; old native thread lifecycle extends beyond Kotlin coroutine
