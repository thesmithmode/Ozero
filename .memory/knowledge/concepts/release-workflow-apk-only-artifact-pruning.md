---
title: Android APK-only release artifact pruning
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# Android APK-only release artifact pruning

## Key Points
- When the user freezes non-APK work, release workflow changes must remove Windows, Linux, macOS, and desktop artifact paths from the release surface.
- APK-only scope is a release-delivery constraint, not permission to ignore Android runtime regression proof.
- The published asset must be verified as the expected APK, for example `Ozero-v1.0.5.apk`, rather than inferred from workflow success.
- Desktop modules can still have CI visibility, but desktop packaging must not re-enter an Android-only release path.

## Details

The 2026-05-28 release work narrowed the release objective to the Android APK after the user explicitly froze Windows, Linux, macOS, and other non-APK analysis. The release workflow was therefore reduced to Android APK publication, and release `v1.0.5` was verified through the published APK asset.

This scope decision is separate from test scope. Android-only delivery can coexist with broader CI diagnostics for shared or previously skipped modules, but non-APK packaging jobs should not participate in the release workflow while the product goal is Android APK recovery.

## Related Concepts
- [[concepts/android-apk-only-release-scope]]
- [[connections/android-apk-scope-vs-runtime-proof]]
- [[concepts/github-release-asset-name-verification]]
- [[concepts/release-runtime-scenario-checklist]]

## Sources
- [[daily/2026-05-28]] records the user freezing non-APK work and requiring the release workflow to publish only the Android APK.
- [[daily/2026-05-28]] records that desktop/windows/linux/macOS release jobs were removed and release `v1.0.5` published `Ozero-v1.0.5.apk`.
