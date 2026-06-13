---
title: FPTN startup stalls can surface as ByeDPI failures
sources:
  - daily/2026-05-29.md
created: 2026-05-31
updated: 2026-05-31
---

## Key Points
- A long FPTN startup can block the shared orchestrator and produce secondary ByeDPI failure labels.
- `ChainOrchestrator` mutex serialization turns long `start()` into delayed `stop()` and restart pressure.
- Intermediate auto-candidate failures need attempt identity and non-terminal handling.
- Fix order should separate primary FPTN startup behavior from secondary ByeDPI status leakage.

## Details
The 2026-05-29 investigation repeatedly observed that `Failed(BYEDPI, timeout)` could appear after or during an FPTN auth/start sequence, without evidence of a fresh direct ByeDPI start in the same window. The connection is therefore not simply "ByeDPI is broken"; it is a lifecycle cascade where a stale or secondary signal can be attached to the wrong engine label.

The recommended plan split the work into layers: isolate non-terminal auto-candidate failures, shorten or make cooperative the FPTN startup path, harden reset/stop idempotency, and then validate that false `Failed(BYEDPI, timeout)` no longer appears after FPTN stalls. This connects the FPTN auth ladder, shared orchestrator mutex behavior, and stale engine signal filtering into one causal chain.

## Related Concepts
- [[concepts/fptn-auth-ladder-orchestrator-block]]
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[connections/stale-engine-signals-cross-engine-failures]]

## Sources
- [[daily/2026-05-29]] records that FPTN `authenticateFirstAvailable()` with `AUTH_TIMEOUT_S=15` can produce a `15s * N` ladder.
- [[daily/2026-05-29]] records that `ChainOrchestrator` serializes start/stop through a mutex, so long starts can block stop/restart.
- [[daily/2026-05-29]] records that false `Failed(BYEDPI, timeout)` was treated as a secondary lifecycle/status effect rather than the primary root cause.
