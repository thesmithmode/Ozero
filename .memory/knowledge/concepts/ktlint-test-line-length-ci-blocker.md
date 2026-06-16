---
title: ktlint test line length can be a CI blocker
sources:
  - daily/2026-06-04.md
created: 2026-06-09
updated: 2026-06-09
---
# ktlint test line length can be a CI blocker

## Summary
Long test URLs and override signatures can fail the `ktlint + detekt` job even when production logic and unit assertions are correct.

## Key Points
- The style gate can fail on `max_line_length = 120` in tests, not only in production code.
- Long `download_url` literals and long override signatures were concrete blockers in the 2026-06-04 CI cycle.
- Fixes should preserve test semantics while wrapping literals, extracting values, or formatting signatures.
- A style-only failure can delay visibility of later unit and coverage failures.

## Details
On 2026-06-04, the CI cycle first appeared as a broad `ktlint + detekt` problem. The exact root cause later narrowed to line-length violations in tests: a long `download_url` line in `LockFileParserTest.kt` and long override method signatures in `engine-warp` test code. These were not product regressions; they were formatting blockers.

The durable rule is to treat style failures as first-class CI blockers, but keep their fixes minimal. When a test literal or method signature exceeds the limit, the correct remediation is to reformat the test while preserving the assertion contract. This links directly to [[concepts/dev-ci-kotlin-style-cascade]] and [[concepts/ci-style-failure-hides-compile-regression]], because style gates can hide the next compile, assertion, or coverage blocker.

## Related Concepts
- [[concepts/dev-ci-kotlin-style-cascade]]
- [[concepts/ci-style-failure-hides-compile-regression]]
- [[concepts/kotlin-style-blank-line-root-cause]]
- [[connections/ci-compile-coverage-style-blocker-sequencing]]

## Sources
- [[daily/2026-06-04]]: session 17:32 records `LockFileParserTest.kt` failing because `download_url` exceeded `max_line_length = 120`.
- [[daily/2026-06-04]]: session 17:32 records additional `engine-warp` ktlint failures from long override signatures.
