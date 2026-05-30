---
title: Overlapping PR merge preserve dev contracts
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# Overlapping PR merge preserve dev contracts
## Summary
When several overlapping PRs all contain needed work, merge them as layered contributions on top of fresh `dev`, preserving already accepted contracts instead of treating a later integration branch as a replacement.
## Key Points
- Compare each PR against current `origin/dev` to isolate its unique diff before resolving conflicts.
- Preserve accepted `dev` behavior during conflict resolution, especially when an older branch reintroduces unsafe behavior.
- Use integration branches as evidence for conflict resolution, not as wholesale replacements for already reviewed PRs.
- Confirm with the full `dev` CI after merge because PR checks can skip unit jobs under workflow conditions.
## Details
The PR #71/#73/#74/#75 sequence showed that multiple `codex/*` branches can each contain a valid slice of the task. PR #71 was an isolated ByeDPI fix. PR #73 added the safer `amnezia-dns` user-confirmed flow. PR #74 added typed `PortBusy(protocol,address,owner)` diagnostics and UI/test mapping, but also carried older auto-remove behavior that had to be rejected. PR #75 was useful only as a minimal running-container contract assertion after #73 and #74 were already merged.

The durable rule is to resolve conflicts against the current target branch, not against an assumed final branch. A staged merge can contain changes that are already present on `dev`; those must be identified and ignored when deciding what a PR uniquely contributes. This prevents a later conflict resolution from silently rolling back newer safety or UI contracts.

This pattern also affects validation. In this session, PR checks on `codex/*` branches could be green while important unit jobs were skipped by workflow conditions. The reliable signal was the full `dev` CI after squash merges.
## Related Concepts
- [[concepts/experimental-fix-branch-selective-port]]
- [[concepts/navigation-cherrypick-preserve-routes]]
- [[concepts/masterdns-amnezia-dns-running-udp-contract]]
- [[connections/layered-pr-merge-ci-feedback-loop]]
## Sources
- [[daily/2026-05-30]]: The user clarified that every PR contained a needed part and conflicts had to be resolved without losing each contribution.
- [[daily/2026-05-30]]: PR #75 was explicitly not treated as a replacement for #73/#74.
- [[daily/2026-05-30]]: The accepted #74 resolution preserved typed `PortBusy` diagnostics while rejecting older `amnezia-dns` auto-remove behavior.
- [[daily/2026-05-30]]: Full test signal was deferred to `dev` CI because PR jobs were skipped by workflow conditions.
