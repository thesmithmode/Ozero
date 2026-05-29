---
title: Memory commits go with related work only
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# Memory commits go with related work only

## Key Points
- `.memory/` changes should not be pushed as a standalone routine step.
- Dirty memory should be included in the nearest related working code commit.
- A separate memory-only commit is acceptable only when explicitly requested or no working commit exists.
- Memory workflow rules can conflict, so the project-specific AGENTS rule overrides generic memory advice for Ozero.
- This rule reduces delay during active debugging sessions.

## Details

The 2026-05-29 session exposed a workflow conflict. The global memory rule said dirty `.memory/` should be committed immediately after each session, while the Ozero project rule says `.memory/` should not be committed or pushed as its own standalone step during normal work. The user explicitly objected to separate memory pushes because they slowed down urgent runtime debugging.

The updated Ozero rule is to include `.memory/` and AGENTS changes in the nearest working code commit. This keeps memory synchronized with the related fix while avoiding extra memory-only commits and pushes that interrupt the main task. Standalone memory commits remain exceptional, not the default.

The rule matters operationally because multiple fixes on 2026-05-29 required fast sequencing across `dev`, CI, and runtime regressions. Memory changes were therefore bundled into working commits such as URnetwork fixes rather than pushed separately.

## Related Concepts
- [[concepts/memory-only-commit-ci-risk]]
- [[concepts/git-active-branch-discipline]]
- [[concepts/ci-workflow-discipline]]
- [[concepts/release-post-stop-dev-review-discipline]]

## Sources
- [[daily/2026-05-29]]: user rejected separate `.memory` push/commit steps during active debugging.
- [[daily/2026-05-29]]: the project rule was changed to commit `.memory/` with the nearest working code commit.
- [[daily/2026-05-29]]: URnetwork commits included `.memory`/AGENTS changes with the working code commit rather than as memory-only commits.
- [[daily/2026-05-29]]: an earlier memory-only commit `3f9dc01b` was treated as a slowdown and later workflow rules were corrected.
