---
title: "URnetwork Active Location IP Refresh Polling"
sources:
  - "daily/2026-05-10.md"
created: 2026-06-09
updated: 2026-06-09
---

# URnetwork Active Location IP Refresh Polling

URnetwork exit-IP refresh must react to active location changes, not only to a static engine key. A cache key such as `"URNETWORK:0"` cannot represent country or location changes, so the UI needs active polling or a location-sensitive invalidation source while URnetwork is running.

## Key Points

- A static key like `"URNETWORK:0"` does not change when the selected URnetwork country changes.
- Without refresh, the UI can keep showing stale IP/location evidence after a location switch.
- The 2026-05-10 fix used a periodic active-URnetwork polling coroutine every 5 seconds.
- Polling tests must use bounded virtual-time advancement, as described in [[concepts/viewmodel-polling-runtest-trap]].
- This complements [[concepts/urnetwork-location-empty-string-fallback]] by separating label normalization from refresh cadence.

## Details

GROUP B found that `MainViewModel` used a static URnetwork key for IP info lookup. That key was stable across country changes, so the IP display was not naturally invalidated when the selected URnetwork location changed.

The chosen fix was runtime polling while URnetwork is active: a coroutine periodically refreshes the IP/location evidence instead of relying on a key that cannot encode the selected country. This is a pragmatic solution when the SDK or state model does not expose a clean location-change event suitable for direct invalidation.

The test implication is important. A `while(true) + delay()` polling loop is valid runtime behavior but must be tested with explicit `advanceTimeBy()` and `runCurrent()`, not `advanceUntilIdle()`, because the scheduler is intentionally never idle.

## Related Concepts

- [[concepts/viewmodel-polling-runtest-trap]] - Test contract for infinite ViewModel polling loops.
- [[concepts/urnetwork-location-empty-string-fallback]] - Separate URnetwork display bug in the same GROUP B batch.
- [[concepts/vpn-ip-detection-contract]] - Exit-IP evidence must reflect the active route/state.
- [[concepts/exit-node-strategy-ui-unification]] - Exit-node UI data should come from engine-owned safe strategies.

## Sources

- [[daily/2026-05-10.md]] - Session 17:46: B3 root cause was static key `"URNETWORK:0"`; country changes did not trigger IP refresh, so the fix used 5-second polling while URnetwork is active.
