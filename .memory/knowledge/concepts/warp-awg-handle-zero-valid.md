---
title: "AWG handle=0 Is a Valid Tunnel Slot"
aliases: [awg-handle-zero, tunnelhandles-map, warp-handle-guard]
tags: [warp, amneziawg, gotcha, jni, native]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-20
---

# AWG handle=0 Is a Valid Tunnel Slot

`GoBackend.awgTurnOn` returns `0` when it successfully creates the *first* tunnel — not as an error code. The Go bridge iterates `tunnelHandles` map starting from index 0 and returns the first unused key. Using `handle <= 0` as the error guard is incorrect: it rejects every clean first start. The correct guard is `handle < 0`; only negative handles signal failure.

## Key Points

- Go bridge code (`.claude/Контекст/amnezia-client/client/macos/gobridge/api.go:123-135`): `for i = 0; i < math.MaxInt32; i++ { if _, exists := tunnelHandles[i]; !exists { break } }` → returns `i`, starting from 0
- Error paths in the same function return `return -1` (lines 101/107/117/132) — always negative
- `handle = 0` is the valid first slot; `handle < 0` is the correct error guard; `handle <= 0` is wrong
- Ozero v0.1.5.1 introduced `handle <= 0` as a "fix" for WARP not connecting — this was a misdiagnosis
- The real root cause of WARP not connecting was always missing WireGuard handshake (TSPU blocking, endpoint unreachable), not a bad handle value
- v0.1.5-4 reverted `handle <= 0` → `handle < 0` in `RealWarpSdkBridge.attachTun` and all related paths

## Details

### The Misdiagnosis Chain

Log analysis in session v0.1.5 showed `awgTurnOn JNI exit handle=0 v4=183 v6=184 dt=4ms`. The analyst incorrectly concluded that `handle=0` was invalid: "handle=0 — не valid AWG handle, проходил guard `if (handle < 0)` потому что 0 не < 0 → тихо считался запущенным."

This led to commit 3a2ba785 (v0.1.5.1) changing the guard to `handle <= 0`. The immediate result: **every clean WARP start returned handle=0 → immediately treated as failure → WARP completely broken** for all users. The false-connected problem was not caused by the handle value — it was caused by `awaitReady()` not receiving a handshake confirmation within the timeout.

### Primary Source Verification

The error was discovered by reading the actual Go bridge source at `.claude/Контекст/amnezia-client/client/macos/gobridge/api.go`:

```go
// lines 123-135
for i = 0; i < math.MaxInt32; i++ {
    if _, exists := tunnelHandles[i]; !exists {
        break
    }
}
handle = i
tunnelHandles[i] = tunnel
// ...
return i  // returns 0 on first call (empty map)
```

Error returns:
```go
if err := device.Up(); err != nil {
    return -1  // line 101
}
if err := uapiConf.IpcSet(interfaceSettings); err != nil {
    return -1  // line 107
}
// ...
return -1  // line 117/132 for other errors
```

The convention is unambiguous: `-1` = error, `0+` = valid handle.

### Affected Code Paths

All three paths were corrected in v0.1.5-4 revert:
1. `RealWarpSdkBridge.attachTun`: `handle <= 0` → `handle < 0`
2. `RealWarpSdkBridge.AwgRuntime.turnOnAndGetSockets` (default impl): same
3. `WarpEngineService.turnOnAndGetSockets` (AIDL binder): same

Sentinel in `RealWarpSdkBridgeTest`:
- Before v0.1.5.1: `handle=0 → Success` (correctly treated as valid)
- During v0.1.5.1: `handle=0 → Failed` (wrong — broke WARP)
- After v0.1.5-4 revert: `handle=0 → Success` restored; `handle=-2 → Failed` (negative = error) preserved

### Correct False-Connected Fix

The actual false-connected behavior (UI shows "Connected" with 0 b/s traffic) is handled by `awaitReady()` timeout → `handleEngineFailure`, not by rejecting `handle=0`. See [[concepts/warp-false-connected-no-handshake]] for the correct fix.

## Related Concepts

- [[concepts/warp-false-connected-no-handshake]] — The actual false-connected problem: awgTurnOn OK but handshake never comes; awaitReady() timeout is the correct failure signal
- [[concepts/warp-uapi-handshake-polling]] — UAPI polling implementation that determines if handshake actually completed
- [[concepts/amneziawg-turnon-minus-one]] — `awgTurnOn` returning truly negative handle (e.g. -1) = actual failure; complementary case

## Sources

- [[daily/2026-05-19.md]] — v0.1.5-4 session: handle=0 misdiagnosis traced through api.go primary source; `handle <= 0` guard reverted to `handle < 0` in RealWarpSdkBridge + WarpEngineService; sentinel inverted back to handle=0→Success; negative handle test preserved; Opus advisor confirmed primary-source reading
