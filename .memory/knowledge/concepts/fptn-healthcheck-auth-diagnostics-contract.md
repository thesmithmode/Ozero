---
title: FPTN health-check auth diagnostics contract
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-05-30
---
# FPTN health-check auth diagnostics contract
## Summary
FPTN startup should separate candidate health-checking from bounded authentication and propagate structured failure reasons through the start pipeline to the UI.
## Key Points
- `autoSelect=true` may try candidates in order within one bounded startup budget, but must not run an unbounded serial `15s * N` auth ladder.
- `autoSelect=false` should try only the selected server and avoid silent fallback to unrelated candidates.
- HTTP 608 and hard timeouts need structured classification instead of a generic `FPTN authentication failed`.
- Reason propagation must be traced through `Engine.start`, `StartSequenceCoordinator`, `TunnelState.Failed`, and UI rendering.
## Details
The 2026-05-30 FPTN analysis clarified that the problem was not merely "fallback exists" or "fallback is missing". The unsafe shape was a serial authentication ladder inside the critical `start()` path, where each candidate could consume a long timeout and block the orchestrator. Historical `v0.2.0` behavior and upstream-like flow both point toward choosing or probing a candidate first, then authenticating a bounded target.

The user also provided logs with repeated HTTP 608 and hard startup timeouts across many candidates, ending in a generic `FPTN authentication failed`. That made the support problem worse: the system could not distinguish dead first endpoint, availability failure, auth failure, HTTP 608, cancellation, or timeout from the UI or logs.

The planned contract is a two-phase pipeline: health-check or candidate ordering first, then bounded authentication with structured reasons. Any new reason model must be verified end-to-end through the service and UI state pipeline; otherwise better diagnostics can be lost before the user sees them. Inline Kotlin-only error text is also a localization risk.
## Related Concepts
- [[concepts/fptn-auth-ladder-orchestrator-block]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-http-608-regression-baseline]]
- [[connections/startup-readiness-runtime-recovery-boundary]]
## Sources
- [[daily/2026-05-30]]: The user clarified that FPTN auto mode should not fail on the first dead server, but the issue was the serial `15s * N` ladder in critical startup.
- [[daily/2026-05-30]]: PR #76/#77 analysis proposed health-check, ordered auth, and structured failure reasons.
- [[daily/2026-05-30]]: The full chain `Engine.start -> StartSequenceCoordinator -> TunnelState.Failed -> UI` was identified as necessary for reason propagation.
- [[daily/2026-05-30]]: Direct Kotlin error strings were called out as a localization risk.
