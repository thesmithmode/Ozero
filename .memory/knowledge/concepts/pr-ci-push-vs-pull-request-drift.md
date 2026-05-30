---
title: PR CI drift from green dev push
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# PR CI drift from green dev push
## Summary
A green `dev` push run does not guarantee that a `dev -> main` pull request run is green, because pull-request workflows can differ by conditions, target branch, secrets, matrix, and required checks.
## Key Points
- Diagnose PRs from their actual PR ref, such as `refs/pull/<id>/head`, not from an assumed local branch.
- "Tests did not start" can be a misleading reading of a grouped job that started and failed internally.
- Compare push and pull-request workflow conditions before assuming a code regression.
- Use concrete run IDs and job logs to distinguish skipped checks, failed Gradle tasks, and workflow graph differences.
## Details
PR #78 showed two diagnostic traps. First, the correct local evidence came from fetching `refs/pull/78/head` into `origin/pr/78`; a local `pr/78` branch could have been stale or unrelated. The range `origin/main..origin/pr/78` confirmed the expected 33 commits.

Second, the apparent "tests do not start" symptom was not a missing workflow graph. The relevant workflow jobs had started, but the grouped `Tests - singbox + extra modules` job failed internally, with failures in `engine-masterdns` and `singbox-subscription`. This required reading job-level Gradle results rather than assuming CI skipped the tests.

The later PR #79 report added the broader rule: even after `dev` push CI and wrapper validation were green, the `dev -> main` PR could still fail because pull-request workflows may execute with different branch filters, secrets, permissions, matrices, or required check naming.
## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-grouped-job-failure-attribution]]
- [[concepts/gradle-module-type-ci-task-selection]]
- [[concepts/ci-module-test-coverage-gap]]
## Sources
- [[daily/2026-05-30]]: PR #78 was fetched explicitly as `origin/pr/78`, and `origin/main..origin/pr/78` contained 33 commits.
- [[daily/2026-05-30]]: The suspected non-starting tests were reclassified as a real failure inside `Tests - singbox + extra modules`.
- [[daily/2026-05-30]]: Green `dev` push CI on `28ac6f72` did not settle PR #79 because PR workflow behavior can differ from push workflow behavior.
