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
