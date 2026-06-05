---
title: CI remediation batches must commit code and memory together
sources:
  - daily/2026-06-04.md
created: 2026-06-05
updated: 2026-06-05
---

## Summary
CI fixes in this project were repeatedly paired with `.memory/` housekeeping in the same commit to keep project state and knowledge state synchronized.

## Key Points
- Memory entries (`.memory/daily`) must be kept with the same push as related working fixes.
- Separate `.memory`-only commits are considered noise unless explicitly requested.
- Pushes are meaningful only when they include both functional remediation evidence and wiki-memory updates.
- Unpushed fixes were treated as incomplete work regardless of local patch completeness.
- Knowledge updates are part of CI operating context, not optional post-processing.

## Details
The daily narrative records explicit reminders to bundle memory updates with code changes and push once a coherent CI batch is ready. This reduced context drift: CI triage notes, root-cause observations, and fixes remain recoverable for future sessions.

This contract complements the broader “do not conclude without terminal success” rule: each push should represent a complete remediation batch, with no deferred `.memory` changes left behind that would break traceability of why a fix was made.

## Related Concepts
- [[concepts/memory-commit-with-work-only]]
- [[concepts/memory-hook-postcommit-dirty-contract]]
- [[concepts/ci-failure-batch-analysis-before-push]]
- [[concepts/dev-ci-root-cause-sequencing-loop]]

## Sources
- Daily log segment 20:50 and earlier entries repeatedly note pending dirty `.memory`, explicit push requirements, and batch semantics for `dev` runs.
