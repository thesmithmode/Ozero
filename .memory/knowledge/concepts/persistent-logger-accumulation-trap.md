---
title: "PersistentLoggers Accumulation Trap: boot.log Bloat"
aliases: [boot-log-bloat, persistent-logger-loop, log-accumulation]
tags: [android, logging, gotcha, performance]
sources:
  - "daily/2026-05-11.md"
created: 2026-05-11
updated: 2026-05-11
---

# PersistentLoggers Accumulation Trap: boot.log Bloat

`PersistentLoggers.warn` called in a periodic loop (e.g., every 30 seconds for stats collection) writes to `boot.log` on disk on every iteration. Over a multi-hour VPN session, this accumulates boot.log to 2.8MB+. The fix: use `Log.w` (logcat only) for periodic non-critical messages and reserve `PersistentLoggers.warn/error` exclusively for one-shot critical events (JNI pre-blocking checkpoints, startup failures, crash diagnostics).

## Key Points

- `PersistentLoggers.warn` in `OzeroVpnService.kt` stats loop (every 30s when stats unavailable) → boot.log grows linearly with session duration
- `PersistentLoggers.info` on success events (DataStoreWarpConfigSlotStore, TunnelController, EngineWarp, ProxyWarpAutoConfig, MainViewModel) → unnecessary disk writes; `Log.i` sufficient
- boot.log is append-only, never truncated during session → unbounded growth
- 2.8MB boot.log after single extended session — degrades boot.log readability for actual crash diagnosis
- Rule: `PersistentLoggers.error/warn` = one-shot critical events only; `Log.i/d/w` = everything recurring or success-path

## Details

### The Accumulation Mechanism

`BootFileLogger` writes to `filesDir/debug/boot.log` as an append-only file initialized in `attachBaseContext`. Every `PersistentLoggers.warn(TAG, msg)` call appends a timestamped line to this file. For one-shot events (library load failure, JNI crash, startup error), this is correct — these events are rare and critical for post-mortem diagnosis.

When `PersistentLoggers.warn` is placed inside a periodic loop, the behavior changes fundamentally. The stats collection loop in `OzeroVpnService` runs every 30 seconds. When native stats are unavailable (engine not ready, native library not loaded), it logged a warning via `PersistentLoggers.warn`. Over a 4-hour VPN session: `4 * 60 * 2 = 480` warning lines — each ~200 bytes = ~96KB from a single loop. With 5 success-event `PersistentLoggers.info` calls on various success paths firing on every config read or state transition, the total grew to 2.8MB.

### The Fix: Categorize Log Targets

Five `PersistentLoggers.info` calls on success paths were demoted to `Log.i`:
- `DataStoreWarpConfigSlotStore` — slot read success
- `TunnelController` — state transition
- `EngineWarp` — engine start success
- `ProxyWarpAutoConfig` — config fetch success
- `MainViewModel` — UI state update

One `PersistentLoggers.warn` in the stats loop was demoted to `Log.w`.

Critical JNI checkpoints were **preserved** in `PersistentLoggers`:
- `loadOnce()` pre/post library load
- `nativeStart` / `nativeStop` lifecycle
- Pre-SIGSEGV/SIGABRT diagnostic markers

These fire at most once per engine start/stop cycle, not in loops.

### Rule: PersistentLoggers Usage Contract

| Event type | Logger | Rationale |
|-----------|--------|-----------|
| JNI pre-blocking checkpoint | `PersistentLoggers.warn` | Need on-disk evidence if process dies during JNI |
| Startup/init failure | `PersistentLoggers.error` | Critical for post-mortem; fires once |
| Crash diagnostic marker | `PersistentLoggers.error` | Must survive process death |
| Periodic status/health | `Log.w` / `Log.i` | Goes to logcat + UnifiedLogger; not persistent |
| Success event | `Log.i` / `Log.d` | Informational; disk write unnecessary |
| Stats unavailable (loop) | `Log.w` | Periodic; would bloat boot.log |

## Related Concepts

- [[concepts/compose-launchedeffect-crash-invisibility]] - boot.log is the persistent channel that LaunchedEffect exceptions miss; keeping it clean ensures crash evidence is findable
- [[concepts/android-silent-crash-diagnosis]] - boot.log is a primary diagnostic artifact; bloated boot.log makes crash diagnosis harder
- [[concepts/engine-ownership-boundary]] - Stats polling loop is one of the VM flows that should be gated; its logging was also incorrectly targeted

## Sources

- [[daily/2026-05-11.md]] - Session 11:23: stats loop `PersistentLoggers.warn` every 30s → boot.log 2.8MB; 5 success-event `PersistentLoggers.info` → `Log.i`; critical JNI checkpoints preserved
