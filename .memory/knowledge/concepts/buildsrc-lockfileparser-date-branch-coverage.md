---
title: BuildSrc LockFileParser Date-branch coverage
sources:
  - daily/2026-06-04.md
created: 2026-06-04
updated: 2026-06-04
---
# BuildSrc LockFileParser Date-branch coverage
## Key Points
- `buildSrc` coverage on 2026-06-04 still missed a branch in `LockFileParser`.
- The uncovered path was tied to `generated_at` being parsed as a YAML `Date`, not as a plain string.
- The failure was framed as a coverage gap, so the correct fix is a test that forces the `Date` branch.
- The module remained part of the CI gate, so the branch gap had to be closed before the run could turn green.
## Details
The log records that the `buildSrc` red state was not a flaky assertion but a real branch-coverage deficit. The important nuance is the type-sensitive YAML path: `generated_at` can emerge from parsing as a `Date`, and that forces a branch that string-only tests do not exercise.

That makes this issue an example of [[jacoco-honest-coverage-gate-boundary]] and [[ci-coverage-gate-artifact-trust-contract]] in practice. The right response is not to relax the gate, but to add a deterministic test that reaches the production branch and proves the parser behavior under the real type shape.
## Related Concepts
- [[jacoco-honest-coverage-gate-boundary]]
- [[ci-coverage-gate-artifact-trust-contract]]
- [[ci-coverage-historical-debt-gate-boundary]]
## Sources
- [[daily/2026-06-04.md]]
