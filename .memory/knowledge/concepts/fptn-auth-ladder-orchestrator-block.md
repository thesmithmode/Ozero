---
title: FPTN auth ladder can block chain orchestration
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# FPTN auth ladder can block chain orchestration

## Key Points
- FPTN runtime `start()` must not run a full serial `authenticateFirstAvailable()` ladder over all candidates in the critical path.
- `AUTH_TIMEOUT_S=15` multiplied by several candidates can turn one startup attempt into a long blocking operation.
- `ChainOrchestrator` serializes start/stop through a mutex, so a long FPTN start can delay stop, restart, and neighboring engine recovery.
- Full candidate fallback is useful only outside the steady startup path, with explicit cancellation and attempt identity.

## Details

The 2026-05-29 investigation compared current behavior with `v0.2.0` and found that FPTN had shifted toward sequential candidate authentication in the normal start path. That made startup duration depend on `15s * N` candidate attempts, while surrounding orchestration still expected bounded readiness. This connects to [[concepts/fptn-cancellation-cooperative-auth-lifecycle]] and [[concepts/fptn-upstream-readiness-ip-callback-flow]]: cancellation must stop fallback work, and readiness must represent actual protocol progress rather than "still trying another server".

The failure mode is systemic because `ChainOrchestrator` protects start/stop transitions with one mutex. If FPTN continues a long auth ladder while UI or watchdog asks for stop/restart, the chain can appear hung and later callbacks can be attributed to the wrong engine. The daily log links this to false `Failed(BYEDPI, timeout)` symptoms and to the existing auto-candidate invariant in [[concepts/auto-candidate-terminal-status-invariant]].

The intended fix direction is to make the default FPTN runtime path use one selected server and one auth attempt, with fast and truthful failure. Broader server fallback can remain as an explicit diagnostic or external selection mechanism, but it must not keep the orchestrator locked or emit terminal UI state before the final candidate is truly final.

## Related Concepts
- [[concepts/fptn-cancellation-cooperative-auth-lifecycle]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]
- [[concepts/auto-candidate-terminal-status-invariant]]
- [[connections/engine-lifecycle-stale-status-cascade]]

## Sources
- [[daily/2026-05-29]] records the root-cause analysis that `selectServerCandidates()` plus `authenticateFirstAvailable()` can create a long `15s * N` FPTN startup ladder.
- [[daily/2026-05-29]] records that `ChainOrchestrator` serializes start/stop and can be blocked by a long FPTN startup.
- [[daily/2026-05-29]] records the final fix plan: keep full fallback out of default `FptnEngine.start()` and protect non-terminal auto-candidate failures from UI terminal status.
