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
