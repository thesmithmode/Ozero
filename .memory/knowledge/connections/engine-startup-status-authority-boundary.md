---
title: Engine startup status authority boundary
sources:
  - daily/2026-05-29.md
created: 2026-05-29
updated: 2026-05-29
---
# Engine startup status authority boundary

## Key Points
- FPTN, ByeDPI, URnetwork, and sing-box regressions shared one authority problem: the layer that reports status was not always the layer that owned truth.
- Startup readiness, runtime recovery, exit IP display, and relay activation each need a clear source of truth.
- Cross-engine symptoms should be traced by event identity and route ownership before fixing a single module.
- A safe fix plan separates engine runtime behavior from UI labels, network callbacks, and diagnostic probes.

## Details
The daily log connects several failures that looked module-specific but were actually authority-boundary issues. FPTN auth ladders blocked orchestration, stale callbacks could appear as ByeDPI failures, URnetwork `peers=0` was interpreted at the wrong lifecycle phase, and sing-box direct IP probe could show a public IP that did not belong to the active outbound graph.

The common remediation pattern is to assign each decision to the owning layer: engine startup returns only when its bounded readiness contract is met; runtime grace belongs to watchdog; relay config is owned by `provideEnabled`; exit IP is owned by an engine-declared probe strategy; terminal UI failures are owned by the current attempt identity.

## Related Concepts
- [[concepts/chain-start-timeout-stale-engine-failure-cascade]]
- [[concepts/urnetwork-providerstate-peer-grace-contract]]
- [[concepts/exit-node-probe-no-direct-fallback]]
- [[connections/startup-readiness-runtime-recovery-boundary]]

## Sources
- [[daily/2026-05-29]] records that FPTN/ByeDPI failures required tracing through StartSequence, ChainOrchestrator, TunnelController, and stale callbacks.
- [[daily/2026-05-29]] records that URnetwork readiness, relay enablement, and sing-box exit IP each needed a separate source-of-truth boundary.
