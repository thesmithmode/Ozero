---
title: Release runtime regression sentinels
sources:
  - [[daily/2026-05-28]]
created: 2026-05-28
updated: 2026-05-28
---
# Release runtime regression sentinels
## Key Points
- Release readiness needs sentinels for the exact runtime scenarios that regressed, not only broad unit coverage.
- Required scenarios include stuck ByeDPI stop recovery, FPTN dead-server fallback, URnetwork `CONNECTED` with zero peers, and sing-box unsupported transport filtering.
- These sentinels complement [[concepts/release-runtime-scenario-checklist]] and reduce false confidence from [[connections/release-regression-ci-vs-runtime-proof]].
- The sentinel set should be tied to user-visible APK behavior when [[concepts/android-apk-only-release-scope]] is active.
## Details
After the user asked whether CI coverage was strong enough, the session shifted from general green-CI confidence to targeted regression sentinels. The useful tests are the ones that encode known failure modes: ByeDPI native stop must not poison the next engine, FPTN should not treat the first dead endpoint as final failure, URnetwork must not fail readiness solely because peer count is zero while SDK status is connected, and sing-box config builders must reject unsupported subscription transports before runtime.

These sentinels are not a replacement for device runtime validation. They are a CI guardrail against reintroducing the same architecture regressions and against tests that exist but are not wired into GitHub Actions. Their value depends on being part of an actual CI job with nonzero test execution, as described in [[concepts/ci-extra-modules-test-gate]].
## Related Concepts
- [[concepts/release-runtime-scenario-checklist]]
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/urnetwork-readiness-connectionstatus]]
- [[concepts/singbox-autochain-validator-parity]]
## Sources
- [[daily/2026-05-28]]: the 18:24 session records the decision to add regression tests for URnetwork `awaitReady` edge cases and sing-box auto-chain invalid server sets.
- [[daily/2026-05-28]]: the 20:59 session records the required sentinel scenarios for ByeDPI, FPTN, URnetwork, and sing-box before claiming release readiness.
