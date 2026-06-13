---
title: FPTN token port schema upstream contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN token port schema upstream contract

## Key Points
- Upstream FPTN token/server schema uses `port`; `vpn_port` was an unverified stale assumption.
- Fixes must not introduce `vpn_port` unless a newer upstream source proves the schema changed.
- FPTN regressions in this session were traced to lifecycle/readiness/auth flow, not to a malformed token.
- The upstream snapshot should be used as the protocol reference before changing token parsing.

## Details
The 2026-05-29 investigation imported the upstream FPTN reference into `.codex/Контекст/FPTN` from `fptn-project/fptn` commit `32085947ff3c3626f7ec64eb183cbc6b8dcfea3e`. That check invalidated an earlier hypothesis that Ozero needed a separate `vpn_port` field. The confirmed upstream schema uses `port`, so changing Ozero to expect `vpn_port` would create a protocol regression rather than fix the observed startup issue.

The user explicitly confirmed that the FPTN token was working and that the defect had to be found in Ozero code/config flow. Subsequent analysis moved the fix direction toward cancellation-cooperative auth, DNS/WebSocket boundary handling, and readiness parity with upstream rather than token invalidation.

## Related Concepts
- [[concepts/fptn-upstream-websocket-dns-boundary]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-auth-ladder-orchestrator-block]]

## Sources
- [[daily/2026-05-29]] records that upstream FPTN uses `port`, not `vpn_port`, and that the `vpn_port` hypothesis was withdrawn.
- [[daily/2026-05-29]] records that the user identified the token as valid and directed the investigation toward Ozero's code/config path.
