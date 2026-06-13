---
title: Kotlin style failures can cascade into downstream CI jobs
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-13
---
# Kotlin style failures can cascade into downstream CI jobs
## Key Points
- A red `kotlin-style` job can mark multiple `needs`-dependent jobs as failed even when those jobs never start.
- In the 2026-06-03 `dev` run, the visible cascade included `buildSrc`, `common-vpn`, `core + common modules`, `engine-warp`, and `singbox + extra modules`.
- The first useful evidence is the style job's concrete `ktlint`/`detekt` findings, not the downstream failure summaries.
- Fix ordering matters: the style gate should be resolved before treating the later module failures as independent regressions.
- Detekt findings can require structural fixes in test code as well as production code, including splitting large test classes.
## Details
The daily log shows a CI run where the initial blocking failure was a style job, and the rest of the pipeline inherited that failure through workflow dependencies. That pattern makes the run look broader than it is. The downstream jobs are often diagnostic noise unless the style gate has already been cleared.

The important operational consequence is that CI triage has to begin at the earliest real failure. Once `kotlin-style` is fixed, the next red signal may move to coverage or module-specific checks, which is why the cascade should be treated as an ordering problem rather than a set of unrelated module failures. This connects directly to [[ci-style-job-downstream-skip]] and [[ci-coverage-gate-artifact-trust-contract]].

The same log shows concrete style roots: a high-complexity `WarpIniBuilder.canonicalLabel`, a complex condition in `EngineSettingsRestartObserver`, and a `LargeClass` warning in an observer test. The chosen repair was structural: map lookup instead of branch-heavy canonicalization, named predicates instead of a long boolean expression, and splitting a monolithic test into focused classes. That keeps the quality gate useful instead of weakening `detekt`.
## Related Concepts
- [[ci-style-job-downstream-skip]]
- [[ci-style-failure-hides-compile-regression]]
- [[ci-failure-batch-analysis-before-push]]
- [[ci-coverage-gate-artifact-trust-contract]]
- [[detekt-object-function-extraction]]
## Sources
- `daily/2026-06-03.md`: recorded that `kotlin-style` blocked `buildSrc`, `common-vpn`, `core + common modules`, `engine-warp`, and `singbox + extra modules` on `dev`.
- `daily/2026-06-03.md`: identified detekt roots in `WarpIniBuilder`, `EngineSettingsRestartObserver`, and `EngineSettingsRestartObserverTest`, and chose structural fixes over suppressions.
