---
title: CI failure batch analysis before push
sources:
  - daily/2026-06-01.md
created: 2026-06-02
updated: 2026-06-02
---

# CI failure batch analysis before push

## Key Points
- When a CI run has several failing layers, gather all failing jobs and artifacts before pushing another one-off fix.
- Early lint or test failures can prevent downstream coverage artifacts from existing, so the next fix must target the blocking layer first.
- `cancelled` runs cannot be treated as green even when upstream jobs were successful.
- Live logs may be unavailable for in-progress GitHub Actions jobs, so analysis must use job metadata and completed artifacts.

## Details

The 2026-06-01 work repeatedly exposed a CI layering problem: ktlint/detekt could skip downstream jobs, app unit failures could prevent `jacocoTestReport`, and cancelled runs could look promising without being valid terminal success. The user explicitly required batch analysis of all current failures instead of fixing one visible module and waiting for the next run to expose another blocker.

The practical workflow is to identify the current run ID, collect every failing job and available artifact, classify whether failures are style, compile, unit-test, coverage, or hang, and then prepare a batch that clears the known blockers together. If the app tests fail before JaCoCo, coverage gaps cannot be inferred from missing artifacts.

## Related Concepts
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/ci-style-failure-hides-compile-regression]]
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-grouped-job-failure-attribution]]

## Sources
- [[daily/2026-06-01]]: Sessions at 10:47 and 11:37 record the request to inspect all CI failures in one run and fix the full current batch.
- [[daily/2026-06-01]]: Sessions at 13:22 and 23:26 record cancelled-run and missing-JaCoCo-artifact lessons.
