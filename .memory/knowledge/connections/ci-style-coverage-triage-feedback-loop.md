---
title: Style-gate cascades and coverage fixes form one CI triage loop
sources:
  - daily/2026-06-03.md
created: 2026-06-04
updated: 2026-06-04
---
# Style-gate cascades and coverage fixes form one CI triage loop
## Key Points
- A red `kotlin-style` job can hide the next real signal by cascading failures into downstream jobs.
- Once the style gate is green, the next blocker may be a coverage gap in a different module.
- The 2026-06-03 run moved from style-dominated failures to a `:singbox-subscription` branch-coverage failure.
- CI triage needs a strict order: earliest real failure first, then the next gate after that is cleared.
## Details
The daily log shows two different failure modes in sequence. First, the style gate blocked multiple jobs through dependency chaining. After the style issues were addressed, the visible red signal shifted to branch coverage in `:singbox-subscription`. Those are not separate mysteries; they are stages of one triage loop.

This matters because the wrong order wastes effort. If the cascade is treated as a module regression, the team chases downstream noise. If the coverage failure is treated before the style gate is cleared, the next real blocker is hidden. The relationship is anchored by [[dev-ci-kotlin-style-cascade]] and [[singbox-subscription-branch-coverage-edges]].
## Related Concepts
- [[dev-ci-kotlin-style-cascade]]
- [[singbox-subscription-branch-coverage-edges]]
- [[ci-coverage-gate-artifact-trust-contract]]
- [[ci-failure-batch-analysis-before-push]]
## Sources
- `daily/2026-06-03.md`: documented a `kotlin-style` cascade across multiple jobs and a later `:singbox-subscription` branch-coverage failure.
