---
title: "ByeDpiEngine Singleton Shared Between VPN and Strategy Testing"
aliases: [byedpi-singleton-shared, strategy-testing-isolation, engine-singleton-leak]
tags: [byedpi, architecture, testing, gotcha]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# ByeDpiEngine Singleton Shared Between VPN and Strategy Testing

`ByeDpiEngine` is `@Singleton` — the same instance serves both the production VPN tunnel (via `OzeroVpnService`) and the strategy testing UI (via `StrategyTestViewModel`). Native state (`proxyJobRef`, `server_fd`, `g_proxy_running`) leaks between use cases. When strategy testing leaves stale native state, the production VPN start fails with `jniStartProxy=-1`. The `proxyScope = Dispatchers.IO.limitedParallelism(1)` means a hung native `main()` call blocks ALL subsequent proxy operations across both use cases.

## Key Points

- `@Singleton ByeDpiEngine` shared between VPN service and strategy testing ViewModel — same `proxyJobRef`, same native globals
- Native `main()` not reentrant: static globals (`server_fd`, params, signal handlers) retain stale state between runs
- `proxyScope = limitedParallelism(1)` serializes ALL proxy operations — hung native call blocks both VPN and testing
- Strategy testing runs 600+ start/stop cycles → stale fd accumulation → production VPN start fails
- Emergency reset (`jniEmergencyReset`) triggers only on `GUARD_BUSY` but not on generic fail codes → recovery gap for non-guard failures

## Details

### The Sharing Problem

Hilt's `@Singleton` scope means one `ByeDpiEngine` instance exists per application process. Both consumers share:

1. **`proxyJobRef`**: Kotlin `AtomicReference<Job>` tracking the coroutine running native `main()`. Strategy testing's last `proxyJob` may still be completing when VPN tries to start.

2. **`server_fd`** (C global): The native socket file descriptor. Strategy testing's last stop may have called `jniStopProxy` (shutdown-only) without `jniForceClose` (close+reset). See [[concepts/byedpi-stale-serverfd-unconditional-forceclose]].

3. **`g_proxy_running`** (C atomic): The guard flag. Strategy testing's last run may have left it in `1` state if `main()` didn't return cleanly.

4. **`proxyScope`**: `Dispatchers.IO.limitedParallelism(1)` — a single-thread dispatcher. If strategy testing's `main()` hangs (28-second timeout observed in session 11:54), the VPN's `start()` call queues behind it on the same single thread.

### Log Evidence

Session 11:54 log analysis revealed:
- Strategy testing `main()` returned `-1` after 28 seconds (likely native cleanup timeout)
- VPN `start()` immediately after strategy test returned `-1` as well
- `proxyScope` single-thread queued all starts sequentially — no parallelism between use cases

### Fix Approaches

Four levels of isolation, in order of increasing correctness:

| Level | Approach | Scope |
|-------|----------|-------|
| F1 | Diagnostic logging in native-lib.c | Visibility only |
| F2 | Emergency reset on any non-zero non-GUARD_BUSY exit | Partial recovery |
| F3 | Separate `ByeDpiEngine` instance for strategy testing | Full Kotlin isolation |
| F4 | Subprocess for strategy testing (ProcessBuilder) | Full process isolation |

F1-F2 were implemented in the session. F3-F4 are deferred — F3 requires DI scope changes (Hilt assisted factory per use-case), F4 requires the same `extractNativeLibs` infrastructure as engine-telegram.

### Relationship to 0% Strategy Results

The user's bug report "подбор стратегий возвращает 0%" traces directly to this isolation gap. Strategy testing's 600 start/stop cycles accumulate stale `server_fd` values. After enough failures, every subsequent `jniStartProxy` returns -1 without even attempting to bind. All `EvalResult.startFailed=true` → fitness 0 for every chromosome → 0% across the board.

The immediate fix (unconditional `forceClose` in stop/failure paths) mitigates the stale fd. But the singleton sharing remains: a production VPN stop during strategy testing, or vice versa, can still interfere.

## Related Concepts

- [[concepts/byedpi-stale-serverfd-unconditional-forceclose]] - The stale fd mechanism that compounds the singleton sharing problem
- [[concepts/byedpi-jni-guard-hardening]] - Guard ownership and emergency reset; recovery gap for non-GUARD_BUSY failures
- [[concepts/genetic-strategy-evolution]] - Evolution engine that drives 600 start/stop cycles through the shared singleton
- [[concepts/engine-telegram-mtproxy]] - ProcessBuilder subprocess pattern (F4) — already used for MTProxy, could be applied to strategy testing

## Sources

- [[daily/2026-05-18.md]] - Session 11:54: ByeDpiEngine @Singleton shared between VPN and StrategyTestViewModel; native main() returns -1 after 28s; proxyScope single-thread queues all starts; F1-F4 fix levels proposed; F3/F4 deferred
