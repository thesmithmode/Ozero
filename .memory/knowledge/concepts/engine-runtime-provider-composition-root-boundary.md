---
title: Engine Runtime Provider Composition Root Boundary
sources:
  - daily/2026-05-31.md
created: 2026-06-01
updated: 2026-06-01
---
# Engine Runtime Provider Composition Root Boundary

## Key Points
- Generic runtime restart orchestration must not import concrete engine stores or prefs.
- Engine-specific runtime config providers are allowed in the composition root where DI wiring belongs.
- Moving concrete imports from `MainActivity` into another app-level helper is not enough if the helper still owns engine details.
- The stable boundary is an `engines-core` provider contract plus multibinding implementations near owning stores.

## Details

The 2026-05-31 restart-observer review found that extracting logic from `MainActivity` into `app/vpn` did not fully remove coupling. If `app/vpn` still knows `FptnConfigStore`, `WarpConfigSlotStore`, `SingboxPrefs`, or `SingboxProbeService`, the dependency only moved location. The generic orchestrator should collect providers through a core contract instead.

The accepted nuance is that `app/di` can remain the composition root. Engine-specific provider implementations may be wired there because dependency composition is the purpose of that layer. This narrows [[concepts/engine-runtime-config-provider-boundary]]: concrete engine knowledge is forbidden in generic orchestration, but permitted in the DI root as wiring, not behavior.

## Related Concepts
- [[concepts/engine-runtime-config-provider-boundary]]
- [[concepts/modular-boundary-engine-specific-logic]]
- [[connections/runtime-config-restart-boundary-loop]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]

## Sources
- [[daily/2026-05-31]]: Reviewer rejected an extraction that left `app/vpn` importing concrete engine stores.
- [[daily/2026-05-31]]: The provider-boundary was refined as a composition-root exception: `app/di` may wire engine-specific providers, while generic `app/vpn` must remain store-agnostic.
