---
title: CI style job downstream skip
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# CI style job downstream skip

## Key Points
- A red `ktlint + detekt` job can make downstream CI jobs appear skipped because they depend on `needs: kotlin-style`.
- The first failure to fix is the style job, not the skipped jobs.
- After style passes, compile failures may surface next, so CI diagnosis must continue past the first red check.
- This complements [[concepts/ci-job-dependency-masking]] and [[concepts/ci-gradle-log-reading]].

## Details

The 2026-05-29 CI review identified run `26661195213` as red because `kotlin-style` failed. Dependent jobs were skipped through `needs: kotlin-style`, which made the run look broader than the first actionable failure.

The same review predicted a follow-up compile blocker after style fixes: `MainViewModel.kt` still used `IpInfo`, but the import had been removed during exit-node resolver refactoring. This is a typical layered CI sequence: formatting/static analysis blocks compilation, then compilation exposes stale import or type errors.

## Related Concepts
- [[concepts/ci-job-dependency-masking]]
- [[concepts/ci-gradle-log-reading]]
- [[concepts/feature-deletion-orphaned-consumers]]
- [[connections/ci-false-green-vectors]]

## Sources
- [[daily/2026-05-29]] records that CI run `26661195213` failed in `ktlint + detekt` and downstream jobs were skipped because of `needs: kotlin-style`.
- [[daily/2026-05-29]] notes the expected next compile blocker: removed `IpInfo` import while the type remained in use.
