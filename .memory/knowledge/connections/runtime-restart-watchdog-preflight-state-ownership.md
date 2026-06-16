---
title: "Runtime restart, watchdog, and preflight share one active-state ownership boundary"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# Runtime restart, watchdog, and preflight share one active-state ownership boundary
## Key Points
- Runtime restart must be observed in application scope, not UI scope.
- Restart must be executed by the already running service through a single owned action.
- Watchdog recovery must ignore stale failures from inactive engines.
- Auto preflight must stop the VPN when all engines fail, instead of leaving the tunnel half-started.
## Details
The daily log shows three bugs that are actually one boundary problem. If restart observation lives in `MainActivity`, runtime configuration changes from background work are missed. If restart is dispatched as a separate foreground-service launch, the service loses ownership of the stop/start sequence. If watchdog accepts stale failures, inactive engines can corrupt the active tunnel state. If auto preflight keeps running after every candidate fails, the system is left in an invalid transitional state.

The shared fix is to anchor all of these decisions to the active tunnel state inside the service/process boundary. Observation happens in application scope, execution stays inside the service, watchdog checks the active engine identity, and preflight failure is terminal. That boundary is captured by [[concepts/runtime-restart-application-scope-observer]], [[concepts/runtime-restart-service-owned-action]], [[concepts/watchdog-active-engine-failure-filter]], and [[concepts/auto-preflight-all-fail-stop-contract]].
## Related Concepts
- [[concepts/runtime-restart-application-scope-observer]]
- [[concepts/runtime-restart-service-owned-action]]
- [[concepts/watchdog-active-engine-failure-filter]]
- [[concepts/auto-preflight-all-fail-stop-contract]]
## Sources
- `daily/2026-06-02.md`: the log records that restart observation must move to application scope, restart must be service-owned, watchdog must ignore stale engine failures, and auto preflight must stop the VPN when every candidate fails.
