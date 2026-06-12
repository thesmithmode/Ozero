---
title: "AmneziaWG Artifact Identity Boundary"
aliases: [amneziawg-artifact-boundary, zaneschepke-amneziawg-boundary, libamneziawg-aar-boundary]
tags: [warp, amneziawg, dependencies, native, gotcha]
sources:
  - "daily/2026-05-06.md"
created: 2026-06-12
updated: 2026-06-12
---

# AmneziaWG Artifact Identity Boundary

AmneziaWG integration work must distinguish Maven/Gradle wrapper artifacts, gomobile AARs, and reference-application native binaries. Similar names do not imply identical APIs or packaged libraries. During WARP `awgTurnOn=-1` diagnosis, `com.zaneschepke:amneziawg-android:2.3.7` was shown to provide `org.amnezia.awg.GoBackend`, while `libamneziawg.aar` contained gomobile bindings such as `awgbind.Awgbind` and was not part of the `engine-warp` build.

## Key Points

- `com.zaneschepke:amneziawg-android` and `libamneziawg.aar` are different artifacts with different Java/Kotlin surfaces.
- `engine-warp` depended on the `GoBackend` surface, not the gomobile `awgbind.Awgbind` surface.
- `ozeroBinaries` being applied only to `engine-byedpi` meant `libamneziawg.aar` was not automatically included in the WARP build.
- Version changes must be checked against native API signatures before editing Gradle coordinates.
- PORTAL WG reference binaries and Maven binaries can share names or versions while still differing in behavior.

## Details

The 2026-05-06 investigation found that `com.zaneschepke:amneziawg-android:2.3.7` was not present in the local Gradle cache and was not available from JitPack under the current repository setup. That made its actual resolution path an evidence item, not an assumption. The same session also ruled out `libamneziawg.aar` as a substitute because it exposes a different gomobile API and because the binary plugin wiring did not include it in `engine-warp`.

This boundary prevents a common native-integration mistake: replacing one artifact with another because both contain AmneziaWG-related names. For WARP, the call site needed the 4-parameter `GoBackend.awgTurnOn(name, fd, config, uapiPath)` API. A downgrade to a version with a 3-parameter API produced compile failure, proving that dependency identity must be checked at the symbol/signature level before treating a version or artifact swap as a fix.

## Related Concepts

- [[concepts/amnezia-wg-warp-migration]] - Later WARP migration work documents the broader AmneziaWG integration and binary replacement path.
- [[concepts/amneziawg-jni-classpath-completeness]] - Native registration depends on the exact Java classpath expected by the bundled SO.
- [[concepts/amneziawg-so-binary-integrity]] - Different AmneziaWG binaries can share labels but differ in SHA256 and runtime behavior.
- [[concepts/amneziawg-turnon-minus-one]] - Artifact swaps were considered during `awgTurnOn=-1` diagnosis and rejected without API evidence.

## Sources

- [[daily/2026-05-06.md]] - Session 09:30: `com.zaneschepke:amneziawg-android` v2.3.7 was not in local Gradle cache or JitPack, `libamneziawg.aar` was identified as a different gomobile artifact, and `ozeroBinaries` was only applied to `engine-byedpi`.
- [[daily/2026-05-06.md]] - Session 10:18: downgrade to v1.2.2 was rejected because that API exposes a 3-parameter `awgTurnOn`, while Ozero's call sites require the v2.3.7 4-parameter signature.
