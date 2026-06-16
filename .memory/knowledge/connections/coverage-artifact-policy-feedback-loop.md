---
title: Coverage artifact policy feedback loop
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Coverage artifact policy feedback loop

## Summary

Coverage CI failures are trustworthy only when coverage policy, artifact evidence, test discovery, and workflow structure are aligned. A red job may reflect gate policy drift rather than failing unit tests.

## Key Points

- App coverage diagnosis needs artifact evidence before any push.
- Exclude policy defines whether the 95% gate measures meaningful logic or hides it.
- A monolithic test job can obscure whether tests, reports, or verification failed.
- Full-bundle coverage verification can break historically low-coverage modules even when tests are green.

## Details

The 2026-06-02 sessions connected several existing CI lessons into one loop. A red `Tests - app` job was initially approached as a test failure or coverage mask problem, but artifacts later showed that tests themselves were green and `jacocoTestCoverageVerification` was the failing step. This changes the diagnosis from production behavior to coverage gate and workflow policy.

The same loop explains why using CI as a hypothesis checker is harmful. If the coverage boundary is too broad, it can include historical debt and fail regardless of the current change. If it is too narrow, it can exclude deterministic production logic and produce false green. If diagnostic sharding lacks N>0 gates, it can create another false green. Therefore, coverage fixes must start with actual artifacts and class-level evidence, then adjust either focused tests, narrow excludes, or workflow structure.

## Related Concepts

- [[concepts/ci-push-not-hypothesis-proof]]
- [[concepts/jacoco-testable-logic-exclude-boundary]]
- [[concepts/app-ci-job-diagnostic-sharding-nonzero-gate]]
- [[concepts/ci-coverage-gate-artifact-trust-contract]]

## Sources

- [[daily/2026-06-02]]: sessions required fresh `jacoco-app` artifacts and actual report XML/HTML before another push.
- [[daily/2026-06-02]]: artifact review found green tests while `jacocoTestCoverageVerification` failed.
- [[daily/2026-06-02]]: `common-vpn` had roughly 38% line and 33% branch coverage under a 95% full-bundle gate.
