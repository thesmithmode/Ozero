---
title: "Connection: Self-Review Insufficient — Code Reviewer Subagent Required"
connects:
  - "concepts/byedpi-jni-guard-hardening"
  - "concepts/suppress-annotation-decomposition"
  - "concepts/byedpi-mock-server-ci-fragility"
sources:
  - "daily/2026-05-15.md"
created: 2026-05-15
updated: 2026-05-15
---

# Connection: Self-Review Insufficient — Code Reviewer Subagent Required

## The Connection

In session 2026-05-15 15:30, a code reviewer subagent found 7 issues (2×P0, 4×P1, 1×P2) in commits the author had just pushed — including a 1-second spin retry blocking the single-thread dispatcher (P0-1) and indistinguishable JNI return codes (P0-2). The author did not catch these during self-review. This matches the earlier 2026-05-12 pattern where 6 subagents found 22 data integrity issues invisible to unit tests.

## Key Insight

Self-review has a structural blind spot: the author knows *what* the code is intended to do and reads it through that lens. A fresh reviewer (human or AI) reads what the code *actually does*. The specific failure class found was "correct logic with wrong performance characteristics" (1s spin in single-thread dispatcher) — a category where the code produces correct results in tests but degrades real-world behavior. Tests pass; the regression is a performance trap, not a functional bug.

The 2026-05-15 session explicitly confirmed: code reviewer subagent should be spawned AFTER push but BEFORE CI monitoring starts. This catches architectural regressions that tests cannot find while CI verifies compilation and functional correctness. The two are complementary, not redundant.

## Evidence

- **P0-1**: 100×10ms C spin retry in `jniStartProxy` — blocks `limitedParallelism(1)` dispatcher for 1s. Tests pass (they mock the JNI layer). Only review caught the serialization bottleneck.
- **P0-2**: `-1` return for both guard-busy and real failure — Kotlin code treated both identically. Functionally correct (both mean "start failed") but diagnostically blind.
- **P1-1**: `jniForceClose` without guard release + single-thread dispatcher = permanent wedge. Tests used mocks that don't hold the guard.
- **P1-2**: `Executors.newSingleThreadExecutor().asCoroutineDispatcher()` leaks in tests — `@AfterEach` didn't call `close()`.
- **P2-2**: Sentinel regex `[^}]*` didn't cross inner braces — sentinel passed CI but didn't actually validate the target code.

Five of seven findings were invisible to automated testing. Only adversarial review found them.

## Related Concepts

- [[concepts/byedpi-jni-guard-hardening]] - The specific code where regressions were found and fixed
- [[concepts/suppress-annotation-decomposition]] - Prior example (2026-05-12): subagent quality issues caught in review
- [[connections/audit-driven-concurrency-discovery]] - Same meta-pattern: adversarial review finds what tests miss; 22 findings in 6-subagent session
- [[concepts/byedpi-mock-server-ci-fragility]] - Mock patterns that hide real JNI behavior; review catches what mocks obscure
