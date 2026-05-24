---
title: "Detekt Threshold Ratchet Desync After Refactor"
aliases: [detekt-config-ratchet, detekt-threshold-drift]
tags: [ci, detekt, kotlin, static-analysis, gotcha]
sources:
  - "daily/2026-05-02.md"
created: 2026-05-02
updated: 2026-05-02
---

# Detekt Threshold Ratchet Desync After Refactor

Detekt's `thresholdInInterfaces` ratchet (and similar per-rule baselines) can drift out of sync when refactoring changes the code being measured. After the v0.0.2-5 P1-fixes batch added method implementations to interfaces and engine classes, the project's detekt config had a baseline of 20 that the refactored code exceeded. CI failed on the detekt gate, blocking all downstream jobs — and because detekt's `needs:` was a prerequisite for `assemble-debug`, the actual compile error that existed in the release build was invisible until detekt passed.

## Key Points

- Detekt default `thresholdInInterfaces` is 10; Ozero's project config raises it to 20 — this mismatch requires checking the project config, not the detekt docs
- After P1 fixes that added interface methods, re-run detekt locally before committing to catch ratchet violations early
- CI job ordering matters: when `assemble-debug` has `needs: kotlin-style` (detekt), compile errors in `assemble-release` are invisible until kotlin-style passes
- Updating ratchet baselines requires understanding whether the new code is legitimately more complex (update baseline) or whether the refactor has created an interface that is too large (split it)
- The `parallelStartAndStop` test was also out of sync after a ChainOrchestrator mutex fix — changed `stopCount` assertion from 5 to 4 to reflect the new behavior that stopping an empty chain no longer counts

## Details

### Ratchet Desync Mechanism

Detekt's ratchet mechanism stores a baseline of complexity/coupling values per file or rule. When code changes push a metric above the stored threshold, detekt fails with a "ratchet violation." The intended workflow is: fail → developer consciously decides to raise the baseline (accepting more complexity) or refactor to reduce it.

The desync happens silently. After significant refactoring (multiple P1 fixes touching engine interfaces), the metrics shift without a corresponding baseline update. CI then fails on what appears to be a style gate, obscuring the actual substance of the change.

### CI Job Dependency Masking

In Ozero's CI configuration, `assemble-debug` depends on `kotlin-style` (detekt + ktlint). When detekt fails, `assemble-debug` is skipped as a dependency. If `release-compile` runs in parallel under a different job path (e.g., triggered by `release.yml`), compile errors in the release build are only visible when running the release workflow — not in the main CI checks. This created a situation where:

1. detekt failed → CI red
2. Developer focuses on detekt
3. Compile error in release build remains invisible
4. Only discovered when release.yml tried to build the APK

The fix ordering was: (1) resolve detekt ratchet → (2) re-run CI → (3) discover compile error → (4) fix compile error → (5) green CI.

### Test Assertion Desync

Alongside the detekt ratchet, the `parallelStartAndStop` test asserted `stopCount == 5` based on the pre-fix behavior where stopping an empty chain triggered an extra stop callback. After the ChainOrchestrator mutex fix changed this behavior, the count became 4. The test was describing the old (buggy) behavior rather than the new correct one, causing CI failure that appeared unrelated to the detekt issue.

## Related Concepts

- [[connections/ci-false-green-vectors]] - Broader pattern of CI reporting wrong status; detekt fail-fast is a related masking vector
- [[concepts/ci-job-dependency-masking]] - CI job ordering that hides failures in downstream jobs
- [[concepts/ci-workflow-discipline]] - The CI workflow where this pattern appeared
- [[concepts/sentinel-protecting-bug-trap]] - Analogous: test asserts old broken behavior rather than new correct behavior

## Sources

- [[daily/2026-05-02.md]] - Session 13:20: detekt `thresholdInInterfaces` ratchet violation after P1 fixes; detekt `needs:` hiding compile error; `parallelStartAndStop` stopCount 5→4 after ChainOrchestrator fix
