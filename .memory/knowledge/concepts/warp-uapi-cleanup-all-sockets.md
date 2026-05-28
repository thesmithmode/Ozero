---
title: WARP UAPI cleanup all sockets
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---
# WARP UAPI cleanup all sockets

## Summary
WARP startup must clean every stale UAPI `.sock` under the socket directory, not only the socket for the current tunnel name, because stale tunnel sockets can leak old handshake state into a new start.

## Key Points
- The regression trace showed WARP starting with suspicious stale or reused tunnel socket state after ByeDPI failures.
- Cleaning only the current `<tunnel>.sock` was insufficient when old `tunN.sock` files remained.
- The accepted fix removes all WARP UAPI `.sock` files before `awgTurnOn` and proxy start.
- This is a root-cause cleanup for stale UAPI state, not a readiness timeout.

## Details
On 2026-05-28, WARP failures were investigated together with ByeDPI because a broken ByeDPI transition had previously caused WARP regressions. The trace suggested a stale UAPI socket path: the engine could read handshake data from an old socket even when the current named socket had been removed.

The durable rule is that WARP startup cleanup must cover the whole WARP UAPI socket directory. This extends [[concepts/warp-uapi-stale-socket-cleanup]] and supports the broader [[connections/release-regression-ci-vs-runtime-proof]] lesson: green CI and release publication do not prove runtime engine state is fresh unless logs and cleanup contracts are checked.

## Related Concepts
- [[concepts/warp-uapi-stale-socket-cleanup]]
- [[concepts/warp-uapi-handshake-polling]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-28]]: Trace analysis identified suspicious stale or reused WARP tunnel socket state.
- [[daily/2026-05-28]]: Fix cleaned all `uapiPath/sockets/*.sock`, not only the current tunnel socket.
- [[daily/2026-05-28]]: Review accepted this as a root-cause stale UAPI cleanup.
