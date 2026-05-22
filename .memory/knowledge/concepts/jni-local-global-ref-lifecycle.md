---
title: "JNI Reference Lifecycle: Local, Global, and WeakGlobal Traps"
aliases: [jni-ref-management, jni-memory-leaks, jni-local-global-weakglobal]
tags: [jni, android, native, memory, gotcha]
sources:
  - "daily/2026-05-22.md"
created: 2026-05-22
updated: 2026-05-22
---

# JNI Reference Lifecycle: Local, Global, and WeakGlobal Traps

Android JNI has three reference types with distinct ownership rules. Confusing them produces memory leaks and dangling references invisible in unit tests but fatal in production under load. All three trap categories were discovered in the FPTN engine C++ implementation and fixed before CI pass.

## Key Points

- `GetStringUTFChars` **must** have a paired `ReleaseStringUTFChars` or it leaks every JNI call
- `NewGlobalRef` for a JNI **return value** is wrong — local refs work correctly as return values and `NewGlobalRef` creates an untracked leak
- `NewWeakGlobalRef` requires `DeleteWeakGlobalRef` — typically in a `nativeDestroy` method paired with `nativeCreate`
- Local refs are valid only within the current JNI call frame; global refs live until explicitly deleted; weak global refs do not prevent GC
- JNI memory leaks in C++ are not visible in Android unit test CI — they only manifest in integration tests or at runtime under repeated calls

## Details

### GetStringUTFChars Without Release

`GetStringUTFChars(env, jstr, nullptr)` allocates a C string copy on every call. The matching `ReleaseStringUTFChars(env, jstr, cstr)` must be called after use, even on error paths. Without it, every JNI invocation that converts a Java string to C leaks proportionally to the string length.

```cpp
// BAD — leak per call
const char* cstr = env->GetStringUTFChars(jstr, nullptr);
process(cstr);
// ReleaseStringUTFChars never called

// GOOD — RAII or explicit release
const char* cstr = env->GetStringUTFChars(jstr, nullptr);
process(cstr);
env->ReleaseStringUTFChars(jstr, cstr);
```

Found in `utils.h` of the FPTN engine and fixed before shipping.

### NewGlobalRef on Return Values

`NewGlobalRef(obj)` promotes a local ref to a global ref that survives JNI call boundaries. This is correct for refs that must be held across JNI calls (e.g., stored in a C++ struct). But using `NewGlobalRef` for a value that is simply returned to Java creates a reference that is never tracked or freed:

```cpp
// BAD — GlobalRef for return value creates untracked leak
jobject response = env->NewObject(clazz, constructor, ...);
return env->NewGlobalRef(response);  // leak: caller gets local ref, global ref stays allocated

// GOOD — return local ref directly; JNI framework handles lifetime
jobject response = env->NewObject(clazz, constructor, ...);
return response;
```

Found in `https_client.cpp` of the FPTN engine. The caller on the Java/Kotlin side receives a local ref copy and the `GlobalRef` is never freed.

### NewWeakGlobalRef Without Delete

`NewWeakGlobalRef(thiz)` is used to hold a reference to the calling Kotlin object inside a C++ struct without preventing garbage collection. The symmetry rule: every `NewWeakGlobalRef` must have a `DeleteWeakGlobalRef` on object destruction.

Pattern: create in `nativeCreate`, delete in `nativeDestroy`:

```cpp
// nativeCreate
wrapper_ = env->NewWeakGlobalRef(thiz);  // store in C++ struct

// nativeDestroy
if (wrapper_ != nullptr) {
    env->DeleteWeakGlobalRef(wrapper_);
    wrapper_ = nullptr;
}
```

When `nativeDestroy` is absent or does not call `DeleteWeakGlobalRef`, the weak ref accumulates across object creation cycles, eventually exhausting the JNI local/global ref table. Found in `websocket_client.cpp` of the FPTN engine.

### Local Refs in Long-Running Functions

JNI allocates local refs in a per-frame table (default 512 slots). Long-running native functions that create objects in a loop (especially under repeated calls from a tight loop) must `DeleteLocalRef` for temporary objects:

```cpp
for (...) {
    jclass clazz = env->FindClass("...");         // allocates local ref
    jstring str = env->NewStringUTF("...");       // allocates local ref
    // use clazz and str
    env->DeleteLocalRef(clazz);
    env->DeleteLocalRef(str);
}
```

Without `DeleteLocalRef`, the function exhausts local ref slots after hundreds of iterations and crashes with `JNI ERROR (app bug): local reference table overflow`. Found in `https_client.cpp` success path.

### JNI Leaks Are CI-Silent

JNI memory leaks in C++ are not covered by Android unit tests (`.so` is not built during unit test CI runs — only during assembly). They surface only in:
- Full device builds (CI assembly step)
- Manual APK testing under repeated operations
- Heap profilers (`Android Studio Memory Profiler`, `malloc_debug`)

Rule: when writing any JNI native function, immediately apply the ref management checklist before the first commit. Retrofit is expensive.

## Related Concepts

- [[concepts/fptn-engine-protocol]] — FPTN C++ JNI layer where all three trap types were discovered and fixed
- [[concepts/byedpi-jni-guard-hardening]] — ByeDPI C JNI guard ownership pattern; similar lifecycle concerns in C
- [[concepts/engine-ownership-boundary]] — JNI object ownership and teardown ordering apply to all native engines
- [[concepts/gomobile-bind-gotchas]] — Go mobile bindings (different toolchain but same JNI reference rules apply)

## Sources

- [[daily/2026-05-22.md]] — Session 22:20+: FPTN CI run exposed GetStringUTFChars without Release (utils.h), NewWeakGlobalRef without Delete (websocket_client.cpp), NewGlobalRef on return value (https_client.cpp), local ref leak on success path (https_client.cpp); all fixed in commit eb237773 before CI pass
