---
title: "Shared lifecycle first fix order"
sources:
  - "daily/2026-05-29.md"
created: 2026-05-29
updated: 2026-05-29
---
# Shared lifecycle first fix order
## Key Points
- Multi-engine regressions after `v0.2.0` should be fixed from shared lifecycle/state poisoning outward, not by patching every visible engine symptom.
- The accepted order was ByeDPI lifecycle isolation, FPTN bounded/correct startup, URnetwork startup/runtime split, relay contract fixes, then sing-box exit IP display.
- Symptoms such as `Failed(BYEDPI, timeout)` can be stale cross-engine state rather than a direct ByeDPI failure.
- Each step needs a regression sentinel that proves the shared invariant, not only a module-local happy path.
## Details
The daily log repeatedly shows a pattern where the visible failing engine was not always the root cause. A failed or wedged ByeDPI stop could poison later starts. A long FPTN auth ladder could hold orchestrator transitions and produce secondary failures. URnetwork could remain stuck before runtime recovery because readiness waited in the wrong layer. sing-box could connect while its displayed exit IP was wrong because probing bypassed the engine route.

The non-obvious relationship is that these are not independent bugs. They all cross a boundary between engine-local work and shared lifecycle/status infrastructure. The fix order therefore matters: first restore shared state isolation and bounded transitions, then align engine-specific readiness, then correct display-only behaviors.

This connection also constrains validation. A green module test is not sufficient if it does not prove same-process recovery, stale callback rejection, no terminal failure before the final candidate, and routed exit-node probing where required.
## Related Concepts
- [[concepts/byedpi-wedged-lane-generation-restart]] - First fix in the chosen order: isolate wedged ByeDPI lanes.
- [[concepts/fptn-auth-ladder-orchestrator-block]] - Long FPTN auth can block orchestrator transitions.
- [[concepts/urnetwork-startup-readiness-runtime-peer-grace]] - URnetwork needed startup/runtime separation.
- [[concepts/singbox-exit-ip-probe-chain-socks]] - sing-box issue belonged to routed probe/display, not connection startup.
## Sources
- [[daily/2026-05-29]]: records the agreed order `ByeDPI -> FPTN -> URnetwork -> sing-box -> CI/review`.
- [[daily/2026-05-29]]: records that `Failed(BYEDPI, timeout)` was often secondary to stale lifecycle state.
- [[daily/2026-05-29]]: records that fixes were split by lifecycle layer to avoid transferring the whole `byedpi-fptn-try-fix` branch.
