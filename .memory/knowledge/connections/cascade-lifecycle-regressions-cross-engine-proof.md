---
title: Cascade lifecycle regressions need cross-engine proof
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Cascade lifecycle regressions need cross-engine proof

## Key Points
- A failure label on one engine can be a downstream effect of another engine's lifecycle race.
- ByeDPI restart poisoning, FPTN auth ladders, and URnetwork startup waits all interact through shared start/stop orchestration.
- Cross-engine proof needs timeline correlation, active sequence ownership, and stale callback filtering.
- Fixes should be staged by owning layer: lane isolation, FPTN readiness, URnetwork startup/runtime split, then display diagnostics.
- This connection ties [[concepts/byedpi-wedged-lane-generation-restart]], [[concepts/fptn-upstream-readiness-ip-callback-flow]], and [[concepts/urnetwork-startup-readiness-runtime-peer-grace]].

## Details
The 2026-05-29 log shows a repeated diagnostic pattern: apparent failures in one module were not reliable evidence that that module was the root cause. `Failed(BYEDPI, timeout)` could appear after an FPTN candidate/auth sequence. URnetwork could look broken because a 5-minute readiness wait blocked `onEngineStarted()` and delayed watchdog/recovery. WARP and other modules could time out after a poisoned ByeDPI stop/start state.

The non-obvious connection is that all of these regressions pass through shared lifecycle machinery: `StartSequenceCoordinator`, `ChainOrchestrator`, `TunnelController`, watchdog, shutdown, and stale callback handling. A local engine fix can reduce one symptom while leaving the shared race intact, so proof must include the full timeline and the current active sequence.

The session produced a staged remediation model. First isolate wedged native/proxy lanes and prevent stale jobs from mutating fresh state. Then align FPTN startup with upstream and stop long serial auth ladders from blocking orchestration. Then split URnetwork short startup readiness from runtime peer grace. Finally, correct diagnostic display such as sing-box exit IP without treating display errors as transport failures.

## Related Concepts
- [[concepts/byedpi-wedged-lane-generation-restart]]
- [[concepts/fptn-upstream-readiness-ip-callback-flow]]
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]]
- [[concepts/singbox-exit-ip-probe-chain-socks]]
- [[connections/engine-lifecycle-stale-status-cascade]]

## Sources
- [[daily/2026-05-29.md]] records the cross-engine pattern linking ByeDPI restart, FPTN fallback/auth lifecycle, URnetwork readiness, and sing-box IP display.
- [[daily/2026-05-29.md]] records the staged fix order and the need to prove root cause through logs, diffs, and sequence ownership.
