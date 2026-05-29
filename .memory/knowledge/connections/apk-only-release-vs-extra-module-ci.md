---
title: APK-only release scope versus extra-module CI
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# APK-only release scope versus extra-module CI

## Key Points
- APK-only release scope controls published artifacts, not all validation jobs.
- Extra-module CI can remain useful because Android regressions may live in shared libraries or engine modules outside `app`.
- Desktop packaging should be frozen for an APK-only release, while desktop tests may still provide diagnostic signal when they are not release gates.
- Coverage gates must match release ownership: Android/shared release-critical modules can gate, while unrelated desktop coverage debt should not block APK publication.

## Details

The 2026-05-28 sessions created a useful distinction. The release workflow was pruned to Android APK-only because the user froze non-APK delivery. At the same time, CI was expanded to run previously skipped module tests because those modules could hide Android engine regressions.

This means artifact scope and validation scope are not identical. APK-only release removes desktop publish jobs, but it does not justify a false-green CI setup where engine or shared module tests exist but never start. Conversely, app-desktop coverage debt should not become an Android release blocker when it is outside the APK delivery contract.

## Related Concepts
- [[concepts/release-workflow-apk-only-artifact-pruning]]
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/app-desktop-coverage-gate-scope]]
- [[concepts/android-apk-only-release-scope]]

## Sources
- [[daily/2026-05-28]] records that desktop/windows/linux/macOS release jobs were removed for the Android APK-only release.
- [[daily/2026-05-28]] records that extra-module tests were added to CI and that app-desktop coverage verification was not kept as an Android release gate.
