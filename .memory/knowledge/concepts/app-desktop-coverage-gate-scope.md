---
title: app-desktop coverage gate scope
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# app-desktop coverage gate scope

## Key Points
- `app-desktop` tests should run in CI, but its coverage verification was removed from the Android release gate.
- The desktop Compose surface has separate coverage debt and is not part of the Android APK release criteria.
- Removing coverage verification for that module was scoped gate ownership, not deletion of tests.
- The distinction complements [[concepts/ci-extra-modules-test-gate]] and [[concepts/release-process]].

## Details

When extra modules were added to CI on 2026-05-28, `app-desktop` was included for test execution and reporting. Its coverage verification, however, was not kept as a blocking release gate because the module represents a separate desktop surface with existing Compose UI coverage debt. Blocking Android release readiness on that debt would mix ownership boundaries.

The accepted pattern is to keep tests visible and reported while making coverage gates match the release surface they protect. This differs from false-green behavior: the tests still run, but the Android release pipeline does not claim desktop coverage as an APK release invariant.

## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/release-process]]
- [[connections/ci-and-release-gating]]

## Sources
- [[daily/2026-05-28]] records the decision to keep `app-desktop` tests and reports but remove coverage verification from the new extra-modules release gate.
- [[daily/2026-05-28]] records the rationale: desktop Compose UI is a separate surface with current coverage debt and should not block Android release gating.
