---
title: Multi-engine regression fix staging
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Multi-engine regression fix staging

## Summary
When several engines fail after one regression window, fixes should be staged by shared lifecycle impact before engine-local symptoms.

## Key Points
- The 2026-05-29 sequence staged work as ByeDPI restart/lifecycle, FPTN lifecycle/DNS, URnetwork readiness, URnetwork relay, then sing-box exit IP.
- ByeDPI restart poisoning could make later WARP, FPTN, or URnetwork starts fail, so it had to be isolated first.
- FPTN `15s * N` auth ladders and stale callbacks had to be separated from false `Failed(BYEDPI, timeout)` symptoms.
- URnetwork and sing-box fixes were kept in their own layers to avoid mixing startup readiness with relay or IP-display policy.

## Details
The session showed that a visible engine label is not always the true source of a failure. A `Failed(BYEDPI, timeout)` status could appear after FPTN work or stale lifecycle callbacks, and a poisoned ByeDPI stop/start state could block unrelated modules. This made isolated engine-local patching risky.

The resulting staging rule is to stabilize shared lifecycle and stale-signal boundaries first, then handle engine-specific contracts. Each stage should have its own regression tests and should preserve unrelated behavior. This avoids the common trap of treating the last displayed engine label as the root cause.

## Related Concepts
- [[connections/shared-lifecycle-first-fix-order]]
- [[connections/stale-engine-signals-cross-engine-failures]]
- [[concepts/byedpi-wedged-lane-restart-isolation]]
- [[concepts/auto-candidate-terminal-status-invariant]]

## Sources
- [[daily/2026-05-29]]: the accepted fix order was ByeDPI, FPTN, URnetwork engine, URnetwork relay, sing-box IP, then CI/code review.
- [[daily/2026-05-29]]: ByeDPI repeated-start failure was fixed with lane rotation and generation guards before continuing to FPTN.
- [[daily/2026-05-29]]: false `Failed(BYEDPI, timeout)` symptoms were treated as possible stale/cross-engine effects rather than direct ByeDPI root cause.
