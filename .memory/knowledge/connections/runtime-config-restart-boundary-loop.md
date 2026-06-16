---
title: Runtime Config Restart Boundary Loop
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Runtime Config Restart Boundary Loop

## Key Points
- Runtime restart correctness depends on both architectural ownership and temporal trigger semantics.
- Engine-owned provider semantics define what changed; `app/di` may wire them as the current composition root.
- The app-level state machine decides when a change is safe to restart without importing concrete engine stores.
- FPTN needs replay-after-starting, while sing-box must avoid generic replay that turns startup writes into self-restarts.
- WARP readiness illustrates the same boundary: lifecycle orchestration must not invent engine-specific readiness success.

## Details

The late 2026-05-31 restart review connected several issues that can look independent: app-level coupling to engine stores, debounce losing early edits, FPTN restarts on ignored fields, and WARP false-connected risk. They are one loop because the restart observer has to know what changed, when it changed, and whether a restart is safe for that engine.

The non-obvious relationship is that reducing coupling alone can still leave behavior wrong if trigger generation remains debounce-fragile, and a better state machine can still be wrong if it compares app-owned partial fields instead of engine-owned runtime fingerprints. The stable design combines [[concepts/engine-runtime-config-provider-boundary]] with [[concepts/settings-restart-baseline-debounce-state-machine]]. Under the current Hilt layout, concrete wiring remains in `app/di`; lifecycle orchestration in `MainActivity` and `app/vpn` remains generic.

Engine-specific exceptions then become explicit provider policy. FPTN can opt into replay-after-starting through [[concepts/fptn-runtime-fingerprint-replay-contract]], while sing-box can avoid replay for startup profile writes. WARP uses the same principle on readiness: orchestration gives the engine enough room to prove readiness, but it does not substitute a generic timeout success for a real handshake.

## Related Concepts
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]
- [[concepts/warp-readiness-delayed-handshake-contract]]

## Sources
- [[daily/2026-05-31]]: Session 23:08 connects provider boundary, FPTN replay policy, debounce races, and no-op restart prevention.
- [[daily/2026-05-31]]: Session 22:09 connects WARP delayed readiness and restart observer correctness under the same lifecycle quality review.
