---
title: "Code Review Before CI Monitor: Mandatory Sequencing"
aliases: [review-before-ci, post-push-review-cadence, code-reviewer-timing]
tags: [workflow, ci, code-review, discipline]
sources:
  - "daily/2026-05-15 (1).md"
created: 2026-05-15
updated: 2026-05-15
---

# Code Review Before CI Monitor: Mandatory Sequencing

After pushing a commit, the correct sequence is: push → spawn code-reviewer subagent → then start CI monitor. Reversing this order (push → wait for CI → review only on failure) allows bad code to accumulate. CI can pass with P0 regressions that a code reviewer would catch, because CI validates correctness of the tests, not quality of the implementation.

## Key Points

- Code review catches design-level regressions that CI tests don't cover: performance bottlenecks, deadlock patterns, resource leaks, return-code ambiguity
- Spawning the reviewer immediately after push uses the CI wait time productively — no idle blocking
- Session 13:30 / 15:30 pattern: P0 regressions (1s spin retry blocking dispatcher, Executor leak, indistinguishable return codes) were caught by code-reviewer subagent but NOT by CI — CI went green
- "Self-review insufficient" is not a preference: a developer cannot reliably spot all regressions in code they just wrote, because the mental model of intended behavior obscures discrepancies
- Reviewer subagent must be provided enriched context (recent changes + surrounding code) and findings must be personally verified before acting on them (false positive rate is non-zero)

## Details

### Why CI Alone Is Insufficient

CI validates that existing tests pass. If the tests were written to match the buggy implementation, CI will pass. In the ByeDPI rework session (2026-05-15), the following P0 regressions survived CI:

- **P0-1**: 1s C-side spin retry in `jniStartProxy` blocked the `limitedParallelism(1)` dispatcher — all subsequent `start()` calls serialized with 1s overhead. Tests used mocked JNI, so the real latency was never measured.
- **P0-2**: `jniStartProxy` returning `-1` for both "guard busy" and "real failure" — Kotlin code treated guard-busy as a fatal error. Tests didn't exercise concurrent start() calls.
- **P1-2**: `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` leaked in tests — CI passed because thread leak is only visible across test lifecycle, not within a single test case.

All three were caught by the code-reviewer subagent reading the diff, not by CI.

### Sequencing Protocol

```
1. git push
2. IMMEDIATELY: spawn code-reviewer subagent with enriched context
   - Include: git diff of the push + relevant surrounding code
3. While reviewer runs: start CI watcher (concurrent)
4. Read reviewer output → verify each finding personally in code
5. If findings confirmed: fix before CI result arrives
6. CI result: should be green; if red, diagnose separately
```

This uses the CI wait time (typically 10-15 minutes for Android builds) productively. The reviewer subagent completes in 1-3 minutes.

### False Positive Rate

Reviewer subagent findings are hypotheses, not facts. In the 2026-05-26 review session, 2 of 3 P0 findings were false positives after verification against the reference implementation. The rule: never create fix tasks directly from reviewer output — read the flagged code section first, then decide. See [[concepts/subagent-code-review-false-positives]].

### When Self-Review Fails

The 2026-05-15 session provides a direct admission: after pushing T-21/T-23/T-24, the developer believed the code was correct. The P0 regressions (spin retry, Executor leak) were non-obvious under the mental model of the implementation. External review with fresh eyes found them immediately. This is the structural reason code review is mandatory, not optional.

## Related Concepts

- [[concepts/subagent-code-review-false-positives]] — reviewer findings require personal verification; false positives are common
- [[concepts/byedpi-jni-guard-hardening]] — the session where missing pre-CI review caused P0 regressions to accumulate before detection
- [[connections/ci-false-green-vectors]] — CI green is not a sufficient quality signal; design regressions pass CI routinely

## Sources

- [[daily/2026-05-15 (1).md]] - Session 15:30: "ОБЯЗАН вызывать code-reviewer subagent ПОСЛЕ push коммита но ДО монитора CI — иначе bad code накапливается"; Session 13:33: "Self-review insufficient — code reviewer subagent caught P0 regressions I missed in honest audit"; P0-1 spin blocking, P0-2 indistinguishable codes, P1-2 Executor leak all caught by reviewer, not CI
