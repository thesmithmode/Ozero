---
title: "android.util.BrotliInputStream: Hidden API Inaccessible from User Apps"
aliases: [brotli-hidden-api, android-brotli, brotli-inputstream-trap]
tags: [android, api, gotcha, compression, brotli]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# android.util.BrotliInputStream: Hidden API Inaccessible from User Apps

`android.util.BrotliInputStream` exists in the Android framework's internal classpath (present on Android 12+ system image) but is NOT a public SDK API. User apps cannot access it even with `@TargetApi(31)`. Attempting to reference it causes a compile error or `NoClassDefFoundError` at runtime. The correct approach for Brotli decompression in user apps is the `org.brotli:dec` library dependency.

## Key Points

- `android.util.BrotliInputStream` is a system/hidden class, not part of the public Android SDK
- Present on device but inaccessible to apps — guarded by Android's hidden API enforcement (`@hide` in AOSP)
- `@TargetApi(31)` and reflection workarounds are unreliable: hidden API enforcement varies by ROM and vendor policy
- Safe alternative: `org.brotli:dec` (Brotli reference implementation for Java/Android)
- Discovered when FPTN engine's `fptnb:` token parsing (Brotli-compressed variant) attempted to use the system class

## Details

### Why It Exists But Is Inaccessible

Android maintains a large internal API surface for system components and privileged apps. Classes in `android.util.*`, `com.android.internal.*`, and similar packages may be present on all devices but are annotated `@hide` in AOSP source. Since Android 9 (API 28), the platform actively enforces access restrictions via a greylist/blacklist mechanism that prevents reflection-based workarounds in user apps.

`android.util.BrotliInputStream` falls into this category. It is used internally by the framework (e.g., for compressed resource delivery in APK installation) but is not exposed in the public `android.jar` stub used during compilation.

### Discovery Context

Found during FPTN engine implementation. FPTN tokens can use two URI schemes:
- `fptn:` — Base64-encoded JSON with server list (uncompressed)
- `fptnb:` — Brotli-compressed variant

The implementation attempted to use `android.util.BrotliInputStream` for `fptnb:` decoding. The import compiles only because some IDE toolchains include hidden APIs in the completion index. The actual SDK `android.jar` does not include it, causing a compile error in CI.

### Correct Solution

Add `org.brotli:dec` as a dependency (requires explicit approval for new prod deps):

```kotlin
// engine-fptn/build.gradle.kts
implementation("org.brotli:dec:0.1.2")
```

```kotlin
import org.brotli.dec.BrotliInputStream

fun decodeFptnB(encoded: String): String? {
    val compressed = Base64.decode(encoded, Base64.DEFAULT)
    return BrotliInputStream(compressed.inputStream()).use { 
        it.readBytes().toString(Charsets.UTF_8) 
    }
}
```

Until this dep is approved, `fptnb:` tokens must return `null` (unsupported) rather than attempting to decompress.

### Scope of the Hidden API Problem

Other commonly encountered hidden APIs that cause the same issue:
- `android.os.SystemProperties` — accessible via reflection on older Android but blocked on 9+
- `android.telephony.TelephonyManager` internal methods
- `com.android.internal.util.*` classes
- `android.util.Log` internal ring buffer access

Rule: if a class is in an `android.*` or `com.android.internal.*` package and is not documented in the official Android SDK reference (`developer.android.com`), assume it is hidden and inaccessible.

## Related Concepts

- [[concepts/fptn-engine-protocol]] — FPTN engine where this trap was discovered; fptnb: token decoding deferred pending brotli dep approval
- [[concepts/nubia-rom-permission-enforcement]] — Another Android ROM-level access restriction that caused unexpected failures
- [[concepts/yaml-biginteger-parsing-trap]] — Similar "looks valid, silently wrong" category of platform parsing traps

## Sources

- [[daily/2026-05-22.md]] — Session 22:20+: FPTN CI run 2 (commit 97dec3b2) failed with compile error on `android.util.BrotliInputStream`; class is not public SDK API; `fptnb:` token type returns null until `org.brotli:dec` dep approved; BrotliInputStream import removed from FptnToken.kt
