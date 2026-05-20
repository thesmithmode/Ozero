---
title: "WARP UAPI Handshake Polling via LocalSocket"
aliases: [uapi-handshake, warp-handshake-uapi, local-socket-uapi]
tags: [warp, amneziawg, native, architecture]
sources:
  - "daily/2026-05-14.md"
  - "daily/2026-05-18.md"
  - "daily/2026-05-20.md"
created: 2026-05-14
updated: 2026-05-20
---

# WARP UAPI Handshake Polling via LocalSocket

`WarpHandshakeUapi.kt` reads `last_handshake_time_sec > 0` from the AmneziaWG UAPI socket at `$dataDir/sockets/<kernel-tun-name>.sock` using `android.net.LocalSocket`. This is the safe way to verify WireGuard handshake completion. The alternative — `awgGetConfig()` JNI call — causes SIGSEGV when called on a partial-handshake handle.

## Key Points

- UAPI socket path: amneziawg-go fork в `ipc/uapi_unix.go` делает `filepath.Join(rootdir, "sockets")` → реальный путь `$dataDir/sockets/<kernel-присвоенное-tun-name>.sock` (НЕ `$dataDir/<our-tunnel-name>.sock`). До v0.1.8 был баг: искали в корне → файла нет → awaitReady timeout → false-failed
- `findUapiSocket()` (v0.1.8+) cascade: 1) preferred `sockets/<tunnelName>.sock`, 2) первый `.sock` через `listFiles` в `sockets/`, 3) legacy `<uapiPath>/<tunnelName>.sock`
- WarpSocketDiagnostics листит ОБА `sockets/` И `wireguard/` (исторический путь некоторых fork-ов) для дифдиагностики
- Signal: `last_handshake_time_sec > 0` in the UAPI config dump = handshake completed
- Polling: every 100ms (reduced from 300ms in v0.1.8), timeout 10s via `withTimeoutOrNull` + `delay`
- `LocalSocket.soTimeout` = 50ms (reduced from 500ms): unix domain socket responds in <1ms; 500ms was a wrong default that added latency per poll cycle
- `awgGetConfig(handle)` JNI is unsafe during partial handshake — Go runtime may access incomplete state → SIGSEGV
- `LocalSocket` (android.net) communicates without JNI — no Go runtime involvement, no crash risk

## Details

### Why Not awgGetConfig JNI

`GoBackend.awgGetConfig(handle)` calls into the Go runtime via JNI to dump the WireGuard configuration including `last_handshake_time_sec`. However, during the 1-3 second window after `awgTurnOn` returns a valid handle but before the handshake completes, the Go-side tunnel state may be partially initialized. Calling `awgGetConfig` during this window can trigger a SIGSEGV in Go's memory access.

The UAPI socket is a Unix domain socket that the WireGuard userspace implementation exposes independently. Reading from it does not invoke the Go runtime — it's a standard socket read of a text protocol. The same `last_handshake_time_sec` field is available, without the crash risk.

### Protocol

The UAPI protocol is a simple text format. After connecting to the socket and sending `get=1\n`, the response contains key-value pairs:

```
private_key=...
listen_port=...
public_key=...
endpoint=...
last_handshake_time_sec=1715689234
last_handshake_time_nsec=0
tx_bytes=...
rx_bytes=...
```

`WarpHandshakeUapi` parses the response line-by-line, looking for `last_handshake_time_sec`. A value > 0 indicates at least one successful handshake with the peer.

### Integration with awaitReady

`WarpHandshakeUapi` is called from `EngineWarp.awaitReady()`:

```kotlin
withTimeoutOrNull(10_000) {
    while (true) {
        if (WarpHandshakeUapi.isHandshakeComplete(uapiSocketPath)) return@withTimeoutOrNull
        delay(300)
    }
}
```

If 10 seconds pass without handshake, the engine transitions to `Failed("No WG handshake after 10s")`.

### Timeout History (10s → 5s → 10s)

