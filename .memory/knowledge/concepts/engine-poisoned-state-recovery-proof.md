---
title: Engine poisoned-state recovery proof
sources:
  - [[daily/2026-05-28]]
created: 2026-05-28
updated: 2026-05-28
---
# Engine poisoned-state recovery proof
## Key Points
- A successful engine start after a full app restart is not proof that switching recovery works inside the same process.
- ByeDPI stop/start hangs can poison shared orchestration state and block later WARP, FPTN, URnetwork, or sing-box starts.
- Recovery evidence must cover same-process transitions after failure, not only isolated clean starts.
- This strengthens [[concepts/engine-switch-failure-containment]] and [[concepts/engine-failure-recovery-isolation]] with an explicit proof boundary.
## Details
The 2026-05-28 trace discussion clarified that WARP events showing `Connected(WARP)`, handshake, and traffic counters did not disprove poisoned-state behavior if a full forced application restart happened between the ByeDPI failure and the later WARP success. Process restart clears state that the real switch path must handle without restart.

For release-regression debugging, the proof target is therefore same-process recovery: one engine fails or hangs, the orchestrator finishes cleanup or contains the failure, and the next engine starts without inheriting stale mutexes, native guards, UAPI sockets, or lifecycle jobs. Clean-start logs remain useful, but they are weaker evidence than transition logs through the actual `ChainOrchestrator.stop/start` path.
## Related Concepts
- [[concepts/engine-switch-failure-containment]]
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/byedpi-stop-timeout-contract]]
- [[concepts/warp-uapi-cleanup-all-sockets]]
## Sources
- [[daily/2026-05-28]]: the 20:31, 20:37, 20:39, and 20:59 sessions record that WARP success after a full restart cannot prove recovery from ByeDPI poisoned state inside one process.
- [[daily/2026-05-28]]: the same sessions identify the shared switching/orchestration path as a suspected root, separate from per-engine failures.
