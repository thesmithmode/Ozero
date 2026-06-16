---
title: Local validation can reduce CI noise but terminal CI remains proof
sources:
  - daily/2026-06-04.md
created: 2026-06-09
updated: 2026-06-09
---
# Local validation can reduce CI noise but terminal CI remains proof

## Key Points
- Ozero normally treats GitHub Actions as the validation authority, but explicit user permission can allow local Gradle/lint runs for diagnosis.
- Local green unit or lint results are only preflight signals; they do not prove `dev` is green.
- Local JaCoCo red is actionable because it predicts a known CI failure and can justify delaying a push.
- Final acceptance still requires a fresh GitHub Actions run ending in terminal `success`.

## Details
The 2026-06-04 sessions created a narrow exception to the usual Ozero rule in [[concepts/local-gradle-validation-ban-ci-only]]. The user explicitly permitted local tests, and local Gradle runs were used to catch compile, unit, lint, and coverage failures before spending another CI iteration. That reduced noise, but it did not change the acceptance contract.

The non-obvious relationship is that local validation and terminal CI proof serve different roles. Local checks can prevent a known-bad push, especially when [[concepts/compile-vs-coverage-priority-contract]] shows compile or coverage blockers locally. But [[concepts/ci-terminal-success-fresh-run-contract]] remains the only proof that the remote workflow, job graph, artifacts, and gates are green on `dev`.

## Related Concepts
- [[concepts/local-gradle-validation-ban-ci-only]]
- [[concepts/ci-terminal-success-fresh-run-contract]]
- [[concepts/compile-vs-coverage-priority-contract]]
- [[concepts/ci-push-not-hypothesis-proof]]

## Sources
- [[daily/2026-06-04]]: sessions 21:17 and 21:35 record explicit user permission for local tests and the decision not to push while local JaCoCo gates were still red.
- [[daily/2026-06-04]]: session 20:50 records the acceptance rule that `dev` CI is green only after a completed GitHub Actions run with status `success`.
