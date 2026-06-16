---
title: "FPTN Android Native Build Pipeline: Conan2, CMake Toolchain, and Reference Source"
aliases: [fptn-native-build, fptn-conan-build, fptn-cmake-toolchain]
tags: [fptn, android, native, conan, cmake, ci]
sources:
  - "daily/2026-05-23.md"
created: 2026-06-12
updated: 2026-06-12
---

# FPTN Android Native Build Pipeline: Conan2, CMake Toolchain, and Reference Source

FPTN Android native integration is a C/C++ Android `.so` build through Conan2 and CMake, not a generic Go server build. The Android client reference uses native client code and dependency sources that must be wired through Conan-generated CMake files. Guessing build flags or using the Android NDK toolchain directly led to repeated CI failures.

## Key Points

- Conan2 CMake builds must use `build/Release/generators/conan_toolchain.cmake`, not the Android NDK toolchain file directly.
- `camouflage-tls` does not guard `add_subdirectory(tests)` and `add_subdirectory(example)` with `BUILD_TESTING`, so `-DBUILD_TESTING=OFF` alone is ineffective.
- `FETCHCONTENT_SOURCE_DIR_CAMOUFLAGETLS` plus a scoped CMakeLists patch is required to pre-populate the dependency and remove unconditional test/example subdirectories.
- The Android arm64 Conan host profile needs explicit `compiler.cppstd=17`; without it, Conan fails before the native library is produced.
- Release workflows should assert that `libfptn.so` exists after download, matching the existing native-library guard pattern.

## Details

The failure chain began when CMake was invoked with the Android NDK toolchain directly. That bypassed Conan2's generated package metadata, so CMake could not find `fptnConfig.cmake` under `build/Release/generators/`. The correct invocation uses Conan's generated toolchain as the only CMake toolchain entry; Conan then supplies package paths, compiler settings, and Android cross-build configuration.

The second failure was assuming that `BUILD_TESTING=OFF` disables dependency tests. `camouflage-tls` unconditionally adds its `tests` and `example` directories, so the flag has no effect unless the dependency source is patched or wrapped. The durable pattern is to read the dependency CMakeLists before adding flags, because flags only work if the upstream project checks them.

The third failure was Conan profile incompleteness. The Android arm64 host profile must declare `compiler.cppstd=17`, otherwise Conan refuses to resolve/build packages that require an explicit C++ standard. This is a build-time trap distinct from [[concepts/android-ndk-cxx-static-linking]], which is a runtime-load trap caused by linking against `c++_shared`.

## Related Concepts

- [[concepts/fptn-engine-protocol]] - FPTN protocol, token, and JNI/native runtime behavior that the `.so` implements.
- [[concepts/android-ndk-cxx-static-linking]] - Runtime namespace failure avoided by `compiler.libcxx=c++_static`.
- [[concepts/native-binary-auto-update-pipeline]] - Broader CI pattern for producing and downloading native Android artifacts.
- [[concepts/release-abi-sentinel-alignment]] - Release checks must match the arm64-v8a-only APK contract.

## Sources

- [[daily/2026-05-23.md]] - Session 03:04: Conan android-arm64 profile requires `compiler.cppstd=17`; `libfptn.so` assert added to `release.yml`; `gh workflow run binaries.yml` needs `--ref dev`. Session 04:05/FPTN build pipeline: CMake must use Conan-generated toolchain, `camouflage-tls` requires pre-population plus CMakeLists patch because `BUILD_TESTING=OFF` is ineffective, and FptnClient-Android reference branch is `develop`.
