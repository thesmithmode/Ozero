---
title: Regression diagnostics require real path grounding
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Regression diagnostics require real path grounding

## Key Points
- Regression analysis must verify real repository paths before attributing behavior to files or modules.
- Diffs against a known-good tag, such as `v0.2.0`, reduce false hypotheses after history rewrites or large refactors.
- Logs, commit history, tests, and reference implementations are separate evidence sources and should be correlated.
- Environment failures such as shell sandbox errors are operational constraints, not code root causes.
- This concept extends [[concepts/release-last-good-baseline-audit]] and [[concepts/code-quality-review-proof-standard]].

## Details
The 2026-05-29 investigation repeatedly corrected assumptions about file locations such as `app/di/FptnModule.kt` and `common-vpn/ChainOrchestrator.kt`. The resolution was to stop building conclusions on expected paths and first locate the actual owning modules and symbols in the current repository.

The same sessions used `v0.2.0` as the working baseline because the user reported FPTN and ByeDPI were stable there. The correct method was not a broad undirected review, but a focused comparison of relevant engine and orchestration files against that baseline, then correlation with `ozero_trace.log` and existing `.memory` articles.

Tooling problems, including `spawn setup refresh` and restricted access to external log paths, were recorded as environment constraints. They affected how evidence could be collected, but they were not accepted as explanations for the runtime regressions.

## Related Concepts
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/code-quality-review-proof-standard]]
- [[concepts/release-audit-tag-sha-grounding]]
- [[connections/engine-switch-regressions-baseline-runtime-proof]]

## Sources
- [[daily/2026-05-29.md]] records the need to verify actual file paths before forming a fix plan.
- [[daily/2026-05-29.md]] records using `v0.2.0`, `ozero_trace.log`, commit history, and reference material as separate evidence streams.
