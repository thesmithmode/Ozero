---
title: FPTN upstream snapshot grounding
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN upstream snapshot grounding

## Summary
FPTN fixes in Ozero must be grounded against a captured upstream reference before changing token schema, DNS flow, readiness, or native WebSocket behavior.

## Key Points
- A local upstream snapshot from `fptn-project/fptn` was imported into `.codex/Контекст/FPTN` for traceable comparison.
- The current upstream token/server schema uses `port`; the earlier `vpn_port` hypothesis was explicitly rejected.
- Upstream startup is a sequence, not a simple login success: server probe, login, DNS, WebSocket, then IP assignment readiness.
- Ozero FPTN fixes should preserve upstream-compatible schema while addressing lifecycle, DNS, cancellation, and readiness boundaries.

## Details
The 2026-05-29 investigation showed that FPTN debugging can go wrong if it starts from local assumptions instead of the upstream client. A suspected `vpn_port` schema mismatch was disproved after checking the upstream repository: the current protocol still uses `port`. This made the proposed `vpn_port` fix invalid without new evidence.

The durable rule is to treat the upstream snapshot as the behavioral reference for protocol order and field semantics. Ozero-specific fixes may still be necessary, but they must be described as deviations from or restorations of upstream behavior, not as guessed protocol changes. This applies especially to the DNS/WebSocket boundary, cancellation, and readiness callback path.

## Related Concepts
- [[concepts/fptn-token-port-schema-upstream-contract]]
- [[concepts/fptn-upstream-dns-websocket-boundary]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]

## Sources
- [[daily/2026-05-29]]: upstream FPTN snapshot was imported from `fptn-project/fptn` and used to reject the stale `vpn_port` hypothesis.
- [[daily/2026-05-29]]: upstream flow was identified as `FindFastestServer`/test file, `Login`, `GetDns`, WebSocket, and IP assignment callback.
- [[daily/2026-05-29]]: Ozero defects were narrowed to cancellation, DNS/WebSocket IP resolution, and readiness flow rather than token invalidity.
