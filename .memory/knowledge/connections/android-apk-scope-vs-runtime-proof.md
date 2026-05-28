---
title: Android APK scope vs runtime proof
sources:
  - [[daily/2026-05-28]]
created: 2026-05-28
updated: 2026-05-28
---
# Android APK scope vs runtime proof
## Key Points
- Narrowing release scope to Android APK reduces unrelated artifact noise but raises the bar for Android runtime evidence.
- Desktop or cross-platform release success cannot substitute for same-process engine recovery proof.
- CI gates must stay focused on APK-relevant regressions while still preserving useful nonblocking signals.
- This connection links [[concepts/android-apk-only-release-scope]], [[concepts/engine-poisoned-state-recovery-proof]], and [[concepts/release-runtime-regression-sentinels]].
## Details
The daily log reveals a non-obvious relationship between scope control and proof quality. Freezing Windows, Linux, macOS, and desktop work prevents release effort from drifting, but it also removes broad release completion as a confidence signal. For an Android-only release, the meaningful evidence is Android APK behavior and Android engine recovery.

This is why green CI, desktop reports, or a successful clean WARP start after process restart are insufficient. The APK release must prove that the same Android process can recover from engine failure and continue through the switching path. Targeted runtime sentinels become the bridge between the narrowed release scope and the user-visible release contract.
## Related Concepts
- [[concepts/android-apk-only-release-scope]]
- [[concepts/engine-poisoned-state-recovery-proof]]
- [[concepts/release-runtime-regression-sentinels]]
- [[connections/release-regression-ci-vs-runtime-proof]]
## Sources
- [[daily/2026-05-28]]: the 20:59 session combines Android-only release scope with the requirement to prove engine recovery scenarios.
- [[daily/2026-05-28]]: the 20:31 through 20:39 sessions explain why WARP after process restart is not adequate proof for poisoned-state recovery.
