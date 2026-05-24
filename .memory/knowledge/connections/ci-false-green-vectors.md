---
title: "Connection: CI False Green Vectors"
connects:
  - "concepts/junit-platform-silent-skip"
  - "concepts/gradle-continue-full-failures"
  - "concepts/ci-workflow-discipline"
  - "concepts/test-tautology-always-green"
sources:
  - "daily/2026-05-01.md"
  - "daily/2026-05-12.md"
  - "daily/2026-05-02.md"
created: 2026-05-01
updated: 2026-05-02
---

# Connection: CI False Green Vectors

## The Connection

Two independent mechanisms in Gradle-based CI pipelines can produce false green results: test runner misconfiguration (missing `useJUnitPlatform()`) that silently skips all tests, and fail-fast behavior (`--continue` not set) that hides failures in unexecuted modules. Both create a CI system that reports success without verifying correctness.

## Key Insight

These two failure modes compound each other. A module with `useJUnitPlatform()` missing reports 0 tests = success. A module with actual test failures may never be reached if an earlier module fails first (without `--continue`). The combination means CI can be green while multiple modules have broken tests and other modules have tests that don't even run.

The deeper insight is that CI truthfulness requires positive evidence, not just absence of failure. A passing test suite with N > 0 tests is evidence. A passing build with 0 tests is absence of evidence — which, in the CI context, is deceptively reported as success.

Coverage gates amplify the problem: JaCoCo on 0 tests produces a vacuously true coverage report. The 95% threshold gate passes trivially because there are no uncovered lines to count against.

## Evidence

In Ozero's v0.0.2 development cycle:

- Library modules had JUnit Jupiter tests for ~3 months without `useJUnitPlatform()` — CI reported green
- Coverage gates passed on 0 tests — fictional 100% coverage
- When `useJUnitPlatform()` was added (`98002fd`), 5+ modules immediately showed test failures
- Without `--continue`, only the first module's failure was visible per CI run
- Fixing all issues required: (1) `useJUnitPlatform()` everywhere, (2) `--continue` in ci.yml, (3) verifying N > 0 tests per module

The fix chain demonstrates that CI truthfulness is a multi-layered property requiring active verification at each layer.

### Tautology Assertions (2026-05-12)

A third false-green vector was identified during a 6-subagent audit: test assertions that are logically always true (e.g., `assertTrue(list.isEmpty() || list.isNotEmpty())`). These tests run, pass, and contribute to coverage metrics — but verify nothing about the implementation. Unlike the first two vectors (tests don't run, or modules are skipped), tautology tests actively inflate both test counts and coverage percentages, making the false green harder to detect. See [[concepts/test-tautology-always-green]].

### Detekt Fail-Fast Masking Compile Errors (2026-05-02)

A fourth false-green-adjacent vector: when `assemble-debug` has `needs: kotlin-style` (detekt), a detekt failure causes `assemble-debug` to be skipped entirely. If a compile error exists in the release build path (`release.yml`), it is invisible until the detekt gate passes. The developer sees "CI red: detekt" and fixes only that, not realizing a compile error lurks behind it. Fixing detekt reveals the compile error, requiring a second CI run. See [[concepts/detekt-ratchet-desync-after-refactor]].

## Related Concepts

- [[concepts/junit-platform-silent-skip]] - Test runner misconfiguration vector: tests exist but don't execute
- [[concepts/gradle-continue-full-failures]] - Build orchestration vector: failures in early modules hide later modules
- [[concepts/ci-workflow-discipline]] - The CI workflow where both vectors were discovered and fixed
- [[concepts/test-tautology-always-green]] - Assertion logic vector: tests run and pass but verify nothing
- [[concepts/sentinel-fqn-desync]] - Naming mismatch vector: sentinel matches wrong string, passes vacuously
- [[concepts/detekt-ratchet-desync-after-refactor]] - Detekt ratchet drift + fail-fast hiding downstream compile errors
