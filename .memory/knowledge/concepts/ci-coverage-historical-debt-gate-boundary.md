---
title: CI coverage historical debt gate boundary
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# CI coverage historical debt gate boundary

## Key Points
- A red coverage CI job can be a gate/workflow regression even when all test suites pass.
- Bundle-wide `jacocoTestCoverageVerification` is unsafe for historically low-coverage modules unless the covered surface is intentionally defined.
- Test-report artifacts can prove N>0 and zero failures while JaCoCo artifacts prove the actual red condition.
- Coverage boundary fixes must use artifacts and class-level ratios, not truncated job labels or guesses.
- This boundary connects to [[concepts/ci-coverage-gate-artifact-trust-contract]] and [[concepts/jacoco-testable-logic-exclude-boundary]].

## Details

On 2026-06-02, Ozero CI showed red test jobs for push and PR variants, but downloaded artifacts showed that the tests themselves were green. `common-vpn` reported 428 tests with zero failures, and core/singbox-related modules also had no test failures. The actual failure source was `jacocoTestCoverageVerification`, not business-code test failure.

This changes the diagnosis from "fix broken tests" to "fix the gate boundary." A module with about 38% line coverage and 33% branch coverage cannot satisfy a 95% full-bundle gate merely because current tests pass. The correct next step is to inspect workflow history and define the intended coverage baseline, measured surface, and exclusions before treating the red job as a production regression.

The artifact distinction matters because grouped CI labels can hide the real blocker. HTML reports, JaCoCo XML, and workflow logs are stronger evidence than job names when CI mixes compile, tests, N>0 discovery, report generation, verification, and upload in one job.

## Related Concepts

- [[concepts/ci-coverage-gate-artifact-trust-contract]]
- [[concepts/ci-artifact-report-driven-debugging]]
- [[concepts/jacoco-honest-coverage-gate-boundary]]
- [[connections/coverage-artifact-policy-feedback-loop]]

## Sources

- [[daily/2026-06-02]]: Session 18:53 recorded that test-report artifacts were green while `jacocoTestCoverageVerification` failed.
- [[daily/2026-06-02]]: Session 18:53 identified `common-vpn` as around 38% line and 33% branch coverage under a 95% gate.
- [[daily/2026-06-02]]: Session 18:53 decided to inspect `.github/workflows/ci.yml` and coverage gate history instead of fixing symptoms from truncated logs.
