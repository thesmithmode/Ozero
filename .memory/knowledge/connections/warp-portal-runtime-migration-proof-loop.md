---
title: "WARP PORTAL Runtime Migration Proof Loop"
aliases: [warp-portal-runtime-proof, amneziawg-so-migration-loop, warp-native-supply-chain-loop]
tags: [warp, amneziawg, native, ci, release]
sources:
  - "daily/2026-05-08.md"
created: 2026-06-12
updated: 2026-06-12
---

# WARP PORTAL Runtime Migration Proof Loop

The WARP migration from Maven AmneziaWG to checked-in PORTAL_WG native libraries required a proof loop across binary identity, Java glue completeness, git tracking, and release packaging. Any one layer can make the migration appear complete while the APK still ships the wrong or unloadable runtime.

## Key Points

- Binary identity is the first proof: the packaged `libam-go.so` must match the known-good PORTAL_WG SHA256, not just the Maven version label.
- Java glue completeness is the second proof: `GoBackend`, `ProxyGoBackend`, and `SocketProtector` must be present because `JNI_OnLoad` registers them eagerly.
- Git tracking is a hidden proof layer: a root `*.so` ignore rule can silently prevent `jniLibs` from entering the commit.
- Release packaging must assert both SHA256 and APK contents so CI catches dependency reintroduction or missing native assets.

## Details

The daily log shows why native runtime swaps cannot be validated by source-level review alone. Ozero had already fixed Kotlin-side symptoms around AWG config parsing and raw INI passthrough, but the crash persisted until the Maven `libam-go.so` was compared against the PORTAL_WG binary. That established that the artifact itself, not the Kotlin wrapper, was the root of the raw INI crash.

After the binary was copied into `engine-warp/src/main/jniLibs/arm64-v8a/`, the migration still needed additional guards. Removing the Maven AAR removed Java classes the native library expects during `RegisterNatives`; omitting those classes would fail at load time. A global `*.so` ignore rule could also make the repository look clean while the binary was not tracked. The release workflow therefore needed independent assertions over the packaged SO hash and APK contents.

## Related Concepts

- [[concepts/amneziawg-so-binary-integrity]] - The binary mismatch that made the migration necessary.
- [[concepts/amneziawg-jni-classpath-completeness]] - The Java classpath requirement introduced by removing the Maven AAR.
- [[concepts/gitignore-jnilibs-conflict]] - The hidden git tracking failure mode for checked-in SO files.
- [[connections/release-checks-beyond-ci]] - Release gates must verify shipped artifacts, not only source-level success.

## Sources

- [[daily/2026-05-08.md]] - Sessions 12:05 and 12:18: Maven and PORTAL_WG SO hashes differed; migration checked in three SO files, added Java glue, removed Maven dependency, fixed `.gitignore`, and added SHA256/APK release assertions.
