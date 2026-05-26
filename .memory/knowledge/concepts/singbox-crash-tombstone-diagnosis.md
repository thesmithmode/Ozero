---
title: "Sing-box Crash Tombstone Diagnosis"
aliases: [singbox-tombstone, sigabrt-diagnosis, engine-singbox-crash]
tags: [singbox, android, crash, jni, sigabrt, debugging]
sources:
  - "daily/2026-05-26.md"
created: 2026-05-26
updated: 2026-05-26
---

# Sing-box Crash Tombstone Diagnosis

When the `:engine_singbox` process crashes with SIGABRT or SIGSEGV, Android writes a tombstone file to the app's debug directory. The tombstone contains the native stack trace, registers, and signal info needed to identify the root cause. Without the tombstone, diagnosing native Go runtime crashes in sing-box is guesswork.

## Key Points

- Tombstone path: `/data/user/0/ru.ozero.app/files/debug/` — pull via `adb pull`
- `:engine_singbox` runs in a separate process; its crashes do not appear in the main app logcat
- SIGABRT in sing-box 1.13.0 is commonly caused by unsupported outbound types (`dns`, `splithttp`) that fail config parse
- SIGSEGV may indicate Go runtime GC/heap corruption or JNI handle misuse
- Tombstone is written only if the process crashes, not if it exits cleanly

## Details

### Pulling Tombstone from Device

```bash
adb pull /data/user/0/ru.ozero.app/files/debug/
```

This pulls all debug artifacts including tombstones. The directory is created by sing-box's Go runtime panic handler. Files are timestamped and named with process IDs.

If `adb pull` fails with permission denied, ensure the device is in developer mode and USB debugging is enabled. On rooted devices the path is accessible from root shell:

```bash
adb shell ls /data/user/0/ru.ozero.app/files/debug/
```

### Known SIGABRT Causes (v0.2.8/v0.2.9)

Three SIGABRTs were found in `:engine_singbox` logs from the v0.2.8/v0.2.9 investigation:

1. **`dns` outbound type**: sing-box 1.13.0 removed the `dns` outbound type; any config containing it causes immediate config parse failure → SIGABRT. Fix: filter or replace with route rule `"action": "hijack-dns"` at parse time in `SingboxSubscriptionParser`.

2. **`splithttp` transport**: The current `libbox.so` does not implement the `splithttp` transport type. Profiles using it produce `unknown transport type: splithttp` → SIGABRT. Fix: filter these profiles in `SingboxSubscriptionParser` before building the config.

3. **Connected→fail race**: Sing-box signals `Connected` state then immediately fails internally. This may be a timing issue between the Go runtime's connection establishment and the JNI callback. Root cause requires tombstone inspection.

### startWithConfig SIGABRT (session 21:41 finding)

In session 21:41, `DeadObjectException` and `CRASH_NATIVE` (SIGABRT) were observed occurring inside `libbox.startWithConfig`, **after** `checkConfig` had already passed successfully. Two different config sizes crashed: `configLen=868` and `configLen=20448`. This means the crash is not a config validation error — it happens during the actual Go runtime startup with an otherwise valid config.

This pattern indicates a native Go runtime crash inside `libbox.so` itself, not a Kotlin-layer issue. The `DeadObjectException` on the Kotlin side is the Binder exception thrown when the remote `:engine_singbox` process dies. The actual crash cause is in the Go stack and requires tombstone analysis.

### Relationship to Config Parse vs Runtime Crashes

Config parse failures (SIGABRT type 1 and 2 above) are deterministic and reproducible with the same subscription config. They can be fixed at the Kotlin layer without needing tombstone analysis — the `unknown transport type` and `dns outbound deprecated` messages appear in logcat before the crash.

True runtime crashes (SIGSEGV from GC corruption, JNI ref leaks, or Go runtime state corruption) require the tombstone native trace to identify the Go call frame. The `checkConfig passed → startWithConfig SIGABRT` sequence is in this category — it is non-deterministic with respect to input and requires deeper investigation of the Go runtime state at crash time.

### Ruled Out: block outbound (v0.2.10 session)

`block` outbound verified STILL registered in sing-box v1.13.12 source (`protocol/block/outbound.go`, `outbound.Register[option.StubOptions](registry, C.TypeBlock, New)`). NOT the crash cause. `checkConfig` passing both configs confirms config format is valid.

### Remaining hypotheses (need tombstone)

1. `PlatformInterface` callback crash — `localDNSTransport()` returns null, 1.13 DNS refactoring may dereference it
2. Go runtime conflict with other Go engines (gomobile go.Seq multi-SDK)
3. TUN fd lifecycle across AIDL — `ParcelFileDescriptor.fromFd()` pattern
4. Crash timing: 600ms between checkConfig and DeadObjectException — crash during `startOrReloadService` execution, `post-startOrReloadService` checkpoint never reached

### Next steps

- Pull tombstone from device: `adb pull /data/user/0/ru.ozero.app/files/debug/`
- Add diagnostic checkpoint inside startOrReloadService 600ms window
- Check if `localDNSTransport()` returning null causes panic in 1.13.12

## Related Concepts

- [[concepts/singbox-dns-outbound-deprecated]] - Config parse failure: `dns` outbound removed in 1.13.0; fix in parser
- [[concepts/singbox-splithttp-unsupported]] - Config parse failure: `splithttp` transport not in current libbox.so
- [[concepts/singbox-engine-design]] - Engine architecture context: process isolation, GoRuntimeGuard, config pipeline
- [[concepts/go-runtime-process-isolation]] - Why `:engine_singbox` crashes don't bring down the main process

## Sources

- [[daily/2026-05-26.md]] - Session 13:59: tombstone pull command identified for singbox SIGSEGV/SIGABRT diagnosis: `adb pull /data/user/0/ru.ozero.app/files/debug/`; session 19:44: 3 SIGABRTs in `:engine_singbox` found in v0.2.8/v0.2.9 logs with `dns outbound deprecated` and `unknown transport type: splithttp` as root causes; session 21:41: `DeadObjectException` + `CRASH_NATIVE` in `libbox.startWithConfig` after `checkConfig passed` with configLen=868 and configLen=20448 — native Go crash, not config validation failure
