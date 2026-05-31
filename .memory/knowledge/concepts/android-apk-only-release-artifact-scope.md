---
title: Android APK-only release artifact scope
sources:
  - [[daily/2026-05-28]]
created: 2026-05-31
updated: 2026-05-31
---

# Android APK-only release artifact scope

## Summary

When the active release goal is Android APK stability, desktop and cross-platform artifacts should be frozen out of the release workflow so unrelated surfaces do not expand the release risk.

## Key Points

- The user explicitly froze Windows, Linux, and macOS work while fixing Android APK regressions.
- The release workflow was reduced to Android APK output and published `Ozero-v1.0.5.apk`.
- Desktop code was not analyzed for the APK-focused regression release.
- Desktop test reporting can remain useful, but desktop artifact generation should not be part of an APK-only release path.
- This is related to [[concepts/android-apk-only-release-scope]] and [[concepts/release-workflow-apk-only-artifact-pruning]].

## Details

During the later May 28 regression work, the target narrowed from general release cleanup to a release-ready Android APK fix for ByeDPI, FPTN, URnetwork, and sing-box. The user explicitly excluded Windows, Linux, and macOS builds from the scope and requested that the release workflow publish only Android APK artifacts.

The resulting `v1.0.5` workflow removed desktop release jobs and published the APK artifact. This was not a statement that desktop tests or code were unimportant; it was a release-scope decision to prevent non-APK artifacts from influencing an Android runtime regression release.

## Related Concepts

- [[concepts/android-apk-only-release-scope]]
- [[concepts/release-workflow-apk-only-artifact-pruning]]
- [[connections/android-apk-scope-vs-runtime-proof]]
- [[concepts/release-runtime-regression-sentinels]]

## Sources

- [[daily/2026-05-28]]: Session 20:59 recorded the user freezing non-APK surfaces and limiting work to Android/APK scope.
- [[daily/2026-05-28]]: Sessions 22:00 and 22:30 recorded the APK-only release workflow and the published `Ozero-v1.0.5.apk`.
