---
title: FPTN upstream WebSocket DNS boundary
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---

# FPTN upstream WebSocket DNS boundary

## Key Points
- FPTN native WebSocket expects an IPv4 address, not the token host string.
- Upstream resolves `selected_server.host` before constructing the WebSocket client.
- The token schema uses `port`; the earlier `vpn_port` hypothesis was rejected.
- DNS-boundary fixes must preserve SNI, fallback behavior, token schema, and native ABI.
- This belongs with [[concepts/fptn-engine-design]] and [[concepts/fptn-http-608-regression-baseline]], not with token invalidation.

## Details

The 2026-05-29 investigation compared Ozero FPTN behavior with upstream `fptn-project/fptn` and found a boundary mismatch: Ozero passed token `server.host` as `serverIp` into `attachTun()`, while native C++ `WrapperWebsocketClient::Run()` builds an IPv4 address from `server_ip_`. Upstream resolves the selected server host before creating the WebSocket client.

This invalidated the earlier `vpn_port` direction. The imported upstream reference uses `port`, and the daily log explicitly marks `vpn_port` as stale. The proper fix direction is resolving host to IPv4 after login and carrying the resolved address into native WebSocket while leaving the token schema and protocol surface intact.

The same session also identified the larger upstream flow: `FindFastestServer` through `testfile`, then `Login`, `GetDns`, WebSocket readiness, and IP assignment callback. The DNS-boundary issue is one layer of this mismatch, related to [[concepts/fptn-cancellation-cooperative-auth-lifecycle]] but not identical to cancellation or server fallback.

## Related Concepts
- [[concepts/fptn-engine-design]]
- [[concepts/fptn-http-608-regression-baseline]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/private-subscription-sanitized-debugging]]

## Sources
- [[daily/2026-05-29]]: Imported upstream FPTN reference from `fptn-project/fptn` commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e`.
- [[daily/2026-05-29]]: Established that upstream uses `port`, not `vpn_port`.
- [[daily/2026-05-29]]: Found that Ozero passed host as `serverIp` while native WebSocket expects IPv4.
