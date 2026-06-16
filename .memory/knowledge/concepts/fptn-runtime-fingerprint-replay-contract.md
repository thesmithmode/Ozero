---
title: FPTN Runtime Fingerprint Replay Contract
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-06-09
---
# FPTN Runtime Fingerprint Replay Contract

## Key Points
- FPTN runtime fingerprint must include only fields the running engine actually uses.
- `selectedServerName` should affect the fingerprint only when `autoSelect=false`; in auto-select mode it is ignored by runtime.
- `includeStarting=false` prevents synthetic startup restarts, but it can lose real edits during `Probing` or `Connecting` without a pending replay policy.
- Replay after startup should be opt-in per provider; it is valid for FPTN runtime edits but unsafe as a generic rule for all engines.
- Pending replay must be tied to the exact fingerprint and baseline so edit-revert during startup collapses to no-op instead of restarting stale config.

## Details

The 2026-05-31 review found two FPTN-specific restart defects. First, `selectedServerName` was included in the fingerprint even when `autoSelect=true`, causing unnecessary restarts for a field not used by runtime in that mode. Second, edits made while FPTN was `Probing` or `Connecting` could update baseline but never restart after the engine reached `Connected`.

The fix contract is to make the FPTN provider compute a runtime-relevant fingerprint and explicitly enable replay-after-starting when needed. A config change during startup should be held as pending and replayed when the same engine transitions to `Connected`. This is not a generic policy because sing-box startup/profile writes can be internal hydration and should not automatically self-restart after readiness.

Later review tightened the pending replay contract. Storing only a restart reason is not enough: an edit followed by a revert during startup can leave a stale pending replay that fires after `Connected`. The pending value should carry the observed fingerprint and baseline, and replay should survive only while the latest fingerprint still differs from the applied baseline on the same engine path.

This concept extends [[concepts/fptn-single-auth-default-start-contract]] by separating startup stability from runtime config correctness. It also depends on [[concepts/settings-restart-baseline-debounce-state-machine]] so that FPTN edits are neither lost during startup nor misclassified as hydration.

## Related Concepts
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/fptn-single-auth-default-start-contract]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-31]]: Session 23:08 records that `selectedServerName` must be excluded from FPTN fingerprint when `autoSelect=true`.
- [[daily/2026-05-31]]: Session 23:08 records that `includeStarting=false` can lose real FPTN runtime edits during `Probing` or `Connecting` unless replay is explicit.
- [[daily/2026-05-31]]: Session 23:44 records the reviewer finding that pending replay must store fingerprint and baseline to avoid stale restart after edit-revert.
