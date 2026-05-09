---
title: "AmneziaWG awgTurnOn Returning -1 — Causes and Diagnosis"
aliases: [awg-turnon-minus-one, amneziawg-negative-handle, warp-tunnel-fail]
tags: [warp, amneziawg, native, jni, gotcha, debugging]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-05-06
---

# AmneziaWG awgTurnOn Returning -1 — Causes and Diagnosis

`GoBackend.awgTurnOn(name, fd, config, uapiPath)` returns a non-negative tunnel handle on success and -1 on failure. The -1 return is the only error signal — no exception, no log, no error code from the Go layer. Two root causes produce this return in Ozero's WARP engine: a blocking TUN file descriptor and a stale UNIX socket file from a prior crash.

## Key Points

- `awgTurnOn` signature (v2.3.7): `awgTurnOn(name: String, fd: Int, config: String, uapiPath: String): Int` — v1.2.2 had only 3 params (no `uapiPath`); mixing versions causes compile error
- **Cause 1**: TUN fd passed in blocking mode — amneziawg-go requires non-blocking fd; `setBlocking(true)` on the fd before passing → -1
- **Cause 2**: Stale UNIX socket at `uapiPath/ozero-warp.sock` from a prior crash — `bind()` returns `EADDRINUSE` inside Go → -1
- `TunSpec.blocking = true` (the Kotlin struct field) is correct for parity with official amneziawg-android; the fd itself must still be non-blocking at the OS level
- Without INI config logging, the -1 return cannot be diagnosed — always add `PersistentLoggers.info(TAG, "ini=\n${ini.replace(privateKey, "***")}")` before calling `awgTurnOn`
- PORTAL WG v1.4.3 reference: identical 4-param `awgTurnOn`, same wg-quick format, `uapiPath = context.dataDir.absolutePath`

## Details

### Cause 1: Blocking FD

The amneziawg-go layer (inside `libam-go.so`) uses Go's async I/O model. When the TUN file descriptor is in blocking mode (`O_NONBLOCK` not set), Go's runtime cannot multiplex I/O correctly — reads block the goroutine scheduler, preventing the tunnel from forwarding packets. The Go layer detects this incompatibility and returns -1 immediately.

The fix: call `Os.fcntl(fd, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)` on the raw TUN fd before passing it to `awgTurnOn`, or use `ParcelFileDescriptor.detachFd()` after ensuring the fd is non-blocking. The `TunSpec.blocking` field in Ozero's config struct controls whether the VPN Builder's `setBlocking()` is called; this is a separate concern from the fd's OS-level blocking mode.

### Cause 2: Stale UNIX Socket

`awgTurnOn` creates a UNIX domain socket at `<uapiPath>/ozero-warp.sock` for the WireGuard UAPI control interface. If the process previously crashed without calling `awgTurnOff`, the socket file remains on disk. On the next start, `bind()` against the same path returns `EADDRINUSE`, and the Go layer returns -1.

The fix: delete `<uapiPath>/ozero-warp.sock` before calling `awgTurnOn`:

```kotlin
File(uapiPath, "ozero-warp.sock").delete()
val handle = GoBackend.awgTurnOn(name, fd, ini, uapiPath)
```

This pattern is used by wireguard-android and PORTAL WG.

### Diagnosis Protocol

The -1 return provides zero diagnostic information on its own. Before any hypothesis, add comprehensive logging:

1. Log the INI config with PrivateKey masked
2. Log `fd > 0` (valid descriptor)
3. Log whether the socket file existed and was deleted
4. Log `uapiPath` and whether it's accessible

Without this data, version downgrade and other speculative fixes waste time. The v1.2.2 downgrade attempt failed because the 3-param API is incompatible with the 4-param call sites compiled for v2.3.7.

### AWG Params Reference

PORTAL WG v1.4.3 includes AWG obfuscation params S3(19), S4(20), I1(28), I2(29), I5(10) beyond the basic Jc/Jmin/Jmax/S1/S2/H1-H4 set. Absence of these additional params is unlikely to cause -1 (the Go layer uses zero/defaults), but parity with the reference may be needed for full obfuscation support.

## Related Concepts

- [[concepts/amneziawg-relinker-loading-trap]] - The prior awgTurnOn failure mode: library not loaded; this article covers the case where library loads but tunnel creation fails
- [[concepts/amnezia-wg-warp-migration]] - The WARP engine migration that introduced awgTurnOn into Ozero
- [[concepts/vpnservice-builder-traps]] - setBlocking and fd management traps in VpnService; related fd lifecycle issues

## Sources

- [[daily/2026-05-06.md]] - Session 09:30: awgTurnOn=-1 investigation; stale socket + blocking fd identified as root causes; v1.2.2 downgrade ruled out (3-param API); PORTAL WG v1.4.3 decompiled as reference oracle; Session 15:01: blocking=true TunSpec parity with official amneziawg-android confirmed
