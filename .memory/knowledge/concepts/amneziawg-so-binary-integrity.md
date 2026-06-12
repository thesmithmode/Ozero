---
title: "AmneziaWG SO Binary Integrity: Maven vs Reference"
aliases: [amneziawg-sha256, libam-go-binary-mismatch, warp-so-integrity]
tags: [warp, amneziawg, native, security, gotcha]
sources:
  - "daily/2026-05-08.md"
created: 2026-05-08
updated: 2026-06-12
---

# AmneziaWG SO Binary Integrity: Maven vs Reference

The `libam-go.so` from Maven artifact `com.zaneschepke:amneziawg-android:2.3.7` has a different SHA256 than the binary shipped in PORTAL_WG v1.4.3. Despite the same version label, the Maven binary (sha256=`cc119dbc`, 8589456 bytes) crashes on raw INI with I1 blob while the PORTAL_WG binary (sha256=`2ebc0ee9`, 8578640 bytes) works. All Kotlin-side fixes were futile because the native runtime itself was the root cause.

## Key Points

- `com.zaneschepke:amneziawg-android:2.3.7` Maven binary ≠ PORTAL_WG v1.4.3 binary — same version label, different SHA256, different behavior
- Maven binary crashes (SIGABRT in `runtime.gcWriteBarrier`) under identical INI config that PORTAL_WG binary handles correctly
- SHA256 verification of checked-in SO at build time is mandatory — sentinel test `AmneziaWgRuntimeBinaryTest` hardcodes expected `2EBC0EE9...`
- Migration approach: remove Maven dependency, copy 3 SO files from PORTAL_WG into `engine-warp/src/main/jniLibs/arm64-v8a/`
- `release.yml` step `assert libam-go.so SHA256` catches any regression where wrong binary gets packaged
- Binary integrity is not complete until the release APK is checked for both the expected SO hash and the expected native assets

## Details

### The Binary Mismatch Discovery

The WARP engine went through multiple fix cycles addressing: vanilla WG → AWG (`awgTurnOn=-1`) → Config.parse lossy (I1 field lost) → raw INI passthrough → SIGSEGV. Each iteration fixed a Kotlin-side symptom while the root cause — the Maven native runtime — remained unchanged. The breakthrough came from comparing SHA256 of the two binaries rather than inspecting Kotlin code.

The Maven artifact is built from the official amneziawg-go repository but may diverge from what reference Android apps ship. PORTAL_WG v1.4.3 ships a binary that handles the same INI config without crashing. Since both claim to be v2.3.7-compatible, the divergence is either from build flags, Go version, or unreleased patches applied by the PORTAL_WG maintainers.

### Migration to Checked-In SO

The correct approach is to check in the verified binary directly rather than depend on Maven:

1. Copy `libam-go.so`, `libam.so`, `libam-quick.so` from PORTAL_WG v1.4.3 into `engine-warp/src/main/jniLibs/arm64-v8a/`
2. Remove `implementation(libs.amneziawg.android)` from `engine-warp/build.gradle.kts`
3. Add `ndk { abiFilters += listOf("arm64-v8a") }` (PORTAL_WG has no x86_64 binary)
4. Add SHA256 sentinel test to catch any future binary substitution

The SHA256 sentinel test is the critical guard: `AmneziaWgRuntimeBinaryTest` reads the packaged SO and asserts its hash matches the known-good reference value. Any future change that swaps the binary (Maven dependency re-introduction, version bump) immediately fails CI.

The release workflow must repeat the check at artifact level. Source tests prove the repository contains the expected file; release assertions prove the generated APK actually packages the same file and did not fall back to a Maven/AAR artifact or omit native assets.

### Java Glue Required

Removing the Maven AAR removes the Java/Kotlin classes that the SO's `JNI_OnLoad` registers via `RegisterNatives`. These must be provided manually:
- `GoBackend` (7 native methods)
- `ProxyGoBackend` (6 native methods)
- `SocketProtector` (interface parameter type)

See [[concepts/amneziawg-jni-classpath-completeness]] for why all three are required.

## Related Concepts

- [[concepts/amneziawg-jni-classpath-completeness]] - Java classes that must accompany the SO
- [[concepts/amneziawg-relinker-loading-trap]] - Prior SO loading issue with the Maven AAR
- [[concepts/amnezia-wg-warp-migration]] - The WARP engine migration context
- [[connections/release-checks-beyond-ci]] - release.yml SO SHA256 assertion as independent gate
- [[connections/warp-portal-runtime-migration-proof-loop]] - The full migration proof chain from binary identity to APK packaging

## Sources

- [[daily/2026-05-08.md]] - Session 12:18: release workflow was updated to assert `libam-go.so` SHA256 and verify the APK contains the AmneziaWG SO files.

- [[daily/2026-05-08.md]] - Session 12:05: Maven libam-go.so sha256=cc119dbc vs PORTAL_WG sha256=2ebc0ee9; 8589456B vs 8578640B; all prior Kotlin fixes were futile — binary was root cause; migration to checked-in SO committed in session 12:18
