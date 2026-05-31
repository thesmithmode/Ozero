---
title: sing-box process stop-and-wait sentinel
sources:
  - daily/2026-05-30.md
created: 2026-05-31
updated: 2026-05-31
---
# sing-box process stop-and-wait sentinel

## Key Points
- sing-box process shutdown must be acknowledged before unbind, close, or profile-change restart proceeds.
- Fire-and-forget `stop()` can race with `close()/unbind` and leave stale runtime state or broken traffic path.
- `activeSocksPort` must not be published until runtime health is proven.
- CI is not green for changed production code when the touched module reports zero tests.
- This extends [[concepts/singbox-probe-socks-port-lifecycle]] and [[concepts/singbox-aidl-async-error-swallow]].

## Details

The 2026-05-30 investigation separated sing-box symptoms from WARP, FPTN, and MasterDNS: sing-box had a native crash before a later apparent start, then traffic and exit-IP probing still failed. The lifecycle finding was that process stop behaved as fire-and-forget; the engine could immediately close and unbind while the subprocess was still stopping, and profile changes could trigger restarts during unstable `Connecting` or `Probing` states.

The stable contract is to expose an acknowledged shutdown boundary such as `stopAndWait(timeoutMs)` plus a `runtimeRunning()` health query. The engine should wait for stop acknowledgement before unbind/close, suppress profile-change restarts while the runtime is unstable, and publish probe resources only after runtime health.

The same session found a validation gap: a green CI run did not prove sing-box process coverage because the changed `singbox-process` module had `0` tests. A sentinel test was added and a new CI run was required. For this module, N=0 is a false-green signal, not acceptance.

## Related Concepts
- [[concepts/singbox-probe-socks-port-lifecycle]]
- [[concepts/singbox-active-socks-port-failure-reset]]
- [[concepts/singbox-aidl-async-error-swallow]]
- [[concepts/ci-engine-module-missing-tests]]

## Sources
- [[daily/2026-05-30]]: sing-box showed native crash, later apparent start, missing real traffic, and exit IP timeout.
- [[daily/2026-05-30]]: The lifecycle root cause was fire-and-forget stop followed by close/unbind and restarts during unstable states.
- [[daily/2026-05-30]]: The accepted fix added acknowledged stop/runtime health checks and delayed `activeSocksPort` publication until runtime health.
- [[daily/2026-05-30]]: Artifact audit found `singbox-process` reported zero tests despite production-code changes, requiring a new sentinel and CI run.
