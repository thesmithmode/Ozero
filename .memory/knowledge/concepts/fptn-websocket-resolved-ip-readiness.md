---
title: FPTN WebSocket needs resolved IP and readiness order
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- FPTN WebSocket startup must pass a resolved IPv4 address to native code, not the token host string.
- Upstream FPTN flow is ordered as probe, login, DNS, WebSocket, then assigned-IP readiness.
- The `vpn_port` hypothesis was rejected because upstream uses `port`.
- Cancellation of auth fallback is a lifecycle guard, not a replacement for protocol readiness.

## Details
The 2026-05-29 investigation compared Ozero FPTN behavior with upstream `fptn-project/fptn` and found that the native WebSocket layer expects an IP address after domain resolution. The earlier `vpn_port` direction was explicitly rejected because the upstream schema uses `port`, so fixes must preserve token schema unless new evidence appears.

The same investigation separated two FPTN layers. First, auth fallback must stop cooperatively when cancellation or engine switching happens, so stale starts do not continue after stop. Second, successful `login` alone is not enough readiness: upstream proceeds through DNS resolution and WebSocket setup before the interface should be considered ready.

## Related Concepts
- [[concepts/fptn-upstream-dns-websocket-boundary]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-token-port-schema-upstream-contract]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]

## Sources
- [[daily/2026-05-29]] records that upstream FPTN was imported and used as the reference for schema and lifecycle behavior.
- [[daily/2026-05-29]] records that `vpn_port` was rejected and that `port` is the upstream token field.
- [[daily/2026-05-29]] records the WebSocket boundary: Ozero passed a host string where upstream resolves host to IPv4 before native WebSocket creation.
