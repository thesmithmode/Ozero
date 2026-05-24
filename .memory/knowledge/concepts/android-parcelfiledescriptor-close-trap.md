---
title: "Android POSIX close() via ParcelFileDescriptor"
aliases: [os-close-int-trap, parcelfiledescriptor-adoptfd]
tags: [android, jni, native, posix, gotcha]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# Android POSIX close() via ParcelFileDescriptor

`Os.close(Int)` does not exist in Android's POSIX API surface. Attempting to call it produces a compile error. The correct pattern for closing a raw integer file descriptor on Android is `ParcelFileDescriptor.adoptFd(rawFd).close()`, which wraps the integer fd in a `ParcelFileDescriptor` and closes it through the Android framework.

## Key Points

- `android.system.Os` exposes `close(FileDescriptor)`, not `close(Int)` — the integer overload is absent
- `ParcelFileDescriptor.adoptFd(Int)` transfers ownership of a raw int fd into a `ParcelFileDescriptor` and marks the original fd as owned by the new object
- Calling `.close()` on the resulting `ParcelFileDescriptor` correctly releases the underlying fd via the framework
- This pattern is required when receiving raw int fds from JNI or native code that must be closed from Kotlin/Java
- The trap typically appears during P1-level fixes involving fd lifecycle (e.g., closing stale server fds, VPN tunnel cleanup)

## Details

### Why `Os.close(Int)` Fails

Android's `Os` class in `android.system` mirrors POSIX but maps to `FileDescriptor` objects rather than raw integers. The POSIX `close(int fd)` signature is not directly exposed because Android's Java layer wraps file descriptors in `FileDescriptor` objects for lifecycle safety. Native code that exposes raw `int` fds to Kotlin requires explicit wrapping before they can be closed.

This mismatch becomes apparent specifically when writing code that interfaces with JNI layers returning `int` fds — such as socket fds from ByeDPI's native proxy, TUN fd from `VpnService.establish()`, or UAPI sockets from WireGuard's WARP engine.

### Correct Pattern

```kotlin
// WRONG — does not compile
Os.close(rawFd)

// CORRECT — wraps and closes
ParcelFileDescriptor.adoptFd(rawFd).close()
```

`adoptFd` documents that ownership is transferred: the raw integer fd must not be closed separately after adoption. The `ParcelFileDescriptor.close()` will call the native `close()` syscall via the framework.

### Discovery Context

This trap was discovered during the v0.0.2-5 P1-fixes batch when implementing a fix for a raw fd cleanup path. CI failed with a compile error on `Os.close(Int)`. The fix was straightforward once the correct API was identified, but the error message does not point to `ParcelFileDescriptor.adoptFd` as the solution.

## Related Concepts

- [[concepts/byedpi-stale-serverfd-unconditional-forceclose]] - Context where fd lifecycle bugs cause stale socket issues
- [[concepts/jni-local-global-ref-lifecycle]] - Related JNI resource lifecycle management
- [[concepts/vpnservice-builder-traps]] - VpnService TUN fd handling shares similar fd lifecycle concerns

## Sources

- [[daily/2026-05-02.md]] - Session 13:20: `Os.close(Int)` compile error discovered during P1 fd cleanup fix; `ParcelFileDescriptor.adoptFd(rawFd).close()` identified as correct pattern
