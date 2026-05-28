---
title: ByeDPI stop timeout contract
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# ByeDPI stop timeout contract

## Summary
ByeDPI stop can legitimately take longer than the shared chain orchestrator default because the engine performs a two-phase native/proxy drain. The timeout belongs in the engine contract through `EnginePlugin.stopTimeoutMs()`, not as an unrelated delay around restart.

## Key Points
- The release-regression trace showed ByeDPI hanging after a 2000 ms stop timeout before the next start.
- Comparison with `v0.2.11` showed ByeDPI already had a two-phase stop using `STOP_GRACE_MS`.
- The fix was to let the orchestrator honor a longer ByeDPI-specific stop timeout, preserving the engine-owned drain window.
- The risk is masking a real JNI hang, so this pattern needs evidence from stop phases and regression sentinels.

## Details
During the 2026-05-28 release-regression investigation, ByeDPI failures were linked to restart after an incomplete stop. The shared orchestrator could release its mutex after the generic 2 second timeout while ByeDPI still needed its native/proxy drain window, allowing the next start to collide with busy native state.

The architectural review accepted `EnginePlugin.stopTimeoutMs()` as the owning layer because the engine knows its legal stop duration. This differs from a blind sleep: the orchestrator still owns sequencing, while ByeDPI declares its lifecycle contract. The finding connects to [[concepts/byedpi-cmd-verbatim-pipeline]] and [[concepts/byedpi-stale-serverfd-unconditional-forceclose]] because both preserve exact engine/native lifecycle behavior instead of patching symptoms.

## Related Concepts
- [[concepts/byedpi-stale-serverfd-unconditional-forceclose]]
- [[concepts/byedpi-native-thread-join-race]]
- [[concepts/release-regression-evidence-checklist]]

## Sources
- [[daily/2026-05-28]]: Trace analysis found ByeDPI stop timeout at 2000 ms before repeat start failures.
- [[daily/2026-05-28]]: Review against `v0.2.11` showed two-phase ByeDPI stop behavior with `STOP_GRACE_MS`.
- [[daily/2026-05-28]]: Architectural review concluded the longer timeout is valid only as an engine-owned `stopTimeoutMs()` contract.
