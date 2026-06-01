---
title: Runtime Restart Pending Fingerprint Baseline
sources:
  - daily/2026-05-31.md
created: 2026-06-01
updated: 2026-06-01
---
# Runtime Restart Pending Fingerprint Baseline

## Key Points
- Pending restart replay after startup must store the latest fingerprint and the applied baseline, not only a textual reason.
- Replay is valid only while the current fingerprint still differs from the baseline and the same engine startup path reaches `Connected`.
- Edit-and-revert during `Probing` or `Connecting` must collapse to no-op and must not restart after connection.
- The policy is engine-specific: FPTN may opt into replay, while sing-box startup profile writes must not self-restart.

## Details

The restart observer work on 2026-05-31 found a race in pending runtime config replay. A pending restart that stores only a reason can survive an edit followed by a revert during startup. When the engine later reaches `Connected`, that stale pending reason can trigger an unnecessary restart even though the effective config returned to the applied baseline.

The corrected contract is `PendingRestart(fingerprint, baseline)`. The observer replays only if the current fingerprint still differs from the baseline captured at startup and the engine path has not changed. This refines [[concepts/settings-restart-baseline-debounce-state-machine]] and [[concepts/fptn-runtime-fingerprint-replay-contract]] by making pending replay state value-based instead of reason-based.

## Related Concepts
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]
- [[concepts/engine-runtime-config-provider-boundary]]
- [[connections/runtime-config-restart-boundary-loop]]

## Sources
- [[daily/2026-05-31]]: Reviewer found that FPTN pending replay stored only reason and could restart after edit then revert during startup.
- [[daily/2026-05-31]]: The fix used `PendingRestart(fingerprint, baseline)` and confirmed no-op debounce semantics for `BYEDPI -> WARP -> BYEDPI` returning to baseline.
