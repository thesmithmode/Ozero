---
title: FPTN upstream readiness and IP callback flow
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN upstream readiness and IP callback flow

## Key Points
- FPTN startup must be checked against upstream order: fastest-server probe, login, DNS, WebSocket readiness, then assigned IP/TUN readiness.
- The token schema in the current upstream uses `port`; `vpn_port` was identified as a stale assumption and must not be introduced without new evidence.
- Returning success before DNS/WebSocket/IP-assignment readiness can make `start()` look successful while the native path is not ready.
- Cancellation during auth fallback must stop the candidate loop instead of becoming another ordinary auth error.
- This concept complements [[concepts/fptn-upstream-dns-websocket-boundary]] and [[concepts/fptn-cancellation-cooperative-auth-lifecycle]].

## Details
The 2026-05-29 investigation imported an upstream FPTN snapshot into `.codex/Контекст/FPTN` from `fptn-project/fptn` commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e`. That comparison invalidated the earlier idea that Ozero should add `vpn_port`; the current upstream token/server schema uses `port`, so a fix based on `vpn_port` would be protocol drift rather than parity.

The durable discrepancy is lifecycle/readiness order. Upstream behavior was described as `FindFastestServer` using `testfile.bin`, then `Login`, then `GetDns`, then WebSocket startup, and finally interface readiness after an IP assignment callback. Ozero's risk is treating earlier phases as enough to report engine success, while hardcoded IP/DNS behavior or missing `on_ip_assigned_callback` propagation leaves the native tunnel not fully ready.

The same session separated this protocol/readiness work from cancellation handling. The minimal committed FPTN cancellation fix made cancellation cooperative, but the next layer remains aligning DNS, IP assignment, and WebSocket readiness with the upstream lifecycle.

## Related Concepts
- [[concepts/fptn-upstream-dns-websocket-boundary]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-http-608-regression-baseline]]
- [[connections/release-regression-ci-vs-runtime-proof]]

## Sources
- [[daily/2026-05-29.md]] records the upstream import, the rejection of the stale `vpn_port` hypothesis, and the upstream phase order.
- [[daily/2026-05-29.md]] records that Ozero still needed follow-up work on `/api/v1/dns`, `testfile.bin`, IP assignment callback, and TUN readiness.
