---
title: FPTN upstream DNS and WebSocket boundary
sources:
  - [[daily/2026-05-29]]
created: 2026-05-29
updated: 2026-05-29
---
# FPTN upstream DNS and WebSocket boundary

## Key Points
- FPTN fixes must be checked against the canonical upstream flow, not inferred from local token failures.
- The current upstream token/server schema uses `port`; the earlier `vpn_port` assumption is stale.
- Upstream resolves the selected server host to IPv4 before constructing the WebSocket client.
- Ozero must not pass a domain host into native WebSocket code that expects an IPv4 address.
- Cancellation during auth fallback is a lifecycle signal, not a normal auth failure.

## Details

The 2026-05-29 investigation imported upstream FPTN from `fptn-project/fptn` at commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e` into `.codex/Контекст/FPTN`. This invalidated the earlier hypothesis that Ozero needed a `vpn_port` field: upstream uses `port`, so implementing `vpn_port` would have been a protocol regression.

The confirmed boundary problem is after successful login: Ozero passed the token `server.host` as `serverIp` into native code, while upstream resolves `selected_server.host` through `ResolveDomain()` before creating `WebsocketClient`. Native `WrapperWebsocketClient::Run()` expects an IPv4 value via `IPv4Address::Create(server_ip_)`, so a domain string at that boundary can break WebSocket setup even when the token and auth are valid.

The same session separated lifecycle protection from protocol alignment. `CancellationException` during fallback must stop the auth cycle instead of being treated as another failed server, otherwise old FPTN starts can continue after stop/switch and poison later engine transitions.

## Related Concepts
- [[concepts/fptn-dead-server-fallback]]
- [[concepts/fptn-http-608-regression-baseline]]
- [[concepts/engine-switch-failure-containment]]
- [[concepts/release-last-good-baseline-audit]]

## Sources
- [[daily/2026-05-29]]: upstream snapshot was saved from `fptn-project/fptn` commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e`.
- [[daily/2026-05-29]]: `vpn_port` was explicitly rejected because current upstream schema uses `port`.
- [[daily/2026-05-29]]: Ozero passed `server.host` into native WebSocket as `serverIp`, while upstream resolves the host before constructing the WebSocket client.
- [[daily/2026-05-29]]: FPTN auth fallback continuing after stop/switch was identified as a cancellation lifecycle bug.
