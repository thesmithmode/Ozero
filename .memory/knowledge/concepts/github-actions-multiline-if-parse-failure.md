---
title: GitHub Actions multiline if parse failure
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# GitHub Actions multiline if parse failure

## Key Points
- GitHub Actions workflow syntax errors can fail a run before jobs are created.
- Multiline `${{ ... }}` expressions in job-level `if:` can break parsing.
- Required check names should not be renamed while fixing parser issues.
- A parser failure is a workflow-file defect, not a runtime CI regression.
- Editing workflow YAML must avoid accidental BOM or Unicode damage.

## Details

The 2026-05-29 CI investigation found a GitHub Actions run that failed before creating jobs. `gh run view --verbose` pointed to a workflow file issue, and the last successful run was tied to an earlier SHA before a `ci.yml` diff introduced multiline `${{ ... }}` job `if` expressions.

The fix direction was intentionally narrow: keep the `pull_request` trigger and required job identifiers, but rewrite each job `if` expression onto one line so the GitHub Actions parser can create jobs again. This avoided disrupting branch protection by renaming checks.

The session also captured a tooling trap: rewriting workflow files with `Set-Content -Encoding UTF8` can introduce a BOM or otherwise damage Unicode. For workflow repairs, prefer preserving source content and making minimal diffs from `HEAD`.

## Related Concepts
- [[concepts/github-actions-run-id-monitoring]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/ci-gradle-log-reading]]
- [[concepts/github-actions-artifact-node-major-upgrade]]

## Sources
- [[daily/2026-05-29]]: run `26654596918` failed before creating jobs and `gh run view --verbose` reported workflow syntax failure.
- [[daily/2026-05-29]]: the relevant diff introduced multiline `${{ ... }}` job `if` expressions.
- [[daily/2026-05-29]]: CI was fixed without changing job names or required checks, and a later run was green at `90b29355`.
- [[daily/2026-05-29]]: editing workflow YAML with `Set-Content -Encoding UTF8` was noted as risky due to BOM/Unicode damage.
