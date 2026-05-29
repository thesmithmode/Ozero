---
title: "FPTN WebSocket DNS boundary"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# FPTN WebSocket DNS boundary
## Key Points
- FPTN native WebSocket must receive a resolved IPv4 address, not the token host string, because native `IPv4Address::Create(server_ip_)` expects an IP-shaped value.
- Upstream `fptn-project/fptn` at `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e` resolves `selected_server.host` before constructing the WebSocket client.
- The current upstream token schema uses `port`; the earlier `vpn_port` hypothesis was disproved and must not be implemented without new evidence.
- DNS/WebSocket alignment is a boundary fix separate from the cancellation fix in [[concepts/fptn-cancellation-cooperative-auth-lifecycle]].
## Details
The durable finding from the 2026-05-29 investigation is that Ozero must preserve the upstream FPTN startup order around DNS and WebSocket creation. The local implementation had evidence of passing token `server.host` into the native WebSocket path as `serverIp`, while upstream resolves the host to an IPv4 address before constructing the WebSocket client.

This issue is not a token-validity problem and not a `vpn_port` migration. The upstream snapshot imported into `.codex/Контекст/FPTN` shows the schema uses `port`, so changes that invent `vpn_port` would diverge from the reference implementation. The correct fix direction is to resolve the selected token host after login and carry that IPv4 value into native WebSocket startup while preserving SNI, fallback behavior, native ABI, and token schema.

This boundary belongs with the broader FPTN readiness model in [[concepts/fptn-upstream-readiness-ip-callback-flow]]: `testfile`/server selection, login, DNS, WebSocket readiness, and assigned IP callback are separate steps. Returning success before this chain is actually ready creates false-positive engine readiness and later lifecycle noise.
## Related Concepts
- [[concepts/fptn-upstream-readiness-ip-callback-flow]] - Defines the wider upstream startup sequence that DNS/WebSocket must fit into.
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]] - Separates cancellation behavior from DNS/WebSocket correctness.
- [[concepts/fptn-auth-ladder-orchestrator-block]] - Explains why serial auth fallback must not block orchestrator transitions.
- [[connections/startup-readiness-runtime-recovery-boundary]] - Generalizes why startup readiness should remain bounded.
## Sources
- [[daily/2026-05-29]]: records importing upstream FPTN commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e` and rejecting the stale `vpn_port` assumption.
- [[daily/2026-05-29]]: records the native/WebSocket evidence that Ozero passed `server.host` where upstream resolves host to IPv4 first.
- [[daily/2026-05-29]]: records that the FPTN DNS-boundary fix was committed as `bd284d81` while CI waiting was deferred by user instruction.
