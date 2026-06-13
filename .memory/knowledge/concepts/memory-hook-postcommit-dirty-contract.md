---
title: Memory hook post-commit dirty contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# Memory hook post-commit dirty contract

## Summary
Codex memory hooks can make `.memory/` dirty again after a working commit, so memory changes should be bundled with the next related code commit instead of pushed as a standalone routine step.

## Key Points
- `.memory/` should not be committed or pushed as an isolated maintenance step during normal feature work.
- If hooks append daily or knowledge files after a working commit, those changes wait for the nearest related working commit.
- A separate memory-only commit is reserved for explicit user direction or when no related working commit exists.
- Final status checks must account for hook-created dirty memory files after commit.

## Details
The 2026-05-29 sessions exposed a workflow conflict: older rules pushed agents toward immediate standalone memory commits, while the project-specific rule required memory updates to travel with the nearest work commit. The user clarified the intended Ozero behavior after repeated delays caused by standalone `.memory` handling.

The practical contract is to treat `.memory` as related traceability material, not as a separate delivery stream. This avoids wasting CI/push cycles on memory-only changes while still preventing durable context from being lost.

## Related Concepts
- [[concepts/memory-commit-with-work-only]]
- [[concepts/memory-only-commit-ci-risk]]
- [[concepts/wiki-knowledge-base]]
- [[concepts/git-active-branch-discipline]]

## Sources
- [[daily/2026-05-29]]: User rejected separate `.memory` push/commit steps and required bundling memory updates with working code commits.
- [[daily/2026-05-29]]: After hooks ran, `.memory/daily/2026-05-29.md`, `knowledge/log.md`, and new knowledge files could become dirty again even after the branch matched remote.
