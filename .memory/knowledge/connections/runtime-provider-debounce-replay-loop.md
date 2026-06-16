---
title: Runtime Provider Debounce Replay Loop
sources:
  - daily/2026-05-31.md
created: 2026-06-09
updated: 2026-06-09
---
# Runtime Provider Debounce Replay Loop

## Key Points
- Runtime restart correctness depends on three layers working together: engine-owned providers, explicit debounce baseline state, and per-engine pending replay policy.
- Provider fingerprints keep `app/vpn` generic while letting each engine decide which config fields affect the running process.
- Debounce state must compare the final burst value against baseline, so no-op toggle-back bursts do not restart the active engine.
- Pending replay is necessary only for engines such as FPTN where real edits during startup must apply after `Connected`.
- Pending replay must carry fingerprint and baseline to avoid stale restart after edit-revert during startup.

## Details

The 2026-05-31 restart review showed that none of the pieces is sufficient alone. Moving logic out of `MainActivity` still leaves coupling if an app-level helper imports concrete stores. Debouncing alone can lose early edits if baseline setup is delayed. Replay alone can create stale restarts if it stores only a reason instead of the exact fingerprint and baseline.

The stable architecture is a loop. Engine-owned `EngineRuntimeConfigProvider` implementations expose runtime-relevant fingerprints through DI. A generic restart observer records baseline immediately, debounces user-visible bursts, and emits only net changes. Engines that can receive real runtime edits during startup opt into replay-after-starting, while engines with startup hydration writes avoid generic self-restart behavior.

This connection ties [[concepts/engine-runtime-config-provider-boundary]] to [[concepts/settings-restart-baseline-debounce-state-machine]] and [[concepts/fptn-runtime-fingerprint-replay-contract]]. It also constrains [[concepts/engine-settings-restart-startup-runtime-match]]: matching only the target engine is too weak, but comparing raw app-level fields is too coupled.

## Related Concepts
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]
- [[concepts/engine-settings-restart-startup-runtime-match]]

## Sources
- [[daily/2026-05-31]]: Session 23:08 records the provider-boundary review, the debounce race, and the explicit replay-after-starting policy for FPTN.
- [[daily/2026-05-31]]: Session 23:44 records the refinement that pending replay must store fingerprint and baseline to avoid stale restart after edit-revert.
