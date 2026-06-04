---
title: Kotlin style blank-line root cause in dev CI
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# Kotlin style blank-line root cause in dev CI
## Key Points
- The `kotlin-style` failure in the 2026-06-04 log came from `ktlint` formatting violations, not from runtime logic.
- Two concrete issues were identified: an extra blank line before a closing brace in `WarpIniBuilder.kt` and a trailing blank line at the end of `EngineSettingsRestartObserverTestBase.kt`.
- The failure sat early in the CI chain and masked later jobs until the style gate was cleared.
- The fix was intentionally minimal: remove the offending blank lines and keep the code path unchanged.
## Details
The daily log records the first red job as `kotlin-style`, with the corresponding run and job identifiers captured in the notes. The actionable signal was a pair of blank-line violations, which made the root cause formatting-only rather than behavioral. That matters because the next CI signal could only be trusted after the style gate stopped short-circuiting the rest of the pipeline.

This kind of failure is related to [[dev-ci-root-cause-sequencing-loop]] because the visible blocker was not the only problem in the run; it was simply the first one the pipeline exposed. It also belongs in the same family as [[ci-style-failure-hides-compile-regression]], where a formatting gate can prevent later jobs from surfacing.
## Related Concepts
- [[dev-ci-root-cause-sequencing-loop]]
- [[ci-style-failure-hides-compile-regression]]
- [[dev-ci-kotlin-style-cascade]]
## Sources
- [[daily/2026-06-04.md]]
