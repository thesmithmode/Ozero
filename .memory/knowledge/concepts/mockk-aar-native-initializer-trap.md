---
title: "mockk AAR Native Static Initializer Trap"
aliases: [mockk-aar-native, mockk-unsatisfiedlinkerror, aar-class-mockk-crash]
tags: [testing, mockk, android, native, gotcha, jni]
sources:
  - "daily/2026-05-10.md"
created: 2026-05-10
updated: 2026-06-09
---

# mockk AAR Native Static Initializer Trap

Calling `mockk<T>()` on a class from an Android AAR that has a native static initializer block (e.g., calls `System.loadLibrary` in a companion object or static init) triggers the JVM class loader to execute that initializer at mock-creation time. In unit tests where the native library is unavailable, this causes `UnsatisfiedLinkError`, which the JVM wraps as `ExceptionInInitializerError`. Every subsequent test that references the same class — even indirectly — receives `NoClassDefFoundError` because the JVM marks the class as "failed to initialize" for the remainder of the process lifetime.

## Key Points

- `mockk<ConnectLocation>()` on a class from URnetwork SDK AAR triggers native static initializer loading `libgojni.so` — unavailable in unit tests
- First test: `UnsatisfiedLinkError` → wrapped as `ExceptionInInitializerError`
- Subsequent tests: `NoClassDefFoundError` — JVM permanently marks the class as failed, no retry
- Simply referencing the class (mock creation, type parameter usage) is enough to trigger loading — no method calls needed
- Solution: **wrapper data class** in bridge interface — tests only ever see the wrapper, never the AAR class directly

## Details

### The Initialization Trigger Chain

The JVM initializes a class exactly once when it is first loaded. For AAR classes that call `System.loadLibrary(...)` in a static initializer block, this happens at the moment `Class.forName` is called, which mockk triggers internally during `mockk<ConnectLocation>()`. The sequence:

1. mockk calls `Class.forName("com.bringyour.sdk.ConnectLocation")`
2. JVM loads the class and runs its static initializer block
3. Static initializer calls `System.loadLibrary("gojni")` — library not present in unit test classpath
4. `UnsatisfiedLinkError` is thrown inside the initializer
5. JVM wraps it as `ExceptionInInitializerError` and marks `ConnectLocation` as "initialization failed"
6. Test 1 fails with `ExceptionInInitializerError`
7. Tests 2-N fail with `NoClassDefFoundError: Could not initialize class ConnectLocation`

The cascading failure across the entire test suite is the most disruptive aspect — a single mock call in one test poisons all subsequent tests.

### The LocationInfo Wrapper Solution

The correct architectural fix is to never let test code see the AAR class. Introduce a wrapper data class in the bridge interface:

```kotlin
// In bridge interface (engines-core or engine-urnetwork)
data class LocationInfo(
    val country: String,
    val countryCode: String,
    val city: String = "",
)

interface UrnetworkSdkBridge {
    fun selectedLocationInfo(): LocationInfo?
    // ...
}
```

The real implementation (`RealUrnetworkSdkBridge`) maps `ConnectLocation` → `LocationInfo` internally:

```kotlin
override fun selectedLocationInfo(): LocationInfo? {
    val loc = deviceLocal?.connectLocation ?: return null
    return LocationInfo(
        country = loc.country ?: "",
        countryCode = loc.countryCode ?: "",
    )
}
```

Tests mock `UrnetworkSdkBridge` (an interface) and return `LocationInfo(...)` objects directly. The `ConnectLocation` class is never referenced in test code; its static initializer never fires.

### Why This Pattern Generalizes

Any AAR class with native static initialization creates this trap:
- AmneziaWG SDK: `GoBackend`, `ProxyGoBackend` — already handled by checking in SO files and adding Java glue directly
- URnetwork SDK: `ConnectLocation`, `DeviceLocal`, and other generated gomobile classes
- Any third-party Go/Rust AAR using gomobile or similar JNI binding generators

The pattern applies universally: if a class comes from a native-heavy AAR, it should never appear in test code. Bridge interface + wrapper data class is the structural solution.

### Relationship to CI Failures

This trap caused a multi-day CI failure in Ozero's `engine-urnetwork` module. Tests that used `mockk<ConnectLocation>()` passed in IDE (native library found via local AAR) but failed on CI (clean runner, no native library in unit test path). The failure manifested as `NoClassDefFoundError` in tests that appeared unrelated to the original mock call — making the root cause non-obvious.

## Related Concepts

- [[concepts/amneziawg-jni-classpath-completeness]] — Related pattern: native SO requires specific Java classes at load time; this article covers the inverse (Java tests cannot reference native-backed classes)
- [[concepts/hilt-di-native-library-failure]] — Hilt DI also breaks when native library load fails; similar root cause, different context
- [[concepts/urnetwork-sdk-integration]] — URnetwork SDK where `ConnectLocation` caused this trap
- [[concepts/urnetwork-location-empty-string-fallback]] — `LocationInfo` wrappers also need app-level blank-string normalization
- [[connections/native-io-test-isolation-feedback-loop]] — Shared boundary with ByeDPI: tests should not cross native or real-IO seams

## Sources

- [[daily/2026-05-10.md]] — Session 14:00: `mockk<ConnectLocation>()` caused `UnsatisfiedLinkError` → `ExceptionInInitializerError` → cascading `NoClassDefFoundError`; fix = `LocationInfo` wrapper data class in `UrnetworkSdkBridge` interface; tests removed all direct `ConnectLocation` references
