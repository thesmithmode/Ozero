---
title: Kotlin style failures can cascade into downstream CI jobs
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Kotlin style failures can cascade into downstream CI jobs
## Key Points
- A red `kotlin-style` job can mark multiple `needs`-dependent jobs as failed even when those jobs never start.
- In the 2026-06-03 `dev` run, the visible cascade included `buildSrc`, `common-vpn`, `core + common modules`, `engine-warp`, and `singbox + extra modules`.
- The first useful evidence is the style job's concrete `ktlint`/`detekt` findings, not the downstream failure summaries.
- Fix ordering matters: the style gate should be resolved before treating the later module failures as independent regressions.
## Details
The daily log shows a CI run where the initial blocking failure was a style job, and the rest of the pipeline inherited that failure through workflow dependencies. That pattern makes the run look broader than it is. The downstream jobs are often diagnostic noise unless the style gate has already been cleared.

The important operational consequence is that CI triage has to begin at the earliest real failure. Once `kotlin-style` is fixed, the next red signal may move to coverage or module-specific checks, which is why the cascade should be treated as an ordering problem rather than a set of unrelated module failures. This connects directly to [[ci-style-job-downstream-skip]] and [[ci-coverage-gate-artifact-trust-contract]].
## Related Concepts
- [[ci-style-job-downstream-skip]]
- [[ci-style-failure-hides-compile-regression]]
- [[ci-failure-batch-analysis-before-push]]
- [[ci-coverage-gate-artifact-trust-contract]]
## Sources
- `daily/2026-06-03.md`: recorded that `kotlin-style` blocked `buildSrc`, `common-vpn`, `core + common modules`, `engine-warp`, and `singbox + extra modules` on `dev`.
