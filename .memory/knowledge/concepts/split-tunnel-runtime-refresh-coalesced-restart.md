---
title: Split tunnel runtime refresh needs cache invalidation and conflated restart
sources:
  - daily/2026-05-31.md
created: 2026-05-31
updated: 2026-05-31
---

# Split tunnel runtime refresh needs cache invalidation and conflated restart

## Key Points
- Installed-app lists must refresh on package-change events and screen resume, not only at initial provider construction.
- Refresh should invalidate list, icon, and missing-app caches without resetting a populated UI into `Loading`.
- UI naming should use "Tunneling" semantics for the expert dock instead of ambiguous split-only wording.
- Restart coalescing must preserve the latest pending settings change during an in-flight restart.
- A stale `Idle` state after stop is not a sufficient settled signal for the second restart.

## Details

The split tunneling fix established that `DefaultAppListProvider` caching can hide newly installed or removed apps during runtime. The app list must be refreshed from package receiver events and lifecycle resume while keeping current rules and query state visible. This avoids a heavy UI reset and keeps the user in context.

The restart logic also needed a stronger coalescing model. A simple `Mutex.tryLock()` drop can lose the final settings change if a second update arrives while a restart is in flight. The safer contract is conflated pending restart: keep the latest request and wait for a real settled state such as `Connected` or `Failed`, not a stale `Idle` emitted during stop.

## Related Concepts
- [[concepts/split-tunnel-internet-permission-filter]]
- [[concepts/vpn-switch-confirm-stop-before-start]]
- [[concepts/visual-connected-switching-state]]
- [[connections/engine-startup-status-authority-boundary]]

## Sources
- [[daily/2026-05-31]]: sessions 11:46, 12:27, 13:07, 15:03, and 17:08 describe package refresh, no-loading refresh, expert dock rename, lost restart risk, and settled-state correction.
