---
title: "WARP UAPI Socket: Stale File Regression and Cleanup Pattern"
aliases: [warp-stale-sock, uapi-stale-socket, warp-socket-cleanup]
tags: [warp, amneziawg, regression, filesystem, uapi]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-05-23
---

# WARP UAPI Socket: Stale File Regression and Cleanup Pattern

A regression introduced in commit `bd6a178a` (2026-05-20) caused WARP `awaitReady` to timeout on every attempt due to stale `.sock` files in `$dataDir/sockets/`. The amneziawg-go fork creates `tunN.sock` where N is the kernel-assigned TUN interface name (not the user-chosen tunnel name). Old socket files from previous sessions persist on disk after the process exits, because Unix domain socket files are not auto-deleted when the process dies.

## Key Points

- amneziawg-go fork socket path: `$dataDir/sockets/<kernel-tun-name>.sock` (e.g., `tun0.sock`, `tun5.sock`). Kernel assigns the name; it may differ from our chosen tunnel name `ozero-warp`
- `RealWarpSdkBridge.attachTun` pre-`bd6a178a` cleaned only `uapiPath/$tunnelName.sock` (legacy path) — `sockets/*.sock` were never cleaned
- Stale `tunN.sock` accumulate across sessions. On next start, Go creates `tunM.sock`, but `findUapiSocket` using `firstOrNull` (lexicographic order) returned the stale file → `LocalSocket.connect()` → ECONNREFUSED → handshake poll all fail → 10s timeout → `Failed`
- Diagnostic log signature: `[sockets/]={tun5.sock,tun0.sock}` — multiple `.sock` files, oldest is stale
- Fix: delete ALL `*.sock` in `sockets/` BEFORE `awgTurnOn`. Use `maxByOrNull { lastModified() }` instead of `firstOrNull` for resilience if cleanup races

## Details

### Root Cause Chain

`bd6a178a` added `findUapiSocket()` with cascade discovery: try `sockets/<tunnelName>.sock`, then `sockets/` listFiles, then legacy path. The discovery was correct, but `attachTun()` only cleaned the legacy path. After the Go process exited, the `tunN.sock` file remained at `$dataDir/sockets/tunN.sock`. On the next session, Go created a new socket at `$dataDir/sockets/tunM.sock` (different N). The `sockets/` directory now contained two files: old stale `tunN.sock` and new live `tunM.sock`. `firstOrNull` on `listFiles()` is lexicographic — on typical systems `tun0.sock` sorts before `tun5.sock`, so the stale file was returned when the new number was higher.

Unix domain socket files are filesystem objects — a `.sock` file does NOT disappear when the process that created it exits. Only explicit `unlink()` removes it. The Go runtime does not call `unlink()` on signal exit (SIGKILL path) or normal process death from Android process lifecycle.

### Fix (commit f458dd5d)

Two changes applied together:

1. **Pre-start cleanup** in `RealWarpSdkBridge.attachTun`: list `sockets/` directory, delete all files ending in `.sock` before calling `awgTurnOn`. This ensures Go starts with a clean directory and `findUapiSocket` finds only the freshly created file.

2. **Newest-file selection** in `WarpHandshakeUapi.findUapiSocket`: replace `firstOrNull()` (lexicographic) with `maxByOrNull { lastModified() }` (most recently created). This is a defense-in-depth change — even if cleanup fails (race condition, IO error), the newest file is the live one.

```kotlin
// Before (regression):
val socketFile = socketsDir.listFiles()?.firstOrNull { it.extension == "sock" }

// After (fix):
socketsDir.listFiles { f -> f.extension == "sock" }?.forEach { it.delete() }  // cleanup
// ... awgTurnOn ...
val socketFile = socketsDir.listFiles { f -> f.extension == "sock" }
    ?.maxByOrNull { it.lastModified() }  // newest = live
```

### General Pattern

Any time a native library (Go, C++) writes a Unix domain socket (`*.sock` file) to a directory:
- The file persists after process death
- Multiple files in the same directory after crashes = stale files
- Selection strategy must handle staleness: prefer newest, or clean before create

This applies equally to `wireguard-go` forks and any other Go/C native library that uses UAPI-style sockets.

## Related Concepts

- [[concepts/warp-uapi-handshake-polling]] — The polling mechanism that uses the socket; stale socket causes ECONNREFUSED during polls
- [[concepts/go-runtime-process-isolation]] — WARP process isolation; Go process death leaves socket files behind
- [[concepts/engine-await-ready-pattern]] — awaitReady() timeout that surfaced this regression
- [[concepts/warp-false-connected-no-handshake]] — False failure vs false connected; stale socket caused false failure (timeout = Failed, not connected)

## Sources

- [[daily/2026-05-23.md]] — Session 02:30: ozero.log showed 4 WARP awaitReady timeouts in sequence; root cause traced to `bd6a178a` socket path migration without matching cleanup; stale `tun5.sock` vs live `tun0.sock` confirmed in diagnostic log; fix: pre-start cleanup of all `*.sock` + `maxByOrNull{lastModified}` selection (commit `f458dd5d`); 5 unit + 2 source-sentinel tests
