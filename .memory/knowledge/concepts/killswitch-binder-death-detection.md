---
title: "Killswitch Binder Death Detection for Process-Isolated Engines"
aliases: [binder-death-detection, linktodeath-warp, remote-process-crash-detection, onservicedisconnected-gap]
tags: [warp, android, aidl, killswitch, reliability, architecture]
sources:
  - "daily/2026-05-18.md"
created: 2026-05-18
updated: 2026-05-18
---

# Killswitch Binder Death Detection for Process-Isolated Engines

Process-isolated engines (WARP in `:engine_warp`) communicate via AIDL Binder. When the remote process crashes, the main process must detect the death and trigger killswitch/recovery. `RemoteAwgRuntime` now uses `IBinder.linkToDeath()` for explicit death detection, and `onServiceDisconnected()` for system OOM unbind — both call `onProcessDied()` which triggers `tunnelController.onEngineDied(EngineId.WARP, reason)`.

## Key Points

- `IBinder.linkToDeath(DeathRecipient)` fires `binderDied()` when remote process terminates unexpectedly — the primary crash detection mechanism
- `ServiceConnection.onServiceDisconnected()` fires when system unbinds the service (OOM kill, not app-initiated unbind) — a secondary detection path
- Both must call `onProcessDied()` because they cover different failure scenarios: `linkToDeath` = process crash, `onServiceDisconnected` = system resource pressure
- `onBindingDied()` ≠ `onServiceDisconnected()`: OOM unbind goes through `onServiceDisconnected`, NOT `onBindingDied`
- Code review (session 16:59) confirmed: `onServiceDisconnected` must also trigger `onProcessDied()` — symmetric with `onBindingDied`
- `WarpModule` wires the callback: `tunnelController.onEngineDied(EngineId.WARP, "remote-binder-died")`

## Details

### The Detection Gap (P31)

Before the fix, `RemoteAwgRuntime` only handled `onBindingDied()` for remote process death. System OOM kills go through a different callback — `onServiceDisconnected()` — which was implemented but did NOT call `onProcessDied()`. When Android killed the `:engine_warp` process due to memory pressure, the main process saw the service disconnection but did not trigger killswitch recovery. The VPN appeared connected in UI but WARP traffic stopped flowing.

### linkToDeath Pattern

```kotlin
override fun onServiceConnected(name: ComponentName, service: IBinder) {
    binder = IWarpEngineProcess.Stub.asInterface(service)
    service.linkToDeath({
        onProcessDied("binder-died")
    }, 0)
}
```

`linkToDeath` registers a `DeathRecipient` callback that fires when the Binder's hosting process dies. This is more reliable than `onServiceDisconnected` for crash detection because it fires immediately on process termination, without waiting for the Android service framework to process the disconnection.

### onServiceDisconnected Symmetry

```kotlin
override fun onServiceDisconnected(name: ComponentName) {
    binder = null
    protectedSockets = null
    onProcessDied("service-disconnected")
}
```

`onServiceDisconnected` fires when the system unbinds the service — typically during low-memory conditions where Android kills background processes. The callback must null all Binder references (to avoid dead object exceptions) AND trigger `onProcessDied()`. The code review finding (session 16:59) identified that the original implementation nulled references but did not trigger recovery.

### Killswitch Audit Context (P30–P37)

The binder death detection was part of a broader killswitch audit that identified 6 critical gaps:

| Finding | Component | Issue |
|---------|-----------|-------|
| P30 | TelegramProxyCoordinator | No auto-restart on subprocess crash |
| P31 | RemoteAwgRuntime | No `onProcessDied` in `onServiceDisconnected` |
| P32 | EngineUrnetwork | No IoLoopDoneCallback for SDK crash detection |
| P33 | Sentinel | killswitch=false → stopVpnRequest without lockdown |
| P34-P37 | Various | Missing sentinel tests |

P31 was fixed immediately. P32 used `compareAndSet(true, false)` to distinguish graceful stop from SDK crash — `onIoLoopDied` only fires when `wasRunning=true`.

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - WARP process isolation architecture that creates the need for Binder death detection
- [[concepts/engine-ownership-boundary]] - VpnService owns engine lifecycle; binder death triggers recovery through TunnelController
- [[concepts/combined-aidl-race-elimination]] - AIDL interface design for WARP; death detection is the reliability complement to race elimination
- [[concepts/engine-telegram-mtproxy]] - Telegram subprocess uses a different restart pattern (P30); side-car vs process-isolated have different failure modes

## Sources

- [[daily/2026-05-18.md]] - Session 14:21/14:45: P31 RemoteAwgRuntime linkToDeath + onServiceDisconnected fix; killswitch audit P30-P37; P32 IoLoopDoneCallback with compareAndSet
- [[daily/2026-05-18.md]] - Session 16:59: code review confirmed onServiceDisconnected must also call onProcessDied() — OOM unbind ≠ onBindingDied
- [[daily/2026-05-18.md]] - Session 19:55: P1 fix committed — RemoteAwgRuntime.onServiceDisconnected symmetric with onBindingDied
