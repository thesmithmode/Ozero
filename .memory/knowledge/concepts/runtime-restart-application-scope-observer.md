---
title: Runtime restart application scope observer
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Runtime restart application scope observer

## Key Points
- Runtime config restart observation must not depend on `MainActivity` visibility.
- Background updates can change active engine config while the VPN keeps running.
- The coordinator should start from `OzeroApp` in the main process and use application context.
- `MainActivity` should remain a UI observer, not the owner of runtime restart logic.
- This strengthens [[concepts/engine-runtime-config-restart-observer-stateflow-tests]] and [[concepts/runtime-restart-pending-fingerprint-baseline]].

## Details

The 2026-06-02 review found a real bug: `EngineRuntimeConfigRestartObserver` lived under `MainActivity` lifecycle and used `flowWithLifecycle(..., STARTED)`. When the UI moved to the background, runtime fingerprint observation stopped even though the VPN service continued to run.

This matters because config can change without a foreground Activity. `SubscriptionUpdateWorker` can update a Singbox profile or chain through WorkManager while the user is not looking at the UI. If the restart observer is paused, the VPN keeps running with stale runtime config until the Activity starts again or the user manually reconnects.

The fix direction is process ownership: start a coordinator from `OzeroApp` in the main process, observe `TunnelController.state`, and trigger restart through application context. Engine/provider contracts remain stable, and `MainActivity` only observes state for UI rendering.

## Related Concepts

- [[concepts/engine-runtime-config-restart-observer-stateflow-tests]]
- [[concepts/runtime-restart-pending-fingerprint-baseline]]
- [[concepts/engine-runtime-provider-composition-root-boundary]]
- [[connections/runtime-config-restart-boundary-loop]]

## Sources

- [[daily/2026-06-02]]: Session 19:53 recorded the review finding that Activity lifecycle paused runtime fingerprint observation while VPN continued.
- [[daily/2026-06-02]]: Session 19:53 identified `SubscriptionUpdateWorker` profile/chain updates as a background path that needs restart observation.
- [[daily/2026-06-02]]: Session 19:53 decided to move runtime restart observation into process/application scope and keep `MainActivity` as UI-only.
---
title: "Runtime restart observer must live in application scope"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# Runtime restart observer must live in application scope
## Key Points
- Runtime restart observation must not depend on `MainActivity` visibility when background work can change active engine config.
- The observer belongs in application or process scope so it can see fingerprint changes while the UI is backgrounded.
- `flowWithLifecycle(..., STARTED)` is too narrow for restart detection in a running VPN process.
- UI lifecycle can remain a presentation observer, but it should not own restart authority.
## Details
The restart observer was originally treated as a screen-lifecycle concern, but the daily log showed that WorkManager-backed updates can change the active Singbox or engine configuration while no activity is visible. If observation is tied to `MainActivity`, the restart signal is delayed until the UI returns, which breaks runtime correctness for a background VPN.

The corrected boundary is process-wide observation: the restart coordinator starts from the application process, watches the engine state and runtime fingerprints, and delegates restart behavior to the service-owned action in [[concepts/runtime-restart-service-owned-action]]. This keeps the decision path alive independently of UI visibility and aligns with [[concepts/engine-runtime-config-provider-boundary]].
## Related Concepts
- [[concepts/runtime-restart-service-owned-action]]
- [[connections/runtime-restart-watchdog-preflight-state-ownership]]
- [[concepts/engine-runtime-config-provider-boundary]]
## Sources
- `daily/2026-06-02.md`: the log states that `EngineRuntimeConfigRestartObserver` must move out of `MainActivity` lifecycle because background workers can update config while the app is backgrounded.
