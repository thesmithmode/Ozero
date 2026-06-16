---
title: "FPTN JNI and FD Lifecycle Contract"
sources:
  - "daily/2026-05-22.md"
created: 2026-06-12
updated: 2026-06-12
---

# FPTN JNI and FD Lifecycle Contract

FPTN's native bridge combines JNI object references, HTTPS/WebSocket callbacks, and a blocking TUN read loop. Its lifecycle contract is stricter than ordinary coroutine cleanup: native stop must happen before fd close, fd close must happen before coroutine join, and JNI references must be released explicitly.

## Key Points

- `fis.read()` on the TUN fd is blocking and does not stop just because the coroutine scope was cancelled.
- Correct stop order is `nativeStop -> pfd.close -> scope.cancel -> join -> nativeDestroy`.
- `GetStringUTFChars` must always be paired with `ReleaseStringUTFChars`.
- `NewWeakGlobalRef(thiz)` must be deleted in `nativeDestroy`.
- JNI return objects do not need `NewGlobalRef`; returning a local ref is valid and avoids leaks.

## Details

The stop sequence exists to avoid a deadlock. If `tunScope.cancel()` is followed by `join()` before the fd is closed, the coroutine can remain blocked inside `fis.read()`. Closing the `ParcelFileDescriptor` first unblocks the read with an `IOException`, allowing the read loop to exit and the coroutine join to complete before `nativeDestroy`.

The same session found native memory leaks in the FPTN JNI layer. String chars obtained from JNI must be released on every path, weak global refs created during `nativeCreate` must be deleted during `nativeDestroy`, and HTTPS response return values should not be promoted to global refs. These bugs are not reliably caught by unit-test CI because the C++ bridge is exercised when building and running the native `.so`, not by ordinary JVM tests.

## Related Concepts

- [[concepts/fptn-engine-design]] - Overall FPTN engine protocol, token, TUN, and JNI architecture.
- [[concepts/android-parcelfiledescriptor-close-trap]] - Android fd cleanup must use the correct ParcelFileDescriptor close pattern.
- [[concepts/jni-local-global-ref-lifecycle]] - General JNI reference lifetime rules used by the FPTN bridge.

## Sources

- [[daily/2026-05-22]] - Session 22:20+: `fis.read()` ignored coroutine cancellation; stop order fixed to `nativeStop -> pfd.close -> scope.cancel -> join -> nativeDestroy`; JNI leaks fixed for `GetStringUTFChars`, weak global refs, response refs, and local refs.
