---
title: "Memory changes bundled with working commits"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# Memory changes bundled with working commits
## Key Points
- In Ozero, `.memory/` changes should not be pushed as routine standalone commits.
- Memory updates should be included in the nearest related working code/rule commit.
- The rule was clarified after memory-only commits slowed simple tasks and complicated push cadence.
- A standalone memory commit remains exceptional and should require explicit user direction.
## Details
The project has two competing pressures: `.memory/` should not be left dirty after meaningful work, but routine memory-only commits waste time and create noise. The 2026-05-29 sessions clarified the Ozero-specific rule: include memory changes with the nearest working commit and avoid a separate push just for `.memory/`.

This changes the operational default from "commit memory immediately as its own unit" to "carry memory into the next related work commit." The user explicitly corrected the workflow after a simple fetch/pull task spent extra time producing a separate memory commit.

This concept is about repository workflow, not memory schema. The schema and article quality rules remain governed by [[concepts/wiki-knowledge-base]], while commit/push discipline should stay compatible with [[concepts/ci-workflow-discipline]] and Ozero branch rules.
## Related Concepts
- [[concepts/wiki-knowledge-base]] - Describes the `.memory/` knowledge system and compile/query workflow.
- [[concepts/ci-workflow-discipline]] - Covers CI and branch discipline around changes.
- [[concepts/memory-only-commit-ci-risk]] - Earlier note on memory-only commits and CI risk.
- [[concepts/git-active-branch-discipline]] - Related repository hygiene rule.
## Sources
- [[daily/2026-05-29]]: records the user rejecting separate `.memory` push/commit cadence.
- [[daily/2026-05-29]]: records the clarified rule to include `.memory` updates in the nearest working commit.
- [[daily/2026-05-29]]: records the earlier memory-only commit `3f9dc01b` and why it was later treated as a workflow problem.
