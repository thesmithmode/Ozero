---
title: Evidence-driven red run iteration loop
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-05
---

## Summary
`dev` CI failures formed a loop: fresh failing run -> rank blockers by root cause -> fix independent blockers -> push batch -> repeat with a new run until terminal success.

## Key Points
- One run can expose independent blockers in `buildSrc`, shared modules, and Android modules simultaneously.
- Compile failures are typically the first real blocker in a fresh run; coverage deficits become next-order after compilation is stable.
- Fixes that address only a symptom (gate relaxation, cosmetic changes) were deprioritized in favor of contract-aligned source/test corrections.
- Every cycle included re-validation against the next run and re-reading all failing jobs, not only a single job.
- `.memory` synchronization was part of each remediation batch, preventing knowledge drift between CI iterations.

## Details
The day’s sessions show the CI as a layered dependency graph, not a linear queue. A module can stay green while another reveals a compiler mismatch or a parser contract mismatch. After each push, the next run surfaced a new layer: lint/style, compile, then coverage hotspots across modules.

The key relationship is sequencing: stale artifacts and old statuses are misleading, and a full run can appear unchanged until old green checks are replaced by fresher failures. The practical control loop is to process jobs at the same granularity as their evidence, then re-run the entire run, rather than closing any subproblem in isolation.

## Related Concepts
- [[concepts/ci-fresh-run-authority-contract]]
- [[concepts/compile-vs-coverage-priority-contract]]
- [[concepts/coverage-gap-targeted-branch-remediation]]
- [[concepts/ci-memory-and-code-batch-push-contract]]

## Sources
- [[daily/2026-06-04.md]] repeatedly ties failing jobs to fresh run IDs (for example `26953272628`, `26968334219`) and records multi-root progression.
