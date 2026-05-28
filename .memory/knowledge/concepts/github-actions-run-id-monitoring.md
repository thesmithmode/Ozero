---
title: GitHub Actions run ID monitoring
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# GitHub Actions run ID monitoring

## Summary
CI monitoring must anchor to a concrete GitHub Actions run ID and poll until terminal status. Watching the latest similar run or relying on unstable jobs endpoints can miss failures and create false progress reports.

## Key Points
- `gh run watch` hit jobs endpoint 404 during the release workflow.
- A later CI run had already failed for about 10 minutes while monitoring was still assumed active.
- Run-level polling with `gh run view <run-id>` and check-runs is more reliable for terminal-status monitoring.
- Every push needs its own run ID captured and watched until `success`, `failure`, or another terminal conclusion.

## Details
On 2026-05-28, release and dev CI monitoring exposed two failure modes. First, `gh run watch` could fail because the jobs endpoint returned 404. Second, a monitoring loop failed operationally: the CI had already reached failure while the assistant still treated monitoring as active.

The operational rule is to capture the run ID produced by the relevant push or workflow and poll that exact run. This extends [[concepts/gh-run-list-watcher-race]] and [[concepts/ci-workflow-discipline]]: CI status must be tied to the commit and run being validated, not to the latest run returned by broad list queries.

## Related Concepts
- [[concepts/gh-run-list-watcher-race]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/ci-gradle-log-reading]]

## Sources
- [[daily/2026-05-28]]: `gh run watch` failed on jobs endpoint 404 and monitoring moved to run-level polling.
- [[daily/2026-05-28]]: User caught that CI had failed around 10 minutes earlier while monitoring was believed active.
- [[daily/2026-05-28]]: Decision was to monitor concrete run IDs to terminal status.
