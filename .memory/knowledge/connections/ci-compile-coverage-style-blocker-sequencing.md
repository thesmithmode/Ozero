---
title: CI compile, style, and coverage blockers surface in sequence
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# CI compile, style, and coverage blockers surface in sequence

## Key Points
- A single red `dev` period can contain style errors, compile errors, assertion failures, and coverage gaps.
- Compile errors can prevent coverage artifacts, so missing reports do not automatically mean a coverage-only failure.
- Fixing `ktlint` or compile blockers often reveals the next JaCoCo or assertion blocker.
- Batch-reading all failing jobs reduces loops caused by chasing only the first visible symptom.

## Details

The 2026-06-04 CI work showed a repeating pattern: `ktlint + detekt` errors were fixed, then `buildSrc` assertion failures appeared, then Android module compile errors and JaCoCo deficits became visible. The same red period involved `buildSrc`, `common-vpn`, `engine-warp`, `singbox + extra modules`, and shared modules.

The non-obvious relationship is that these are not interchangeable failure types. Style and compile blockers can hide test and coverage results, while strict coverage gates can make real progress look like a loop. This links [[concepts/ci-style-failure-hides-compile-regression]], [[concepts/ci-coverage-historical-debt-gate-boundary]], and [[concepts/ci-terminal-success-fresh-run-contract]] into one triage pattern.

## Related Concepts
- [[concepts/ci-style-failure-hides-compile-regression]]
- [[concepts/ci-coverage-historical-debt-gate-boundary]]
- [[concepts/ci-terminal-success-fresh-run-contract]]

## Sources
- [[daily/2026-06-04.md]] recorded that CI moved from style failures to `buildSrc` tests, Android compile errors, and coverage deficits.
- [[daily/2026-06-04.md]] recorded the decision to aggregate failing jobs and read fresh logs instead of treating old blockers as current truth.
