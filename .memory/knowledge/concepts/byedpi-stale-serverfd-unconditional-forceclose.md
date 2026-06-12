---
title: "ByeDPI Stale server_fd: Unconditional forceClose in Stop and Failure Paths"
aliases: [byedpi-stale-fd, forceclose-always, byedpi-cascade-minus-one, server-fd-shutdown-only]
tags: [byedpi, native, jni, gotcha, architecture]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-06-12
---

# ByeDPI Stale server_fd: Unconditional forceClose in Stop and Failure Paths

`jniStopProxy` performs only `shutdown(server_fd, SHUT_RDWR)` without `close()`/`reset`. Upstream byedpi `main()` stores `server_fd` as a global variable and on next start sees the stale fd, causing `bind()` to return -1. `ByeDpiEngine.stop()` and `start()` failure path must **always** call `proxy.forceClose()` after `job.join()`, unconditionally — not gated on `proxyJob.isActive`. Without this, every failed start leaves a stale fd that blocks all subsequent starts until a random `jniForceClose` clears it.

## Key Points

- `jniStopProxy` = `shutdown(server_fd, SHUT_RDWR)` only — no `close()`, no fd reset, no `g_proxy_running` release
- Upstream byedpi `main()` stores `server_fd` globally; stale fd from prior run → next `bind()` returns -1 → `jniStartProxy` returns -1
- Production evidence: 10+ consecutive `jniStartProxy завершился с кодом -1` in ozero.log (2026-05-15 01:33–01:40), single `jniForceClose` (line 15254) recovered connection 4 min later
- `EvolutionEngine` 0% fitness = same root cause: 600 start/stop cycles accumulate stale fds → all `EvalResult.startFailed=true` → fitness 0
- Fix: remove `if (proxyJob.isActive)` guard — always `join + forceClose` in both `stop()` and `start()` failure path
- Later review found the same stale-fd class in `start()` pre-flight cleanup: a prior job that already returned `-1` still needs unconditional `forceClose` after `join`
- Existing test `startFailureNoStopProxyWhenProxyReturnedErrorImmediately` was inverted — it proved the bug as "expected behavior"

## Details

### The Stale fd Mechanism

ByeDPI's C layer (`main.c`) creates a server socket and stores its file descriptor in a global `server_fd`. The `jniStopProxy` JNI function calls `shutdown(server_fd, SHUT_RDWR)` which signals the socket to stop accepting connections, but does NOT call `close(server_fd)` or reset the variable. The Go routine or C `main()` eventually exits, but the fd number remains in the global.

On the next `jniStartProxy`, `main()` attempts to `bind()` a new socket on the same port. If the old `server_fd` was never closed via `jniForceClose` (which calls `close()` + resets the global), the OS may still hold the fd reference. The `bind()` fails with EADDRINUSE or the old fd is reused incorrectly, producing a return code of -1.

`jniForceClose` is the only function that actually calls `close(server_fd)` and resets the global state. It must be called after every engine stop or start failure, regardless of whether the proxy coroutine is still active.

### Production Evidence

Analysis of `ozero.log` from 2026-05-15 session revealed the pattern clearly:

1. Lines 15240–15253: 10 consecutive `jniStartProxy завершился с кодом -1` over ~7 minutes
2. Line 15254: Single `proxyJob не завершился за 1500ms — jniForceClose` (timeout-triggered cleanup)
3. Lines 15255+: Successful `jniStartProxy` → connected state restored

The sole recovery event was an accidental `jniForceClose` triggered by the 1500ms watchdog timeout. Without this timeout, the engine would remain permanently dead — every start attempt returns -1 because the stale fd is never cleared.

### Impact on Evolution Engine

`EvolutionEngine` runs 600+ start/stop cycles testing strategy chromosomes. Each cycle:
1. `ByeDpiEngine.start(strategyArgs)` → `jniStartProxy(args)`
2. Wait for SOCKS5 ready → probe target sites
3. `ByeDpiEngine.stop()` → `jniStopProxy`

Without unconditional `forceClose`, stale fds accumulate. After N failed starts (where `main()` returned -1 before binding), subsequent starts all fail. The evolution results in 100% `startFailed=true` → fitness 0 for every chromosome → GA produces meaningless 0% results. The user reported this as "подбор стратегий возвращает 0%."

### The Inverted Test

`startFailureNoStopProxyWhenProxyReturnedErrorImmediately` asserted that `forceClose` is NOT called when `startProxy` returns an error immediately. This test was semantically wrong — it verified the buggy behavior as correct. The fix inverted the test to assert that `forceClose` IS called after any start failure, ensuring stale fd cleanup regardless of how quickly the proxy failed.

This is an instance of [[concepts/sentinel-protecting-bug-trap]]: a test guarding buggy behavior blocks the correct fix.

### forceClose Safety

`jniForceClose` is safe to call multiple times — when `server_fd < 0` (already closed), it's a no-op. This means unconditional calls in both `stop()` and `start()` failure paths don't create double-close issues.

### Pre-Flight Cleanup Uses the Same Rule

The same pattern later appeared in `ByeDpiEngine.start()` pre-flight. If an old proxy job had already returned `-1` during `withTimeoutOrNull`, `oldJob.isActive` was false, so the pre-flight branch skipped `forceClose`. That left `server_fd` stale before launching the next attempt. The review fix applied the same invariant everywhere: after joining a prior proxy job, call `forceClose` unconditionally.

## Related Concepts

- [[concepts/byedpi-jni-guard-hardening]] - Guard ownership prevents `jniForceClose` from releasing `g_proxy_running`; this article covers a different fd lifecycle issue
- [[concepts/byedpi-native-thread-join-race]] - proxyJob.cancel() ≠ native thread exit; second join after forceClose; complementary to this article's focus on stale fd
- [[concepts/sentinel-protecting-bug-trap]] - Inverted test guarding bug; same pattern discovered here
- [[concepts/genetic-strategy-evolution]] - 0% fitness from stale fd accumulation across 600 start/stop cycles

## Sources

- [[daily/2026-05-18.md]] - Session 12:06: 10 consecutive -1 fails in prod log, single jniForceClose recovery, 0% evolution fitness traced to same root cause; conditional `if (proxyJob.isActive)` removed; test inverted; committed as fix
- [[daily/2026-05-18.md]] - Sessions 16:59 and 19:55: code review found the same `isActive` gate in start pre-flight; fix made pre-flight `forceClose` unconditional after join
