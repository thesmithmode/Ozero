---
title: CI push is not a hypothesis proof
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# CI push is not a hypothesis proof

## Summary

Ozero CI runs must not be used as a trial-and-error mechanism for coverage fixes. A push is acceptable only after the proposed fix is grounded in current artifacts and the real Gradle/JaCoCo matching behavior.

## Key Points

- A new CI run is not valid evidence that a coverage hypothesis is correct.
- Manual simulation of JaCoCo XML can diverge from Gradle exclude matching.
- Fresh artifacts and actual report data are the primary proof source before pushing.
- Repeated amend-and-push loops without artifact proof create noisy red CI and hide root cause.

## Details

The 2026-06-02 sessions established that app coverage fixes must be proven before another `dev` push. The failed loop came from relying on manual coverage XML simulation and assumed exclude behavior, then using GitHub Actions as the verifier. This violated the project rule that CI is a validation gate, not an exploratory test bench.

The required proof chain is artifact first: download the latest `jacoco-app` or relevant test-report artifact, inspect `jacocoTestReport.xml` or HTML reports, map missed and covered classes to actual compiled class names, and only then change tests or excludes. The rule is especially important for `jacocoTestCoverageVerification`, because small mask changes can move both covered and missed classes and produce counterintuitive ratios.

## Related Concepts

- [[concepts/ci-failure-batch-analysis-before-push]]
- [[concepts/ci-snapshot-artifact-failure-grounding]]
- [[concepts/jacoco-exclude-evidence-boundary]]
- [[connections/coverage-gate-vs-test-harness-validity-loop]]

## Sources

- [[daily/2026-06-02]]: user rejected starting CI without proof that coverage would reach at least 95%.
- [[daily/2026-06-02]]: sessions recorded that manual JaCoCo XML simulation did not prove real Gradle mask matching.
- [[daily/2026-06-02]]: action items required fresh artifacts and actual XML/HTML reports before the next push.
