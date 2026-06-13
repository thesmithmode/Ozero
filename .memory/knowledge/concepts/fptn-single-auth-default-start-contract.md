---
title: FPTN single auth default start contract
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN single auth default start contract

## Key Points
- Default FPTN `start()` should not run mandatory full candidate auth fallback in the critical startup path.
- `AUTH_TIMEOUT_S=15` multiplied by several candidates can block shared orchestration for minutes.
- Full candidate probing may remain a diagnostic or controlled fallback, but not the normal steady path.
- Cancellation must stop auth fallback instead of letting old attempts continue into the next engine transition.

## Details
The final plan from 2026-05-29 identified `FptnEngine.start()` as a primary source of long startup stalls: `selectServerCandidates()` plus `authenticateFirstAvailable()` could sequentially try several servers with a 15-second timeout each. Because `ChainOrchestrator` serializes `start/stop` through a mutex, one long FPTN attempt can delay stop/restart transitions and amplify failures in neighboring engines.

The chosen contract is to return FPTN's default runtime path closer to the stable `v0.2.0` model: one selected server and one auth attempt in the critical path. Broader server fallback should be bounded, explicitly controlled, cancellation-cooperative, and observable, not a hidden ladder inside startup readiness.

## Related Concepts
- [[concepts/fptn-auth-ladder-orchestrator-block]]
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/startup-readiness-runtime-recovery-boundary]]
- [[concepts/auto-candidate-terminal-status-invariant]]

## Sources
- [[daily/2026-05-29]] records the final plan to remove mandatory full candidate auth from FPTN default `start()`.
- [[daily/2026-05-29]] records the `15s * N` auth ladder as a cause of blocked orchestration and false cascade symptoms.
