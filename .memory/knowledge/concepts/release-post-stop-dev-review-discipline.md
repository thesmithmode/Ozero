---
title: Post-release stop returns work to dev review
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Post-release stop returns work to dev review

## Key Points
- If a user stops after a release and questions architectural correctness, the next work happens on `dev`, not by continuing to touch `main` or the published release.
- Green CI and a published release are delivery signals, not proof that engine-regression fixes were root-cause fixes.
- Follow-up fixes after the stop must be tracked as separate `dev` commits and must not be described as included in the already published tag.
- Dirty `.memory/` produced by hooks during the stop/review flow must still be committed according to the memory rules.

## Details

During the 2026-05-28 release cycle, `v1.0.3` was published and then the user stopped the process to ask whether the ByeDPI, URnetwork, and sing-box fixes had actually been reviewed. The resulting rule is that a post-release stop changes the mode of work: return to `dev`, audit the architecture, and avoid treating a green release workflow as evidence that the design is correct [[daily/2026-05-28.md]].

The same session clarified traceability for follow-up fixes. The additional review-fix commit `c8f3db44` was made on `dev` after the stop and was explicitly not part of the already published `v1.0.3` release. This distinction matters because release notes, regression analysis, and later audits must know which fixes shipped in which tag [[daily/2026-05-28.md]].

The pattern connects release workflow discipline with memory hygiene. The daily log became dirty after hooks ran during the release/review loop, and the action item was to avoid leaving `.memory/daily/2026-05-28.md` uncommitted [[daily/2026-05-28.md]].

## Related Concepts
- [[concepts/release-regression-self-review-gate]]
- [[concepts/release-runtime-scenario-checklist]]
- [[concepts/release-process]]
- [[concepts/memory-only-commit-ci-risk]]

## Sources
- [[daily/2026-05-28.md]]: records the user stopping after `v1.0.3`, the return to `dev`, the architectural review requirement, and the rule that post-stop fixes were separate from the published release.
- [[daily/2026-05-28.md]]: records the dirty `.memory/daily/2026-05-28.md` state caused by hooks and the requirement to commit memory changes.
