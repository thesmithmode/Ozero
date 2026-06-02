---
title: App CI diagnostic sharding needs N>0 gates
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# App CI diagnostic sharding needs N>0 gates

## Summary

Splitting Ozero's monolithic `Tests - app` job can improve diagnostics, but only after the coverage policy is stable and each shard has reliable test discovery with N>0 enforcement.

## Key Points

- `Tests - app` currently mixes compile, unit tests, discovery, JaCoCo report, verification, and artifact upload.
- Splitting can separate unit execution, report generation, coverage verification, and possibly domain-specific shards.
- Sharding before a stable coverage boundary changes two variables at once and obscures root cause.
- Filtered test shards are false-green risks unless each shard proves that it ran at least one intended test.

## Details

The daily log records repeated discussion of splitting the app CI job for better failure attribution. The idea was considered valid because a single job can report red while hiding whether the blocker is compilation, tests, N>0 discovery, JaCoCo report generation, coverage verification, or artifact upload. The 18:53 session reinforced this: downloaded artifacts showed tests were green, while the job failed because `jacocoTestCoverageVerification` rejected coverage.

The decision was to defer sharding until the coverage boundary is honest. Diagnostic workflow changes should not be mixed with mask and threshold changes because that makes the next CI result ambiguous. If domain-specific shards are introduced later, each shard needs its own N>0 gate to avoid a green result caused by filters selecting no tests.

## Related Concepts

- [[concepts/dev-push-ci-visible-full-run-contract]]
- [[concepts/ci-module-test-coverage-gap]]
- [[concepts/ci-engine-module-missing-tests]]
- [[concepts/ci-grouped-job-failure-attribution]]

## Sources

- [[daily/2026-06-02]]: sessions discussed splitting `Tests - app` into unit tests, coverage report, coverage gate, and optional domain shards.
- [[daily/2026-06-02]]: decision recorded that sharding should wait until coverage policy is stabilized.
- [[daily/2026-06-02]]: lesson recorded that sharding by filters is dangerous without per-shard N>0 discovery gates.
