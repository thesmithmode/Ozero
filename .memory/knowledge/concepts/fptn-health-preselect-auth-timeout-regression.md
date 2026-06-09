---
title: FPTN health-preselect auth timeout regression
sources:
  - daily/2026-05-30.md
created: 2026-05-30
updated: 2026-06-09
---
# FPTN health-preselect auth timeout regression

## Summary
FPTN runtime regressions after a stable baseline can come from the startup selection policy itself: health-preselect may reorder candidates, shorten auth windows, and make valid tokens fail with repeated HTTP 608 timeouts.

## Key Points
- FPTN failures with repeated HTTP 608 should be compared against the last stable baseline before changing token schema or native startup behavior.
- Health-checking and authentication are separate phases; preselect must not silently create a worse auth order than the user-selected or token-defined server order.
- A very short per-candidate auth timeout can turn live but slow FPTN endpoints into false startup failures.
- Generic `FPTN authentication failed` hides whether the failure came from reachability, auth timeout, HTTP 608, or orchestration timeout.
- Fixes should keep full fallback out of the startup mutex unless it is a bounded, cancellable recovery path.

## Details
On 2026-05-30, runtime logs showed FPTN failing after v1.0.9 with repeated HTTP 608 authentication timeouts. The investigation identified a plausible regression source in the newer health-preselect path: current code could select one visible server, then authenticate against other candidates under a short budget, producing failures that did not match the stable user-visible behavior.

This differs from the known startup contract captured in [[concepts/fptn-healthcheck-auth-diagnostics-contract]] and [[concepts/fptn-single-auth-default-start-contract]]. Health checks may rank or filter endpoints, but the final auth path must remain bounded, explainable, and aligned with explicit selection semantics. When a candidate fails with HTTP 608, the UI and logs should expose that structured reason rather than collapsing it into a generic engine failure.

The regression also connects to [[concepts/fptn-http-608-regression-baseline]]: a stable release such as v1.0.9 is evidence for expected behavior, but only after the exact tag or SHA is grounded. Fixes should compare server order, health-preselect behavior, and auth timeout values before changing lower-level WebSocket, DNS, or token parsing code.

The same log refined the startup rule. `autoSelect=true` may try token servers inside one overall bounded startup budget, but `autoSelect=false` should try only the explicitly selected server. A serial `15s * N` auth ladder inside ordinary `start()` remains unsafe because it can block `ChainOrchestrator` transitions and make engine switching or shutdown look broken.

## Related Concepts
- [[concepts/fptn-healthcheck-auth-diagnostics-contract]]
- [[concepts/fptn-http-608-regression-baseline]]
- [[concepts/fptn-single-auth-default-start-contract]]
- [[concepts/fptn-auth-ladder-orchestrator-block]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]

## Sources
- [[daily/2026-05-30]]: Runtime diagnosis reported FPTN as a likely regression relative to v1.0.9, with HTTP 608 auth timeouts after health-preselect and short candidate auth windows.
- [[daily/2026-05-30]]: Earlier FPTN planning separated health-checking from bounded auth and required structured failure reasons to reach UI.
- [[daily/2026-05-30]]: The FPTN startup contract was refined so auto mode can try candidates within one bounded budget, while explicit selection avoids silent fallback.
