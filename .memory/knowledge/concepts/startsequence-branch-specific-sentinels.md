---
title: StartSequence branch-specific sentinels
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# StartSequence branch-specific sentinels

## Key Points
- Extracting shared startup helpers can make a single sentinel pass against only the first branch.
- `usesCustomTun` and non-custom-TUN paths need separate sentinels when their order differs.
- After structural startup changes, measure detekt complexity and return-count again.
- Fail-after-start paths should explicitly notify `TunnelController.onEngineDied(engineId)`.

## Details

The StartSequenceCoordinator changes split startup behavior by branch. For custom TUN engines the establish path must happen first; for non-custom-TUN engines the chain startup can happen first. A sentinel based on `indexOf` or the first matching text can pass while only one branch preserves the intended order.

The same work showed that structural fixes can introduce static-analysis failures even when behavior is correct. Extracting `establishTunAndChain()` reduced complexity and return count, while a later failure path needed an explicit `onEngineDied` signal so the UI did not remain stuck in Connecting after `startChain` succeeded and `establishTun` failed.

## Related Concepts
- [[concepts/sentinel-anchor-substringafter-trap]]
- [[concepts/sentinel-protecting-bug-trap]]
- [[concepts/cyclomatic-complexity-extract-helper]]
- [[concepts/engine-switch-chain-cascading-failures]]

## Sources
- [[daily/2026-05-21.md]] records that a single sentinel after extraction was insufficient and branch-specific sentinels were needed for `usesCustomTun` and `!usesCustomTun`.
- [[daily/2026-05-21.md]] records the detekt complexity/return-count fix and the explicit `onEngineDied(engineId)` notification for fail-after-start.
