---
title: "WARP awgTurnOn: Non-Blocking FD Returns -1"
aliases: [awgturnon-blocking, warp-blocking-fd, amneziawg-fd-blocking]
tags: [warp, amneziawg, jni, gotcha, native]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-05-06
---

# WARP awgTurnOn: Non-Blocking FD Returns -1

The amneziawg-go native library's `awgTurnOn(name, fd, config, uapiPath)` requires the TUN file descriptor to be in **blocking mode**. Passing a non-blocking fd causes `awgTurnOn` to immediately return `-1` without any further diagnostics. This was the root cause of stable `-1` failures on real devices even when the native library loaded successfully and all other parameters were valid.

## Key Points

- `awgTurnOn` returns -1 when the fd is non-blocking — no error log, no exception, silent failure
- The fix: set `blocking = true` on the `TunSpec` or call `setBlocking(true)` on the fd before passing it to `awgTurnOn`
- This matches the behavior of the official `amneziawg-android` (PORTAL WG) which uses blocking fds exclusively
- Non-blocking mode may be inherited from `VpnService.Builder.establish()` under certain Android versions or ROM configurations
- Confirmed as the primary cause of stable `-1` failures on Nubia NX729J (SDK=35, arm64) where ReLinker and all other checks passed

## Details

### Discovery Process

The investigation into stable `awgTurnOn=-1` failures went through several false hypotheses before arriving at the blocking fd root cause:

1. **Native library load** — eliminated: `ReLinker loaded am-go` confirmed in logs
2. **uapiPath** — eliminated: standard `context.dataDir.absolutePath` is correct
3. **Race condition with onDestroy** — eliminated: second clean attempt also returned -1
4. **INI config format** — suspicious but inconclusive: adding INI logging revealed config was valid
5. **Library version downgrade** — rejected: v1.2.2 has 3-param signature, v2.3.7 has 4-param; downgrade caused compile fail
6. **Non-blocking fd** — confirmed: `setBlocking(true)` in `TunSpec` fixed the stable -1

The correct approach to diagnosing `awgTurnOn=-1` when library load succeeds:
1. Add INI config logging with `PrivateKey` masked
2. Verify `fd > 0` (fd validity)
3. Verify uapiPath directory exists
4. Check fd blocking mode — this is the most common silent failure

### Blocking Mode Parity with Reference Implementation

PORTAL WG v1.4.3 decompilation confirms their `GoBackend` always operates with blocking fds. Their `AbstractBackend$VpnService` never sets non-blocking mode on the TUN fd before passing to `awgTurnOn`. The Ozero implementation must maintain this parity.

```kotlin
// TunSpec — blocking must be true for amneziawg-go compatibility
TunSpec(
    fd = tunFd,
    blocking = true,   // REQUIRED: non-blocking → awgTurnOn=-1
    mtu = mtu,
)
```

### Relationship to Other -1 Causes

`awgTurnOn` returning -1 has at least two distinct root causes that produce identical symptoms:
1. Non-blocking fd (this article) — affects initial connection attempt
2. Stale socket file (`EADDRINUSE` on bind) — affects restart after crash

Both produce stable -1 across retries. Distinguishing them requires logging fd blocking state and checking for stale socket files before the call.

## Related Concepts

- [[concepts/warp-uapi-stale-socket-cleanup]] — Second cause of awgTurnOn=-1: stale socket causing EADDRINUSE on bind
- [[concepts/warp-false-connected-no-handshake]] — awgTurnOn=-1 means no tunnel created; failure is immediate, not deferred
- [[concepts/amneziawg-relinker-loading-trap]] — Loading the native lib is necessary but not sufficient; fd mode is also required
- [[concepts/warp-uapi-handshake-polling]] — Handshake polling only runs after awgTurnOn succeeds (handle ≥ 0)

## Sources

- [[daily/2026-05-06.md]] — Sessions 09:30, 10:18, 13:16, 15:01: stable awgTurnOn=-1 on Nubia NX729J; investigation eliminated race, library load, uapiPath; root cause confirmed as non-blocking fd; fix = `blocking=true` in TunSpec (parity with PORTAL WG v1.4.3 reference)
