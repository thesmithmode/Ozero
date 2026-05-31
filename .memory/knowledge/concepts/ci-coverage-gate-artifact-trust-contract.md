---
title: CI coverage gate artifact trust contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# CI coverage gate artifact trust contract

## Summary
Coverage gates are trustworthy only when they consume the actual test execution artifacts for every gated module and are paired with N>0 test verification.

## Key Points
- Android JaCoCo can be false-green if `executionData` points to a stale AGP output path.
- Real unit-test exec data may live under `build/jacoco/testDebugUnitTest.exec`.
- A module having tests in the repository does not prove it is included in CI.
- `app-desktop`, `buildSrc`, instrumentation tests, and UI smoke tests need explicit gate decisions.
- This extends [[concepts/ci-module-test-coverage-gap]] and [[concepts/dev-push-ci-visible-full-run-contract]].

## Details
The 2026-05-31 project review found a likely false-green Android JaCoCo gate: the verification task used an outdated execution-data path while real `.exec` files were produced elsewhere. That means coverage thresholds can appear satisfied without measuring the intended test run.

The same review broadened the CI trust boundary. N>0 checks catch empty test runs, but they do not automatically include every relevant module. Desktop tests, `buildSrc` coverage, and instrumentation/UI suites need explicit inclusion or an explicit documented non-blocking status.

## Related Concepts
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/dev-push-ci-visible-full-run-contract]]
- [[concepts/ci-extra-modules-exposes-hidden-release-risk]]
- [[concepts/app-desktop-coverage-gate-scope]]

## Sources
- [[daily/2026-05-31]]: session 20:48 records the likely false-green Android JaCoCo gate caused by stale `executionData`.
- [[daily/2026-05-31]]: session 20:48 records that actual `.exec` data may be under `build/jacoco/testDebugUnitTest.exec`.
- [[daily/2026-05-31]]: session 20:48 records missing `app-desktop`, `buildSrc`, and instrumentation/UI gate decisions.
