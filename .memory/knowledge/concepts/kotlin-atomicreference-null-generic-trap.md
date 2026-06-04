---
title: Kotlin AtomicReference null generic trap
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# Kotlin AtomicReference null generic trap

## Key Points
- `AtomicReference(null)` in Kotlin can infer `AtomicReference<Nothing?>` instead of the concrete type expected by the test or fake.
- The failure can appear as a CI compile error before tests or JaCoCo reports are produced.
- New tests that store callbacks, sessions, or domain values in atomics should declare the generic type explicitly.
- Treat this as a compile blocker, not a coverage failure, when Android module reports are missing.

## Details

The 2026-06-04 CI repair loop found that new `common-vpn` tests used `AtomicReference(null)` without explicit generic types. Kotlin inferred an unusable nullable bottom type, which broke compilation where concrete fields or callbacks were expected. This blocked `common-vpn` before test reports and coverage artifacts could be generated.

The practical rule is to write the domain type at construction time, for example `AtomicReference<MyType?>(null)`, when the reference is used across fakes, callbacks, or observable state. This belongs with other Kotlin test infrastructure traps such as [[concepts/kotlin-action-lambda-unused-param]] and [[concepts/cascade-unresolved-import-masking]] because it can look like a broader module failure while the root cause is a small type inference issue.

## Related Concepts
- [[concepts/cascade-unresolved-import-masking]]
- [[concepts/common-vpn-split-start-and-shutdown-branch-coverage]]
- [[concepts/ci-grouped-job-failure-attribution]]

## Sources
- [[daily/2026-06-04.md]] recorded that `common-vpn` compile failures came from new tests using `AtomicReference(null)` without explicit generic types.
- [[daily/2026-06-04.md]] recorded that Kotlin compile errors can stop CI before coverage reports are available.
