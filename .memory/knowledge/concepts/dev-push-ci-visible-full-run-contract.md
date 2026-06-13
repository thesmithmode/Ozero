---
title: dev push must trigger visible full CI
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-13
---

# dev push must trigger visible full CI

## Key Points
- A green `workflow_dispatch` CI run is supporting evidence, but not the same GitHub UI signal as a push-associated CI run.
- For Ozero, full `CI` should run automatically on `dev` pushes while `main` remains protected from direct push workflow assumptions.
- If `dev` is excluded by `branches-ignore`, GitHub UI may show only partial checks such as Gradle Wrapper Validation.
- N>0 test gates must stay enabled because they expose modules with no real executed tests.
- CI monitoring should follow concrete run IDs to terminal status and treat jobs API/network failures separately from workflow failure.
- A visible push run must remain attached to the pushed SHA, because workflow-dispatch success can be invisible or misleading in the branch checks UI.

## Details

The 2026-05-31 CI loop found that manual full CI could be green while the user saw only one visible push check for `dev`. The root cause was workflow trigger configuration: full `CI` was not associated with the push event. The fix was to remove `dev` from push ignore rules so every `dev` push gets the full visible CI run.

The same session reinforced that N>0 gates are not ceremony. They caught a module without tests and prevented a false-green status. Because local tests are forbidden in Ozero, GitHub Actions run IDs, logs, and JUnit XML counts are the primary validation source.

The operational consequence is that workflow-trigger design is part of validation, not merely repository hygiene. If only `workflow_dispatch` runs the full suite, users reviewing `dev` in GitHub can see a partial push signal and reasonably conclude that full CI did not run. The push-associated run must carry the same meaningful gate names and nonzero test evidence.

The same day also showed why this matters during long stabilization loops. The team had to distinguish a green manually triggered run from the actual push-associated checks, and later CI diagnosis depended on identifying the exact run, SHA, failing job, and N>0 artifacts. A workflow that hides full CI behind manual dispatch breaks that evidence chain even when the underlying Gradle jobs are correct.

## Related Concepts
- [[concepts/dev-ci-workflow-dispatch-nonzero-tests-contract]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-module-test-coverage-gap]]
- [[connections/ci-false-green-vectors]]
- [[connections/multi-engine-stabilization-ci-review-loop]]

## Sources
- [[daily/2026-05-31]]: sessions 13:07, 14:03, 14:26, 15:03, and 15:25 describe manual dispatch visibility gaps, full `dev` push CI restoration, N>0 gate behavior, and run-level monitoring.
- [[daily/2026-05-31]]: Session 14:26 records that a green workflow-dispatch run did not satisfy the visible `dev` push CI expectation in GitHub UI.
- [[daily/2026-05-31]]: sessions 19:29 through 20:03 show that exact run/SHA/job attribution remained necessary when later `dev` CI fell red and GitHub API access was unreliable.
