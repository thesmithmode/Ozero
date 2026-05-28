---
title: Navigation cherry-pick conflicts require preserving both route sets
sources:
  - daily/2026-05-27.md
created: 2026-05-28
updated: 2026-05-28
---
# Navigation Cherry-Pick Preserve Routes

## Key Points
- RootNavigation conflicts during feature cherry-picks are additive by default: dev routes and feature routes can both be valid.
- Choosing one side can silently remove screens such as SingboxAdvancedSettings or ChainSettings.
- Conflict resolution must inspect route ownership before deletion, not rely on "ours" or "theirs".
- The same rule applies to navigation entries, route constants, and destination handlers.

## Details

During the 2026-05-27 merge of urnetwork/code-hideing/proxy branches into dev, RootNavigation conflicts contained routes from both the current dev branch and the feature branch. The correct resolution was to preserve both sides, specifically keeping SingboxAdvancedSettings and ChainSettings navigation paths from different conflict sides.

Navigation conflicts are high-risk because the code can still compile after one route set is dropped. The primary failure mode is a user-visible missing screen or broken navigation path rather than an immediate compiler error. Resolve these conflicts by mapping each route to its screen and owner module before finalizing the merge.

## Related Concepts
- [[concepts/modular-boundary-engine-specific-logic]]
- [[concepts/per-engine-ui]]
- [[concepts/feature-deletion-orphaned-consumers]]

## Sources
- [[daily/2026-05-27.md]]: Session 11:42 recorded RootNavigation cherry-pick conflicts and the decision to preserve both dev and proxy navigation routes.
