---
title: Android APK only release scope
sources:
  - [[daily/2026-05-28]]
created: 2026-05-28
updated: 2026-05-28
---
# Android APK only release scope
## Key Points
- When the active release goal is Android APK recovery, Windows, Linux, macOS, and desktop artifact analysis stay out of scope unless the user explicitly reopens them.
- Release workflow changes must preserve the Android APK path first and avoid accidental cross-platform churn in [[concepts/release-process]].
- Desktop tests can still run as signal, but desktop coverage debt should not block the Android release gate; this aligns with [[concepts/app-desktop-coverage-gate-scope]].
- Scope freeze is especially important during engine regressions because unrelated release artifacts increase noise while debugging [[concepts/engine-failure-recovery-isolation]].
## Details
During the 2026-05-28 regression session, the user explicitly froze all non-APK work: Windows, Linux, macOS builds and code were not to be analyzed, and the release workflow goal was to publish only the Android APK. This changed the release target from a broad multi-artifact validation problem into an Android runtime recovery problem.

This scope rule does not mean desktop or cross-platform code is permanently irrelevant. It means the active acceptance contract should be limited to Android APK behavior, Android CI gates, and the Android release artifact until the user reopens other platforms. The rule prevents a false sense of progress from fixing unrelated release warnings while ByeDPI, FPTN, URnetwork, sing-box, or switching recovery remain broken.
## Related Concepts
- [[concepts/release-process]]
- [[concepts/app-desktop-coverage-gate-scope]]
- [[concepts/release-runtime-scenario-checklist]]
- [[concepts/engine-failure-recovery-isolation]]
## Sources
- [[daily/2026-05-28]]: the 20:59 session records that the user froze non-APK work and required the release workflow to produce only the Android APK.
- [[daily/2026-05-28]]: the 20:02 and 20:10 sessions record that desktop coverage verification was removed from the release gate while tests and reports stayed useful as secondary signal.
