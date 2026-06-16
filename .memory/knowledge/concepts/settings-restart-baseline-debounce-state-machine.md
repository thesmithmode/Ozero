---
title: Settings Restart Baseline Debounce State Machine
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Settings Restart Baseline Debounce State Machine

## Key Points
- Runtime settings restart triggers need an explicit baseline/debounce state machine, not a fragile `debounce -> runningFold -> drop` flow.
- The first config emission must establish baseline immediately so early real edits inside the first debounce window are not lost.
- A quick no-op burst such as BYEDPI -> WARP -> BYEDPI must not restart the active BYEDPI engine.
- Debounced flows hide intermediate states, so `previous/current` values after debounce cannot always be treated as user-visible edit history.

## Details

The restart observer review on 2026-05-31 found that placing `debounce()` before `runningFold()` and `drop(2)` could discard the first real settings change during the first four seconds after observer startup. Tests that always advanced time after baseline setup missed this race because they never exercised a real edit inside the initial debounce window.

The correct model is an explicit state machine. The first emission records baseline without waiting for debounce. Later bursts are compared against that baseline after the debounce window, and only a net runtime-relevant change emits a restart trigger. If the final value returns to baseline, no trigger is emitted. This handles debounced engine toggles and repeated identical startup snapshots without treating hydration as a real config edit.

This state machine belongs with [[concepts/engine-runtime-config-provider-boundary]] because the comparison should use runtime fingerprints supplied by engine-owned providers. It also supports [[concepts/vpn-switch-confirm-stop-before-start]] by avoiding restarts that are not backed by a real settled config change.

## Related Concepts
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]
- [[concepts/vpn-switch-confirm-stop-before-start]]
- [[concepts/debounce-split-heterogeneous-flow]]

## Sources
- [[daily/2026-05-31]]: Session 23:08 records the critical race where `debounce()` before `runningFold()` and `drop(2)` could drop the first real change after startup.
- [[daily/2026-05-31]]: Session 22:09 records edge cases for identical startup snapshots, same-engine config changes during startup, and no-op debounce toggle-back.
