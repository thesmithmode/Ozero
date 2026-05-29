---
title: Real path grounding before fix plans
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# Real path grounding before fix plans

## Summary
Regression plans in Ozero must be grounded in the repository's actual file paths, symbols, commits, and log evidence before proposing or applying fixes.

## Key Points
- Do not plan against expected paths such as `app/di/FptnModule.kt` or `common-vpn/ChainOrchestrator.kt` until `rg --files` or equivalent evidence confirms they exist.
- Path mistakes can turn a real regression investigation into a false architectural narrative.
- Evidence maps should include file path, line or symbol, commit or diff range, observed log symptom, and why that item matters.
- This rule is especially important when comparing `v0.2.0..HEAD`, because history rewrites and module moves can make old assumptions stale.

## Details
The 2026-05-29 investigation repeatedly found that assumed file locations did not match the current repository layout. That changed the diagnostic workflow: before forming the fix plan, the agent needed to re-anchor on actual module paths for `FptnEngine`, `ByeDpiEngine`, `StartSequenceCoordinator`, `ChainOrchestrator`, `TunnelController`, and shutdown/watchdog code.

This is a separate rule from ordinary code search. The point is not only to find files faster, but to prevent a plan from assigning ownership to the wrong layer. In lifecycle regressions, a symptom can appear under one engine label while the root cause lives in orchestration or stale status propagation, so real paths and real symbols are part of the proof.

## Related Concepts
- [[concepts/regression-diagnostics-real-path-grounding]]
- [[connections/cascade-lifecycle-regressions-cross-engine-proof]]
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/ci-artifact-report-driven-debugging]]

## Sources
- [[daily/2026-05-29]]: Assumed paths such as `app/di/FptnModule.kt` and `common-vpn/ChainOrchestrator.kt` were found not to match the actual project structure during the FPTN/ByeDPI plan.
- [[daily/2026-05-29]]: The investigation shifted to verified paths, `v0.2.0..HEAD` diffs, concrete commits, and `ozero_trace.log` before forming the final fix plan.
