---
title: "Combined AIDL Method: Eliminating IPC Race Conditions"
aliases: [combined-aidl, turnon-and-get-sockets, aidl-race-fix]
tags: [warp, aidl, architecture, concurrency]
sources:
  - "daily/2026-05-14.md"
created: 2026-05-14
updated: 2026-05-14
---

# Combined AIDL Method: Eliminating IPC Race Conditions

Two sequential AIDL IPC calls (`turnOn` then `getSockets`) create a race window where the engine state can change between calls. Replacing them with a single `turnOnAndGetSockets` method that returns both the tunnel handle and protected sockets atomically eliminates this race class entirely.

## Key Points

- Sequential `turnOn()` + `getSockets()` over AIDL has a race window: engine state may change between the two IPC calls
- Combined `turnOnAndGetSockets()` returns a `WarpTurnOnResult` Parcelable with handle + socket list in one atomic IPC call
- `WarpTurnOnResult` is a manual Parcelable (not `@Parcelize`) because it contains `ParcelFileDescriptor` array
- `ParcelFileDescriptor.adoptFd().close()` replaces reflection for fd cleanup — cleaner than `FileDescriptor.class.getDeclaredField("fd")`
- This pattern applies to any AIDL interface where two related operations must be atomic

## Details

### The Race (H3 Fix)

In the process-isolated WARP architecture, the main process communicates with `:engine_warp` via AIDL. The original flow:

1. `binder.turnOn(config)` → returns handle (IPC call 1)
2. `binder.getSockets()` → returns protected sockets (IPC call 2)

Between steps 1 and 2, the WARP engine process could: crash, restart, change state, or the Binder connection could be interrupted. The `getSockets()` call might then return stale data, an empty list, or fail entirely — while the caller believes `turnOn` succeeded.

### The Fix

Single method `turnOnAndGetSockets(config): WarpTurnOnResult` that performs both operations atomically within the engine process and returns a combined result:

```kotlin
// AIDL
WarpTurnOnResult turnOnAndGetSockets(in String config);

// Result
class WarpTurnOnResult : Parcelable {
    val handle: Int
    val protectedSockets: List<ParcelFileDescriptor>
}
```

### fd Cleanup (H4 Fix)

The prior code used Java reflection to access `FileDescriptor.fd` private field for closing file descriptors. Replaced with `ParcelFileDescriptor.adoptFd(rawFd).close()` — standard Android API, no reflection, no access warnings on newer Android versions.

## Related Concepts

- [[concepts/go-runtime-process-isolation]] - WARP process isolation architecture that requires AIDL IPC
- [[concepts/engine-ownership-boundary]] - VpnService sole lifecycle owner; combined AIDL reinforces single-owner pattern

## Sources

- [[daily/2026-05-14.md]] - Session 17:47: combined turnOnAndGetSockets AIDL method (H3 fix), ParcelFileDescriptor.adoptFd().close() replacing reflection (H4 fix), manual WarpTurnOnResult Parcelable
