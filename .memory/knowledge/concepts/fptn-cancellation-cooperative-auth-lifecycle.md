---
title: FPTN cancellation-cooperative auth lifecycle
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN cancellation-cooperative auth lifecycle

## Summary
FPTN startup must treat cancellation as a lifecycle boundary, not as another authentication failure. Serial fallback over multiple servers can otherwise continue after stop/switch and poison the shared start/stop orchestration.

## Key Points
- Default `FptnEngine.start()` must not run an unbounded `authenticateFirstAvailable()` ladder in the critical startup path.
- `CancellationException` must stop candidate traversal and propagate lifecycle cancellation instead of being converted to ordinary auth failure.
- Upstream FPTN readiness is a sequence: probe, login, DNS, WebSocket, and assigned IP callback, not just login success.
- The current token schema uses `port`; the earlier `vpn_port` direction is stale and must not be implemented without new evidence.
- This concept intersects with [[concepts/fptn-upstream-readiness-ip-callback-flow]] and [[concepts/auto-candidate-terminal-status-invariant]].

## Details
The 2026-05-29 investigation compared current FPTN behavior against `v0.2.0`, local logs, and upstream `fptn-project/fptn`. It found that long serial auth fallback can continue after a stop or switch request. With `AUTH_TIMEOUT_S=15` per candidate, this creates a timeout ladder that blocks shared orchestration and makes later failures appear under neighboring engines.

The same investigation corrected a false lead: upstream does not require a `vpn_port` token field for the current schema. The confirmed next layer is lifecycle/readiness alignment: startup should follow upstream order through test/probe, login, DNS, WebSocket readiness, and assigned IP callback. Cancellation is a separate safety boundary so a stopped startup cannot keep authenticating candidates in the background.

## Related Concepts
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]
- [[concepts/fptn-upstream-websocket-dns-boundary]]
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[connections/stale-engine-signals-cross-engine-failures]]

## Sources
- [[daily/2026-05-29]]: upstream comparison showed `port` rather than `vpn_port`, and established the probe/login/DNS/WebSocket/IP callback order.
- [[daily/2026-05-29]]: logs showed FPTN auth fallback continuing after stop/switch, making cancellation-cooperative auth a separate fix target.
