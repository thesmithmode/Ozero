---
title: "Runtime restart should be a single service-owned action"
sources:
  - daily/2026-06-02.md
created: 2026-06-04
updated: 2026-06-04
---
# Runtime restart should be a single service-owned action
## Key Points
- Runtime restart should be handled by one service-owned action instead of a new foreground-service launch path.
- The service must restore foreground state before calling `startVpn()` again.
- Baseline fingerprints should advance only after the restart has actually succeeded.
- A restart must abort if a parallel user stop is already in flight.
## Details
The daily log converged on a single service action, `ACTION_RESTART_RUNTIME_CONFIG`, so restart happens inside the already running VPN service. That avoids a separate `startForegroundService` path and keeps shutdown/startup sequencing under one owner. The service can stop without `stopSelf`, return to foreground, and then restart the tunnel with the new runtime configuration.

The important contract change is that restart success is not inferred from enqueueing a request. The restart callback must report success or failure, and the runtime fingerprint baseline should move forward only after a successful restart. This prevents stale configuration from being treated as applied and matches the ownership boundary described in [[concepts/runtime-restart-application-scope-observer]] and [[connections/runtime-restart-watchdog-preflight-state-ownership]].
## Related Concepts
- [[concepts/runtime-restart-application-scope-observer]]
- [[concepts/auto-preflight-all-fail-stop-contract]]
- [[connections/runtime-restart-watchdog-preflight-state-ownership]]
## Sources
- `daily/2026-06-02.md`: the log records the move to a single service-owned restart action, foreground restoration before `startVpn()`, and baseline advancement only after successful restart.
