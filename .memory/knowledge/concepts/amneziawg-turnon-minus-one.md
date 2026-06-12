---
title: "AmneziaWG awgTurnOn Returning -1: Causes and Diagnosis"
aliases: [awg-turnon-minus-one, amneziawg-negative-handle, warp-tunnel-fail]
tags: [warp, amneziawg, native, jni, gotcha, debugging]
sources:
  - "daily/2026-05-06.md"
created: 2026-05-06
updated: 2026-06-12
---

# AmneziaWG awgTurnOn Returning -1: Causes and Diagnosis

`GoBackend.awgTurnOn(name, fd, config, uapiPath)` returns a non-negative tunnel handle on success and `-1` on failure. The `-1` return is the only error signal: no exception, no Go-layer error code, and often no native log. Ozero's WARP investigation showed that the same symptom can come from several unrelated causes, so the diagnostic path must capture config, fd, and UAPI socket evidence before changing versions or tunnel semantics.

## Key Points

- `awgTurnOn` signature in v2.3.7 is `awgTurnOn(name, fd, config, uapiPath)`; v1.2.2 has a 3-parameter API, so downgrade is invalid without matching call sites.
- The final fd contract for Ozero is `TunSpec.blocking = true`, matching official amneziawg-android parity; the non-blocking-fd hypothesis from the same session was an intermediate false lead.
- Stale UNIX sockets such as `uapiPath/ozero-warp.sock` can produce the same `-1` symptom through `EADDRINUSE` after a crash.
- Without masked INI config logging, fd validity, and uapiPath/socket checks, the `-1` return cannot be diagnosed safely.
- PORTAL WG v1.4.3 reference showed the same 4-parameter call shape, wg-quick config format, and dataDir-style uapiPath.

## Details

### FD Contract and False Hypothesis

The 2026-05-06 session contained two fd hypotheses. An intermediate fix attempted `setBlocking(false)` after interpreting `awgTurnOn=-1` as a non-blocking requirement. Later in the same release loop, the decision was corrected to `blocking = true` for parity with official amneziawg-android and PORTAL WG behavior. The durable rule is the final one: preserve the blocking fd contract and use reference parity before changing fd mode.

This matters because `awgTurnOn=-1` is too coarse to identify fd mode by itself. Treating an unproven fd-mode theory as root cause can introduce a new regression while leaving the real cause, such as stale socket state or invalid config, untouched. The fd article [[concepts/warp-awgturnon-blocking-fd]] is the owning reference for the final contract.

### Stale UNIX Socket

`awgTurnOn` creates a UNIX domain socket at `<uapiPath>/ozero-warp.sock` for the WireGuard UAPI control interface. If the process previously crashed without calling `awgTurnOff`, the socket file remains on disk. On the next start, `bind()` against the same path returns `EADDRINUSE`, and the Go layer returns `-1`.

The fix is to delete `<uapiPath>/ozero-warp.sock` before calling `awgTurnOn`:

```kotlin
File(uapiPath, "ozero-warp.sock").delete()
val handle = GoBackend.awgTurnOn(name, fd, ini, uapiPath)
```

This pattern is independent of the later `sockets/tunN.sock` cleanup. Both named UAPI sockets and generated per-interface sockets can become stale, and both belong in the WARP pre-start cleanup path. See [[concepts/warp-uapi-stale-socket-cleanup]].

### Diagnosis Protocol

The `-1` return provides zero diagnostic information on its own. Before any hypothesis, add comprehensive logging:

- Log the INI config with `PrivateKey` masked.
- Log `fd > 0` and whether the fd contract matches [[concepts/warp-awgturnon-blocking-fd]].
- Log whether the named socket file existed and was deleted.
- Log `uapiPath` and whether it is accessible.

Without this data, version downgrade and other speculative fixes waste time. The v1.2.2 downgrade attempt failed because the 3-parameter API is incompatible with the 4-parameter call sites compiled for v2.3.7. The artifact and version boundary is captured in [[concepts/amneziawg-artifact-identity-boundary]].

### AWG Params Reference

PORTAL WG v1.4.3 includes AWG obfuscation params S3(19), S4(20), I1(28), I2(29), I5(10) beyond the basic Jc/Jmin/Jmax/S1/S2/H1-H4 set. Absence of these additional params was marked as a parity gap, not a proven immediate `-1` cause.

## Related Concepts

- [[concepts/amneziawg-relinker-loading-trap]] - The prior awgTurnOn failure mode: library not loaded; this article covers the case where library loads but tunnel creation fails.
- [[concepts/amnezia-wg-warp-migration]] - The WARP engine migration that introduced awgTurnOn into Ozero.
- [[concepts/vpnservice-builder-traps]] - setBlocking and fd management traps in VpnService; related fd lifecycle issues.
- [[connections/warp-awgturnon-reference-diagnostic-loop]] - Connects masked config logging, fd contract, socket cleanup, and PORTAL WG parity into one diagnostic loop.

## Sources

- [[daily/2026-05-06.md]] - Sessions 09:30 and 10:18: `awgTurnOn=-1` had no useful native diagnostics without masked INI logging, fd validity, and uapiPath checks; v1.2.2 downgrade was rejected because its API had only 3 parameters.
- [[daily/2026-05-06.md]] - Session 13:16: stale `ozero-warp.sock` was recorded as an `EADDRINUSE` cause for the same `-1` symptom.
- [[daily/2026-05-06.md]] - Session 15:01: the final release-loop decision used `blocking=true` in `TunSpec` for official amneziawg-android parity.
