---
title: "WARP UAPI Handshake Polling via LocalSocket"
aliases: [uapi-handshake, warp-handshake-uapi, local-socket-uapi]
tags: [warp, amneziawg, native, architecture]
sources:
  - "daily/2026-05-14.md"
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
- Polling: every 300ms, timeout 10s via `withTimeoutOrNull` + `delay`
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

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] - The problem this solves: awgTurnOn returns valid handle but no handshake = false-connected
- [[concepts/engine-await-ready-pattern]] - awaitReady() architecture that uses this polling as WARP's readiness signal
- [[concepts/amneziawg-turnon-minus-one]] - Complementary failure: awgTurnOn returns -1 (complete failure); this article covers handle OK but no handshake

## Sources

- [[daily/2026-05-14.md]] - Session 16:41: UAPI socket polling for last_handshake_time_sec, NOT awgGetConfig (SIGSEGV on partial-handshake); WarpHandshakeUapi.kt via LocalSocket; 300ms poll, 10s timeout
- [[daily/2026-05-14.md]] - Session 16:33: awgTurnOn ≠ handshake; TSPU blocks vanilla WG; polling pattern documented
