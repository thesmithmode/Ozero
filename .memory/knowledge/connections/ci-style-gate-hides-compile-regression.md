---
title: CI style gate hides compile regression
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# CI style gate hides compile regression

## Key Points
- A style gate can be the first red CI signal while a separate compile regression waits behind it.
- Downstream skipped jobs should not be treated as clean or irrelevant.
- Fixing style must be followed by another CI run to reveal hidden compile/test failures.
- This links [[concepts/ci-style-job-downstream-skip]] with [[concepts/strategy-extraction-import-retention]].

## Details

The 2026-05-29 review connected two facts from the same `dev` run: `ktlint + detekt` failed first, and a likely compile failure in `MainViewModel.kt` was already visible from code review. Because downstream jobs depended on `kotlin-style`, the compile issue did not appear as a CI job failure yet.

This pattern matters for triage. A red style job is actionable, but it is not proof that the rest of the branch is healthy. After style is fixed, the same SHA family needs another full run to expose compile, unit-test, and module-level failures.

## Related Concepts
- [[concepts/ci-style-job-downstream-skip]]
- [[concepts/strategy-extraction-import-retention]]
- [[concepts/ci-job-dependency-masking]]
- [[connections/ci-false-green-vectors]]

## Sources
- [[daily/2026-05-29]] records CI run `26661195213` failing at `ktlint + detekt` with downstream jobs skipped through `needs`.
- [[daily/2026-05-29]] records the separate pending compile blocker caused by the removed `IpInfo` import.
