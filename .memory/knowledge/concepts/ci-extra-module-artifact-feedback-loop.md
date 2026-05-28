---
title: CI extra module artifact feedback loop
sources:
  - [[daily/2026-05-28]]
created: 2026-05-28
updated: 2026-05-28
---
# CI extra module artifact feedback loop
## Key Points
- Enabling previously skipped module tests often exposes stale fakes, wrong Gradle task names, and branch coverage gaps.
- Each CI failure should be grounded in a concrete run ID, job, artifact, and XML or HTML report before changing code.
- The loop is useful only when monitoring follows the exact run to terminal status, as in [[concepts/github-actions-run-id-monitoring]].
- This extends [[concepts/ci-extra-modules-test-gate]] and [[concepts/ci-artifact-report-driven-debugging]] with the observed failure-fix-rerun workflow.
## Details
The 2026-05-28 CI work showed the lifecycle of a trustworthy extra-module gate. First, wiring mistakes caused a false failure, such as using `:shared-warp-settings:testDebugUnitTest` for a Kotlin library module that only has `:shared-warp-settings:test`. After task wiring was fixed, the new job exposed real latent failures in sing-box modules, MasterDNS tests, fake DAOs, fake SSH matching, and shared WARP settings coverage.

The effective debugging pattern was artifact-first. The assistant had to fetch GitHub Actions logs, test-report artifacts, and JaCoCo XML, then fix only what the artifacts proved. This avoided guessing from local assumptions and matched the repository rule that local tests are not run; the CI reports become the executable evidence source.
## Related Concepts
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/shared-warp-settings-branch-coverage]]
- [[concepts/masterdns-fake-ssh-specificity]]
## Sources
- [[daily/2026-05-28]]: the 19:02, 19:33, 19:39, and 19:51 sessions record the run IDs, wrong Gradle task, artifact analysis, stale fakes, and coverage gaps.
- [[daily/2026-05-28]]: the 18:24 session records the broader decision to wire previously skipped module tests instead of deleting tests.
