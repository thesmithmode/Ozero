---
title: "Android NDK: c++_static Required for Third-Party .so (clns-7 Namespace)"
aliases: [cxx-static-linking, libcxx-shared-android, clns7-namespace]
tags: [android, ndk, native, conan, fptn, cmake]
sources:
  - "daily/2026-05-23.md"
created: 2026-05-23
updated: 2026-05-23
---

# Android NDK: c++_static Required for Third-Party .so (clns-7 Namespace)

When building a native `.so` for Android with Conan2 + CMake, the C++ runtime library must be linked **statically** (`c++_static`). Using `c++_shared` causes a runtime crash: Android loads the `.so` into the `clns-7` (ClassLoader Native Namespace) which does not include `libc++_shared.so` in its search path. The library loads successfully in the build environment but crashes with `dlopen failed: library "libc++_shared.so" not found` on device.

## Key Points

- `libc++_shared.so` is available only in namespace `clns-2` (the main app namespace); third-party `.so` loaded via `System.loadLibrary()` from app code lands in `clns-7`
- `clns-7` does not inherit `libc++_shared.so` — it is not in `permitted.paths` for this namespace
- Fix: set `compiler.libcxx=c++_static` in the Conan profile (e.g., `android-arm64` host profile in Dockerfile)
- The crash is **silent at build time** — compilation and packaging succeed; the crash occurs only on first `System.loadLibrary()` call on device
- This applies to all third-party `.so` files built with Conan2 targeting Android, not only FPTN

## Details

### Namespace Architecture

Android's linker namespace system (`bionic` linker) partitions native libraries:

| Namespace | Who populates it | `libc++_shared.so` visible? |
|-----------|-----------------|----------------------------|
| `clns-2` (default/app) | APK `lib/arm64-v8a/`, system libs | Yes |
| `clns-7` (classloader-ns) | `System.loadLibrary()` from non-default ClassLoader | **No** |

When an Android app calls `System.loadLibrary("fptn_native_lib")`, the linker resolves it in `clns-7`. If `libfptn_native_lib.so` was linked with `-lc++_shared`, the dynamic linker tries to find `libc++_shared.so` in `clns-7`'s search path — and fails.

### Conan Profile Fix

In the Conan host profile for Android arm64 (e.g., `Dockerfile.fptn`):

```ini
# Before (crash on device):
compiler.libcxx=c++_shared

# After (correct):
compiler.libcxx=c++_static
```

This links `libc++` statically into the `.so`. The resulting `.so` is self-contained: it carries its own copy of the C++ runtime. The file size increases slightly (~300–500 KB) but there are no runtime dependencies on namespace-restricted shared libs.

### FPTN-Specific Occurrence

The FPTN `libfptn_native_lib.so` was originally built with `c++_shared` in CI. The crash manifested immediately on first `System.loadLibrary("fptn_native_lib")` — the FPTN engine failed to start with a native library load error. The fix was adding `compiler.libcxx=c++_static` to the `android-arm64` Conan profile in `Dockerfile.fptn` (commit `cbeaeaee`). This is distinct from the Conan toolchain path issue (`compiler.cppstd=17`) which is a build-time failure, not a runtime crash.

### Sentinel

`JniContractTest` verifies that `libfptn_native_lib.so` is loadable via `System.loadLibrary()`. A `c++_shared` regression would fail this test — the native library would throw `UnsatisfiedLinkError` or crash the process before any JNI calls can be made.

## Related Concepts

- [[concepts/extract-native-libs-legacy-packaging]] — `extractNativeLibs` packaging requirement; related to how native libs are extracted from the APK before the linker resolves them
- [[concepts/fptn-engine-protocol]] — FPTN engine architecture; `libfptn_native_lib.so` is the core native component
- [[concepts/go-runtime-process-isolation]] — amneziawg-go (`libam-go.so`) is a Go-built `.so` with different linking requirements (gomobile bind)
- [[concepts/fptn-sni-bypass-method]] — The SNI bypass logic that depends on `libfptn_native_lib.so` loading correctly

## Sources

- [[daily/2026-05-23.md]] — Session 12:23: FPTN crash on first library load after binaries.yml build; root cause: `compiler.libcxx=c++_shared` in Conan android-arm64 profile; `libc++_shared.so` not in clns-7 namespace search path; fix: `compiler.libcxx=c++_static` in `Dockerfile.fptn` Conan profile (commit `cbeaeaee`)
