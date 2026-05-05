---
title: "AmneziaWG AbstractBackend ReLinker Loading Trap"
aliases: [amneziawg-loadlibrary, libam-go-loading, awg-relinker-trap]
tags: [warp, amneziawg, native, jni, gotcha]
sources:
  - "daily/2026-05-05.md"
created: 2026-05-05
updated: 2026-05-05
---

# AmneziaWG AbstractBackend ReLinker Loading Trap

The `amneziawg-android` AAR loads `libam-go.so` exclusively via `ReLinker.loadLibrary(context, "am-go")` inside the `AbstractBackend` constructor. Calling `GoBackend.awgTurnOn()` or any other JNI method without first instantiating `AbstractBackend` (which runs the constructor) results in a JNI call against an unloaded native library, causing an immediate `UnsatisfiedLinkError` or SIGABRT.

## Key Points

- `AbstractBackend(context)` constructor calls `ReLinker.loadLibrary(context, "am-go")` — this is the only place the native library loads
- Calling `GoBackend.awgTurnOn()` directly (bypassing `AbstractBackend` construction) = native not loaded → JNI fail
- ReLinker is used instead of `System.loadLibrary` because the amneziawg-android AAR is designed for split-APK / dynamic feature delivery (ReLinker handles `.apk!lib/` extraction)
- In a universal APK (no splits, no dynamic features), `System.loadLibrary("am-go")` works directly and is the correct lazy-load pattern
- Fix: add `loadOnce("am-go")` via `System.loadLibrary` in `RealWarpSdkBridge` before any `GoBackend` JNI calls

## Details

### The Loading Chain

The `amneziawg-android` library (`com.zaneschepke:amneziawg-android`) ships with a Go JNI layer (`libam-go.so`) that implements the WireGuard-like tunnel operations. The library provides `AbstractBackend` as the base class for `GoBackend`. The design assumption is that callers instantiate `AbstractBackend` (or `GoBackend extends AbstractBackend`) before calling tunnel operations, since `AbstractBackend()` constructor triggers `ReLinker.loadLibrary(context, "am-go")`.

In Ozero, `RealWarpSdkBridge` was calling `GoBackend.awgTurnOn(...)` as a static-like call without holding an `AbstractBackend` instance. Since no `AbstractBackend` was ever constructed, `ReLinker.loadLibrary` never ran, and `libam-go.so` was never loaded into the process. The first JNI call hit an unresolved symbol.

### ReLinker vs System.loadLibrary

ReLinker is designed for apps that use split APKs or Play Feature Delivery — it handles the case where `.so` files are compressed inside split APK archives and must be extracted before loading. For Ozero's universal APK (`abiFilters: arm64-v8a, armeabi-v7a, x86_64`, no dynamic features, no splits), all `.so` files are embedded directly in the APK and extractable by the standard Android classloader. `System.loadLibrary("am-go")` works correctly and does not require ReLinker's extraction logic.

The correct pattern for `RealWarpSdkBridge`:

```kotlin
private val loadOnce by lazy {
    System.loadLibrary("am-go")
}

fun start(config: AwgConfig) {
    loadOnce  // triggers library load on first call
    GoBackend.awgTurnOn(...)
}
```

This follows Ozero's established `loadOnce()` idiom (documented in CLAUDE.md) for lazy native library loading.

### Symptom vs Root Cause

The observable symptom was `awgTurnOn` failing with a JNI error. Initial diagnosis might point to a missing `.so` file in the APK, but the APK contained `libam-go.so` correctly. The root cause was the load sequence: the library existed but was never loaded because `AbstractBackend` constructor was never called.

This pattern generalizes: any AAR that loads native libraries in its constructor creates an implicit contract that callers must instantiate the class before using JNI-bound methods. When Ozero wraps AAR functionality behind a bridge interface, the bridge must replicate the loading step explicitly.

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] - The WARP engine migration to AmneziaWG that introduced this bridge
- [[concepts/hilt-di-native-library-failure]] - Similar pattern: native load failures bypass normal error handling
- [[concepts/vpnservice-builder-traps]] - Other patterns where deviation from expected initialization order causes silent failures

## Sources

- [[daily/2026-05-05.md]] - Session 22:08: WARP awgTurnOn JNI fail diagnosed as libam-go.so never loaded; AbstractBackend ctor is sole load site; fix = System.loadLibrary("am-go") in RealWarpSdkBridge
