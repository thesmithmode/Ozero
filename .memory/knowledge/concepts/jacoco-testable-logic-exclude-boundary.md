---
title: JaCoCo excludes must not remove testable production logic
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# JaCoCo excludes must not remove testable production logic

## Summary

Ozero's 95% coverage gate applies to production code that is realistically testable. Excludes are acceptable for Android shell, generated, DI, native, and external SDK wrapper boundaries, but not for deterministic Kotlin or Java logic.

## Key Points

- Parser, config, fingerprint, restart, state-machine, and security helper classes belong in the coverage gate.
- Generated, DI, Android framework glue, native bridges, and external SDK wrappers may be excluded when they have no deterministic unit-test surface.
- Broad masks such as `**/ui/**` can lower the ratio by excluding covered classes while leaving miss-heavy code.
- `JacocoExcludesContractTest` must catch both broad masks and targeted exclusions of meaningful testable classes.

## Details

The 2026-06-02 audit concluded that the current `dev` coverage policy was too soft because it excluded business logic and stateful production behavior that should remain measurable. The user clarified the intended threshold: 95% is not for every generated or Android-bound line, but it is for code that has a meaningful deterministic test surface.

This boundary changes the role of `JacocoExcludesContractTest`. It should not only reject obviously broad masks; it should also prevent specific class-level exclusions for production logic such as runtime restart orchestration, parsers, fingerprints, and security verification. When code is important and unit-testable, the preferred fix is focused coverage or a justified narrow boundary, not removal from the gate.

## Related Concepts

- [[concepts/jacoco-exclude-evidence-boundary]]
- [[concepts/jacoco-honest-coverage-gate-boundary]]
- [[concepts/bootstrap-signature-real-trust-gate]]
- [[concepts/runtime-restart-pending-fingerprint-baseline]]

## Sources

- [[daily/2026-06-02]]: user clarified that 95% coverage applies to code that realistically makes sense to test.
- [[daily/2026-06-02]]: audit found exclusions for restart logic, state machines, parsers, fingerprints, and security helpers.
- [[daily/2026-06-02]]: decision recorded that only generated, DI, Android shell, native, and external SDK wrapper code should remain excluded.
