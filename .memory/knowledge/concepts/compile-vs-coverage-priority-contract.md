---
title: Compile blockers before coverage gates
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-05
---

## Summary
On `dev`, Kotlin compile breaks in owning modules must be treated as the first blocker ahead of JaCoCo gates, because they can suppress all downstream reporting and mislead root-cause analysis.

## Key Points
- Compile errors in tests or production sources block multiple modules at once, regardless of later coverage percentages.
- Compile failures in one module can hide branch/line deltas in unrelated modules by stopping test/report tasks earlier.
- Root-cause fixes should target concrete compiler/runtime API mismatches before gate tuning.
- Assertions and coverage work are valid only after the compile pipeline can emit stable artifacts.
- Coverage debt is still real after compile is green; it is a second-order action item, not the first one.

## Details
The log repeatedly records compile-root causes during CI triage: missing back-ticked identifiers or API mismatches in `singbox-subscription`, incorrect generic typing in `AtomicReference(null)` tests in `common-vpn`, and production/test drift for unsupported transport cases. These blocked test tasks before coverage verification could even run.

This pattern reinforced a sequencing rule: fix compile blockers first, then only then address coverage deficits in modules like `buildSrc`, `common-vpn`, and `shared-warp-settings`. The result was a clearer boundary between “cannot run tests due to compilation” and “tests run but fail gate thresholds.”

## Related Concepts
- [[concepts/dev-ci-root-cause-sequencing-loop]]
- [[concepts/ci-compile-coverage-style-blocker-sequencing]]
- [[concepts/ci-coverage-historical-debt-per-module-baseline-boundary]]
- [[concepts/ci-current-run-batch-failure-triage]]

## Sources
- Daily log entries 11:29, 12:28, 16:36, 20:50, 21:17 in [[daily/2026-06-04.md]] describe compile regressions discovered in parallel with coverage gaps.
- The same run sequence shows `common-vpn`, `buildSrc`, and `singbox-subscription` issues being handled before gate tuning.
