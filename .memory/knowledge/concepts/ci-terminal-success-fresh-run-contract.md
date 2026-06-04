---
title: CI success requires a fresh terminal run
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# CI success requires a fresh terminal run

## Key Points
- A stale green run does not prove the current `dev` state is green.
- A commit or push is not completion; completion requires a fresh GitHub Actions run ending in `success`.
- When a run remains red, read the current failing jobs instead of reusing old logs or memory notes.
- Terminal status matters because one fixed job can expose the next independent blocker.

## Details

Across the 2026-06-04 sessions, multiple intermediate assumptions were invalidated by newer red `dev` runs. The user clarified the acceptance condition: `dev` CI is green only after a completed GitHub Actions run reports `success`. Old successful runs, partial job success, and pushed commits were not valid proof.

This contract connects [[concepts/github-actions-run-id-monitoring]], [[concepts/dev-ci-root-cause-sequencing-loop]], and [[concepts/ci-failure-batch-analysis-before-push]]. CI repair should anchor to a concrete fresh run, aggregate all failing jobs, fix current root causes, push, and then wait for the next terminal status.

## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/dev-ci-root-cause-sequencing-loop]]
- [[concepts/ci-failure-batch-analysis-before-push]]

## Sources
- [[daily/2026-06-04.md]] recorded that stale green runs were rejected as proof of current `dev` health.
- [[daily/2026-06-04.md]] recorded the acceptance condition that `dev` CI is green only after a fresh completed run with `success`.
