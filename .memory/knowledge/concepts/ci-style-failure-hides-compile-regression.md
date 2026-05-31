---
title: Style CI failures can hide compile regressions
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- A red `ktlint + detekt` job can skip downstream jobs and hide compile errors.
- After fixing style, rerun CI and expect additional blockers to surface.
- Removed imports after refactors must be checked against remaining type usage.
- Mojibake in source and tests weakens diagnostics and should be cleaned with scoped edits.

## Details
The 2026-05-29 CI review recorded a concrete failure mode: a red style job stopped downstream jobs, so compile failures were not visible yet. The review predicted a later compile blocker because `MainViewModel.kt` had lost the `IpInfo` import while the type was still used.

This is a CI diagnosis pattern, not only a style issue. The first red job must be fixed, but the engineer should not treat that as proof of success until downstream compile, test, and coverage jobs actually run. Source mojibake observed during the same cycle also reduces diagnostic quality and should be handled without broad unrelated rewrites.

## Related Concepts
- [[concepts/ci-style-job-downstream-skip]]
- [[connections/ci-style-gate-hides-compile-regression]]
- [[concepts/strategy-extraction-import-retention]]
- [[concepts/source-mojibake-diagnostics-risk]]

## Sources
- [[daily/2026-05-29]] records that CI run `26661195213` failed at `ktlint + detekt` and skipped downstream jobs.
- [[daily/2026-05-29]] records the expected compile blocker from removing `IpInfo` import while the type remained used.
- [[daily/2026-05-29]] records mojibake in changed Kotlin source/test files as a diagnostics risk.
