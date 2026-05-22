---
title: "VpnService Double Shutdown Guard"
aliases: [double-shutdown, performShutdown-race, stopping-flag-reset]
tags: [vpnservice, concurrency, lifecycle, idempotency]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# VpnService Double Shutdown Guard

`OzeroVpnService.performShutdown()` resets the `stopping` flag in its `finally` block. This creates a window where `onDestroy()`, which uses a CAS on `stopping` to gate its own shutdown call, sees `stopping=false` and triggers a second `performShutdown()`. The fix is for `onDestroy()` to join the existing `shutdownJobRef` coroutine instead of spawning a new shutdown sequence.

## Key Points

- `stopping = true` is set at the start of `performShutdown()`; reset to `false` in `finally` block after cleanup
- `onDestroy()` CAS: `if (stopping.compareAndSet(false, true)) { launch performShutdown() }` — passes because `finally` already reset the flag
- Result: two concurrent `performShutdown()` calls — double resource cleanup, potential crash
- Fix: `onDestroy()` joins `shutdownJobRef` (existing coroutine) instead of CAS + new launch
- General pattern: guard flags controlling idempotency must not be reset if they are checked downstream

## Details

### Root Cause

`performShutdown()` was structured as:

```kotlin
suspend fun performShutdown() {
    stopping.set(true)
    try {
        // ... cleanup: stop engine, close TUN fd, etc.
    } finally {
        stopping.set(false)  // ← BUG: resets the guard
    }
}
```

`stopVpn()` sets `stopping=true` → `onRevoke()` or another lifecycle callback triggers `onDestroy()`. By the time `onDestroy()` executes, `performShutdown()` has completed its `finally` block and reset `stopping=false`. The CAS in `onDestroy()` then passes, spawning a second `performShutdown()`. This was observed in ozero.log `v0.1.13` as a double `performShutdown:` log line in rapid succession.

### The Fix

```kotlin
// onDestroy
override fun onDestroy() {
    super.onDestroy()
    shutdownJobRef.get()?.let { job ->
        serviceScope.launch { job.join() }  // wait for existing shutdown, don't start new
    } ?: run {
        // shutdownJobRef is null = shutdown never started; trigger it now
        serviceScope.launch { performShutdown() }
    }
}
```

`shutdownJobRef` holds the `Job` launched by `stopVpn()` or `onRevoke()`. `onDestroy()` joining it guarantees the cleanup runs exactly once regardless of call order.

### Idempotency Pattern

This is a general Android VpnService lifecycle pattern: the service receives multiple lifecycle callbacks (`onRevoke`, `stopSelf`, `onDestroy`, `onTaskRemoved`) that all could trigger shutdown. Using a `Job` reference as the idempotency primitive (join if exists, create if not) is more robust than an `AtomicBoolean` flag that gets reset.

Contrast with [[concepts/ontaskremoved-vpn-swipe-standard]]: `onTaskRemoved` should NOT call shutdown at all — it is a different lifecycle event (app swipe from recents) that must not terminate the VPN.

## Related Concepts

- [[concepts/engine-ownership-boundary]] — VpnService owns engine lifecycle; double shutdown violates ownership contract by triggering cleanup twice
- [[concepts/vpnservice-god-object-decomposition]] — Decomposed VpnService coordinators; shutdown coordinator is one of five extracted coordinators
- [[concepts/ontaskremoved-vpn-swipe-standard]] — Different lifecycle trap: onTaskRemoved must NOT call stopVpn at all
- [[concepts/go-runtime-process-isolation]] — WARP process has its own lifecycle; double shutdown in main process can misfire IPC teardown

## Sources

- [[daily/2026-05-22.md]] — Session 13:40+: ozero.log v0.1.13 double `performShutdown` trace; root cause = stopping reset in finally → onDestroy CAS passes; fix = onDestroy joins shutdownJobRef; pattern: guard flags must not be reset if used downstream for idempotency