The current `WARP_READY_TIMEOUT_MS = 10s` is the post-revert value. In v0.1.5 the timeout was tightened to 5s on the theory that slower waits were masking honest failures. In v0.1.5-4 it was reverted to 10s because Cloudflare WARP handshake on slow/congested Russian ISP networks legitimately requires up to 10s — under 5s every WARP start fired `handleEngineFailure` even when the handshake would have completed shortly. The 10s value is therefore NOT "always correct" — it is calibrated against observed Cloudflare handshake distributions. See [[concepts/warp-false-connected-no-handshake]] for the full timeout-revert sequence and the false-Connected→honest-failure propagation it preserves.

### Legacy Socket Path Bug (v0.1.9, 2026-05-20)

`WarpUapi.readState` was reading from the legacy path `$dataDir/ozero-warp.sock` (or similar fixed filename). The amneziawg-go fork actually writes the UAPI socket to `$dataDir/sockets/<kernel-tun-name>.sock` — the kernel assigns the tun interface name, which may differ from our chosen tunnel name. With the legacy path, `LocalSocket.connect()` failed silently (file not found), `readState` returned null stats, and the health monitor classified the tunnel as DEGRADED. This triggered spurious recover cycles even when the WireGuard handshake had completed successfully.

Fix: `WarpHandshakeUapi.findUapiSocket()` with cascade discovery:
1. Try `sockets/<tunnelName>.sock` (preferred, matches kernel-assigned name when names align)
2. Try first `.sock` file found via `listFiles("sockets/")` (handles kernel-assigned names that differ)
3. Fall back to legacy `<uapiPath>/<tunnelName>.sock` for backward compatibility

`WarpSocketDiagnostics` was also added to enumerate and log both `sockets/` and `wireguard/` subdirectories for diagnostic purposes during tunnel startup.

### Detection Latency Optimization (v0.1.8, 2026-05-18 session 23:41)

The original polling parameters introduced an unnecessary detection lag:

| Parameter | Before | After | Reason |
|-----------|--------|-------|--------|
| `LocalSocket.soTimeout` | 500ms | 50ms | Unix domain socket = same-host IPC, responds in <1ms; 500ms was a wrong copy from TCP socket defaults |
| `WARP_READY_POLL_MS` | 300ms | 100ms | Analogous engines: URnetwork polls every 200ms, ByeDPI every 100ms; 300ms was conservative |

**Combined detection lag**: was max ~800ms (500ms socket read + 300ms wait), now max ~150ms (50ms socket read + 100ms wait). A handshake that completes immediately after `awgTurnOn` returns was previously delayed 800ms before the UI showed "Connected."

The `soTimeout` reduction is a correctness fix, not an optimization: a 500ms blocking read on a local unix socket violates the expected semantics. The poll interval reduction is an optimization calibrated by comparison with peer engine implementations.

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] - The problem this solves: awgTurnOn returns valid handle but no handshake = false-connected
- [[concepts/engine-await-ready-pattern]] - awaitReady() architecture that uses this polling as WARP's readiness signal
- [[concepts/amneziawg-turnon-minus-one]] - Complementary failure: awgTurnOn returns -1 (complete failure); this article covers handle OK but no handshake

## Sources

- [[daily/2026-05-14.md]] - Session 16:41: UAPI socket polling for last_handshake_time_sec, NOT awgGetConfig (SIGSEGV on partial-handshake); WarpHandshakeUapi.kt via LocalSocket; 300ms poll, 10s timeout
- [[daily/2026-05-14.md]] - Session 16:33: awgTurnOn ≠ handshake; TSPU blocks vanilla WG; polling pattern documented
- [[daily/2026-05-18.md]] - Session 23:41: `soTimeout` 500ms→50ms (correctness, unix socket <1ms), `WARP_READY_POLL_MS` 300ms→100ms (optimization, peer engine analogy); detection lag max 800ms→150ms
- [[daily/2026-05-20.md]] - WarpUapi.readState using legacy `ozero-warp.sock` path → null stats → false DEGRADED → spurious recovers; fix: findUapiSocket() cascade through sockets/ subdir + listFiles discovery; WarpSocketDiagnostics added
