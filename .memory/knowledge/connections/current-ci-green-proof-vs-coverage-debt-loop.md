---
title: Current CI Green Proof vs Coverage Debt Loop
sources:
  - [[daily/2026-06-04]]
created: 2026-06-04
updated: 2026-06-04
---
# Current CI Green Proof vs Coverage Debt Loop

## Key Points
- A stricter CI gate can reveal historical coverage debt and fresh compile/style failures in the same period.
- Local green unit tests are insufficient when GitHub Actions also runs JaCoCo verification.
- Pushing without a fresh terminal CI result only advances the loop if the pushed state had credible local or artifact evidence.
- Batch triage and explicit coverage boundaries prevent the work from degrading into random test additions.

## Details

The 2026-06-04 sessions connected two recurring problems: `dev` was only green when a fresh GitHub Actions run ended in `success`, while multiple modules had coverage deficits that could not be resolved by treating every red job as a product regression. Some failures were ordinary compile/style regressions, but others were historical coverage debt exposed by a broader or stricter gate.

This creates a loop: gather all failing jobs from the current run, fix compile/style/assertion blockers first, then use JaCoCo reports to decide whether to add targeted tests, simplify dead branches, or apply explicit per-module baselines for historical runtime surfaces. The loop ends only with terminal CI success, not with a commit or partial local pass.

## Related Concepts
- [[concepts/ci-current-run-batch-failure-triage]]
- [[concepts/jacoco-historical-debt-per-module-baseline-boundary]]
- [[concepts/ci-terminal-success-fresh-run-contract]]
- [[connections/coverage-artifact-policy-feedback-loop]]

## Sources
- [[daily/2026-06-04]]: sessions 17:40, 17:53, 21:17, 21:35 and 23:20 connect stricter CI gates, historical coverage debt, local unit-test limits, and the requirement for terminal green CI.
