---
title: Experimental fix branch selective port
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Experimental fix branch selective port

## Summary
Large experimental fix branches should not be merged wholesale into `dev`; only verified pieces should be ported after comparison with current code and the last-good baseline.

## Key Points
- The `byedpi-fptn-try-fix` branch diverged too far from current `dev` to be safe as a direct merge source.
- The accepted workflow was to keep CI-only fixes in `dev` and port runtime fixes selectively.
- Each candidate patch had to be rechecked against `v0.2.0`, current `dev`, logs, and actual file paths.
- Selective porting reduced the risk of reintroducing stale FPTN behavior while fixing ByeDPI lifecycle regressions.

## Details
The session separated two kinds of work: CI workflow repair and runtime behavior repair. The experimental branch held useful ideas, but also contained older assumptions and a larger mismatch with current `dev`. Merging it as a block would have made it difficult to know which change caused each new behavior.

The durable practice is to treat experimental fix branches as evidence sources, not as merge units. A change should be copied only after confirming the owning file, the current code shape, and the regression chain from the last known working release. This matters especially when several engines share lifecycle state and a stale patch can affect unrelated modules.

## Related Concepts
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/regression-diagnostics-real-path-grounding]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[connections/cascade-lifecycle-regressions-cross-engine-proof]]

## Sources
- [[daily/2026-05-29]]: `byedpi-fptn-try-fix` was identified as unsuitable for wholesale merge because it diverged from current `dev`.
- [[daily/2026-05-29]]: the chosen approach was to port only relevant pieces after comparing with `v0.2.0`, logs, and current file paths.
- [[daily/2026-05-29]]: CI fixes were kept separate from runtime fixes to preserve required check names and avoid workflow churn.
