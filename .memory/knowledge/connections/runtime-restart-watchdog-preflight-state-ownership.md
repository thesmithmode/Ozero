---
title: Runtime restart, watchdog, and preflight state ownership
sources:
  - daily/2026-06-02.md
created: 2026-06-02
updated: 2026-06-02
---

# Runtime restart, watchdog, and preflight state ownership

## Key Points
- Runtime restart, watchdog failure handling, and auto-preflight all depend on the same active-attempt ownership boundary.
- Background config changes must be handled by process/service scope, while UI state remains observational.
- Stale engine failures and empty candidate sets must not override active tunnel ownership.
- Baseline advancement must wait for real service state, not intent enqueue or UI target hints.
- This connection joins [[concepts/runtime-restart-service-owned-action]], [[concepts/watchdog-active-engine-failure-filter]], and [[concepts/auto-preflight-all-fail-stop-contract]].

## Details

The 2026-06-02 fixes reveal one underlying relationship: lifecycle correctness depends on a single authoritative owner of active attempt state. Runtime restart was wrong when Activity lifecycle or app-level intents acted as owners. Watchdog was wrong when inactive engine failure could stop the active tunnel. Auto-preflight was wrong when UI target state could keep a failed attempt in `Probing`.

In all three cases, the right boundary is service/process state. The application-scope observer can detect background config changes, but `OzeroVpnService` owns the actual restart action. `EngineWatchdogCoordinator` must check the active `TunnelState` engine before acting. Auto-preflight must stop when no candidate survived, regardless of UI hints.

The non-obvious connection is that these are not separate bugs in restart, watchdog, and auto mode. They are different manifestations of stale or non-authoritative state being allowed to drive lifecycle transitions.

## Related Concepts

- [[concepts/runtime-restart-application-scope-observer]]
- [[concepts/runtime-restart-service-owned-action]]
- [[concepts/watchdog-active-engine-failure-filter]]
- [[concepts/auto-preflight-all-fail-stop-contract]]

## Sources

- [[daily/2026-06-02]]: Sessions 19:53, 20:45, and 21:12 connected runtime restart ownership, watchdog stale-engine filtering, and auto-preflight terminal cleanup.
- [[daily/2026-06-02]]: Session 20:45 identified the baseline race between enqueueing start and actual startup state.
- [[daily/2026-06-02]]: Session 21:12 required restart success/failure feedback before fingerprint baseline advancement.
