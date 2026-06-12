---
title: "ByeDPI strategy apply and favorites separation"
sources:
  - "daily/2026-05-18.md"
created: 2026-06-12
updated: 2026-06-12
---
# ByeDPI strategy apply and favorites separation

## Key Points
- Applying a strategy is not the same product action as saving a strategy to favorites.
- `savedStrategyStore.add` in `onApply` or automatic evolution paths pollutes the user's curated favorites.
- Evolution may discover and apply a winner, but persistence should be explicit or stored in a separate history/cache.
- Sentinel tests should verify that apply/history flows do not mutate the favorites store.

## Details

The strategy screen bug came from conflating two persistence meanings. The user expected "Применить" to activate the selected ByeDPI args, while the code also wrote the strategy into `savedStrategyStore`. The same accidental persistence existed in evolution paths, so best chromosomes could appear in favorites even when the user did not choose to bookmark them.

The correct boundary is that favorites are user-curated. Apply is runtime selection, and evolution history belongs in GA memory or a dedicated history/cache. This concept connects [[concepts/genetic-strategy-evolution]] with [[concepts/gene-memory-concurrency-traps]]: learned fitness and user bookmarks are both persisted, but they serve different product contracts.

The fix removed `savedStrategyStore.add` calls from apply/evolution paths and added a sentinel for the non-mutation behavior. This prevents UI trust erosion where the favorites list stops representing deliberate user choices.

## Related Concepts
- [[concepts/genetic-strategy-evolution]]
- [[concepts/gene-memory-concurrency-traps]]
- [[concepts/byedpi-auto-strategy-testing]]
- [[concepts/byedpi-strategy-runtime-disconnect]]

## Sources
- [[daily/2026-05-18]]: Session 11:38 identifies `onApply` writing into `savedStrategyStore` as the root cause of favorites pollution.
- [[daily/2026-05-18]]: Session 11:54 records removal of three auto-add points during `onApply` and `runEvolution`, with a sentinel test.
