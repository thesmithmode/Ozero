---
title: Startup readiness and runtime recovery must stay separate
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Startup readiness and runtime recovery must stay separate

## Key Points
- Startup gates must prove bounded readiness, not absorb long fallback or recovery windows.
- Runtime grace belongs after `onEngineStarted()`, where watchdog and recovery infrastructure are active.
- Stale callbacks must be correlated by attempt or generation before they can publish terminal status.
- The same boundary explains FPTN auth ladders, URnetwork peer grace, and ByeDPI restart poisoning.

## Details

The day connected several regressions through one architectural pattern. FPTN placed serial server fallback in startup, URnetwork placed a five-minute `peers=0` window before `onEngineStarted()`, and ByeDPI could leave wedged native/proxy jobs that affected later starts. Each case moved long recovery behavior into a phase where orchestration still expected a bounded start or stop.

The boundary matters because startup owns user-visible readiness and orchestration locks. If a startup path keeps trying candidates or waits for runtime network quality, stop/restart requests can queue behind it and stale callbacks can surface under the wrong engine label. This extends [[concepts/fptn-auth-ladder-orchestrator-block]], [[concepts/urnetwork-startup-readiness-runtime-peer-grace]], and [[concepts/byedpi-wedged-lane-generation-restart]].

The repair pattern is consistent: keep startup short, make failure attribution attempt-aware, move grace/retry to runtime watchdogs, and guard late callbacks by generation or attempt id. That is the operational form of [[concepts/auto-candidate-terminal-status-invariant]] and [[connections/stale-engine-signals-cross-engine-failures]].

## Related Concepts
- [[concepts/fptn-auth-ladder-orchestrator-block]]
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/auto-candidate-terminal-status-invariant]]

## Sources
- [[daily/2026-05-29]] records that FPTN serial auth fallback can block orchestrator startup and cause false adjacent-engine failures.
- [[daily/2026-05-29]] records that URnetwork's five-minute `peers=0` wait belongs in runtime watchdog, not startup `awaitReady()`.
- [[daily/2026-05-29]] records that ByeDPI needed wedged-lane rotation and generation guards so stale native jobs cannot overwrite later starts.
