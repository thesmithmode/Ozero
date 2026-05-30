---
title: PR CI merge ref flaky test feedback loop
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# PR CI merge ref flaky test feedback loop

## Summary
A green `dev` push run and a red `dev -> main` PR run can both be correct when PR CI tests a synthetic merge ref and exposes a flaky asynchronous unit test before coverage artifacts are produced.

## Key Points
- Push CI on `dev` tests the branch head, while PR CI may test a synthetic merge commit against current `main`.
- A single flaky async test can make the PR look like a broader merge or coverage problem.
- Missing coverage artifacts with present test reports usually means unit tests failed before JaCoCo verification.
- Diagnosis should anchor to concrete run IDs, job names, and the tested SHA or merge ref.

## Details
On 2026-05-30, `dev` push CI was green on `28ac6f72`, while PR #79 failed on a synthetic merge ref. The failing job was narrowed to `Tests — engine-urnetwork + engine-byedpi`, then to a single ByeDPI wedged-lane test. That connected a CI topology difference from [[concepts/pr-ci-push-vs-pull-request-drift]] with the race described in [[concepts/byedpi-proxy-lane-test-race-synchronization]].

This pattern matters because the first symptom can be misleading. A PR run can look like skipped tests, missing coverage, or target-branch drift, but the root cause may be a real test failure that stops JaCoCo from writing artifacts. The diagnostic loop should follow [[concepts/github-actions-run-id-monitoring]] and [[concepts/ci-artifact-driven-extra-module-debugging]]: identify the exact run, exact job, exact SHA/ref, and then inspect test reports before changing workflow conditions.

Once the failure is localized, the repair should address the behavioral test race rather than weakening CI. In this case, the test needed synchronization with the proxy coroutine before verification; production `ByeDpiEngine` did not need a speculative change.

## Related Concepts
- [[concepts/pr-ci-push-vs-pull-request-drift]]
- [[concepts/byedpi-proxy-lane-test-race-synchronization]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-artifact-driven-extra-module-debugging]]

## Sources
- [[daily/2026-05-30]]: PR #79 tested a synthetic merge ref and failed while the prior `dev` push run was green.
- [[daily/2026-05-30]]: The failed PR job had unit test reports but no coverage artifacts, indicating test failure before JaCoCo rather than a coverage-only issue.
