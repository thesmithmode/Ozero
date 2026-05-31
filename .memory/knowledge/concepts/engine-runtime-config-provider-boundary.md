---
title: Engine Runtime Config Provider Boundary
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---
# Engine Runtime Config Provider Boundary

## Key Points
- Runtime restart observation must not make app runtime orchestration depend on engine-specific stores such as FPTN, WARP, or sing-box preferences.
- The shared boundary is an `EngineRuntimeConfigProvider` contract in `engines-core`, with engine-owned implementations contributed through DI multibinding.
- `app/vpn` should collect providers and compare runtime fingerprints without knowing concrete config models.
- `app/di` is the composition root in the current project layout, so it may wire engine stores into providers; that exception must not leak into `MainActivity` or `app/vpn`.
- A sentinel that only removes engine-specific imports from `MainActivity` is insufficient if the coupling moves to another app-level helper.

## Details

The 2026-05-31 restart review found that moving engine restart logic out of `MainActivity` was not enough when the new helper still imported `FptnConfigStore`, `WarpConfigSlotStore`, `SingboxPrefs`, or `SingboxProbeService`. That only relocated engine coupling into runtime orchestration. In the current project layout, Hilt engine bindings already live in `app/di`, so `app/di` is treated as the composition-root exception: it may wire concrete engine stores, while `MainActivity` and `app/vpn` must stay generic.

The chosen boundary is a shared provider contract: engine modules expose runtime-relevant fingerprints through multibinding, and app-level restart code observes a collection of providers. This keeps orchestration generic and lets each engine define what settings actually affect its running process. The same boundary supports [[concepts/settings-restart-baseline-debounce-state-machine]] because trigger generation can compare provider fingerprints instead of hard-coded config fields.

This pattern also protects [[concepts/modular-boundary-engine-specific-logic]] and [[concepts/per-engine-ui]]: app-level code can coordinate lifecycle, but it should not encode engine-local storage or profile semantics. If a new engine needs restart behavior, it contributes a provider beside its owning config store rather than extending a central `when(engine)` block.

## Related Concepts
- [[concepts/modular-boundary-engine-specific-logic]]
- [[concepts/settings-restart-baseline-debounce-state-machine]]
- [[concepts/fptn-runtime-fingerprint-replay-contract]]
- [[connections/engine-startup-status-authority-boundary]]

## Sources
- [[daily/2026-05-31]]: Session 23:08 records the reviewer finding that app-level helpers still knew `FptnConfigStore`, `WarpConfigSlotStore`, `SingboxPrefs`, and `SingboxProbeService`.
- [[daily/2026-05-31]]: Session 23:08 records the decision to introduce `EngineRuntimeConfigProvider` in `engines-core` with Hilt multibinding implementations.
