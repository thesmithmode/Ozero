---
title: "JaCoCo excludes must keep testable production logic in the gate"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# JaCoCo excludes must keep testable production logic in the gate
## Key Points
- Coverage excludes should remove Android shell, generated code, native glue, and thin SDK wrappers only when they are not meaningfully testable.
- Deterministic production code such as parsers, state machines, fingerprint logic, and security helpers should stay in the gate.
- Broad masks can lower the ratio by removing already-covered code while leaving miss-heavy code in place.
- The contract test should defend both against broad patterns and against accidental narrow exclusions of real logic.
## Details
The daily log sharpened the coverage boundary: the 95 percent gate is only fair when it applies to code that actually has testable production behavior. Excluding deterministic Kotlin or Java logic simply because it is inconvenient to test creates a false-green gate and makes CI less trustworthy.

The resulting boundary is narrow and evidence-based. Generated, DI, Android shell, and native bridge layers can be excluded, but parsers, restart state machines, fingerprint builders, and security-sensitive helpers should remain inside the measurement surface. This aligns with [[concepts/jacoco-exclude-evidence-boundary]] and [[concepts/coverage-security-defensive-branches-test-contract]].
## Related Concepts
- [[concepts/jacoco-exclude-evidence-boundary]]
- [[concepts/coverage-security-defensive-branches-test-contract]]
- [[concepts/ci-coverage-historical-debt-gate-boundary]]
## Sources
- `daily/2026-06-02.md`: the log says the coverage gate should keep deterministic production logic in scope and only exclude Android/generated/native glue and similar non-deterministic boundaries.
