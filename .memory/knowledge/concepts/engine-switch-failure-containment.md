---
title: Engine switch failure containment
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# Engine switch failure containment

## Key Points
- Failure of one engine must not poison startup or recovery of neighboring engines.
- Regression analysis should separate shared orchestration failures from per-engine configuration or network failures.
- `v0.2.11` is useful as a behavioral baseline only after grounding the exact tag or SHA.
- Logs must be mapped by component and lifecycle phase before applying per-engine fixes.

## Details

The 2026-05-28 release-regression sessions treated failed switching as an architectural problem, not as a single engine bug. User reports showed that a ByeDPI failure could be followed by failed starts or confusing states in WARP, FPTN, URnetwork, and sing-box. The durable lesson is that `ChainOrchestrator.stop/start` and cleanup paths must contain failure state locally, so a stuck shutdown or stale resource cannot block the next engine.

The trace analysis also showed why diagnostics must split horizontal orchestration evidence from vertical engine evidence. ByeDPI had stop/start timeout symptoms, FPTN had an HTTP 608 timeout, sing-box failed before runtime because config validation rejected generated chains, and WARP in one trace still reached handshake and transferred bytes. Treating all of those as one engine failure would hide the shared lifecycle risk and the independent per-engine bugs.

## Related Concepts
- [[concepts/engine-failure-recovery-isolation]]
- [[concepts/engine-switch-chain-cascading-failures]]
- [[concepts/byedpi-stop-timeout-contract]]
- [[connections/release-engine-fix-contract-vs-timeout]]

## Sources
- [[daily/2026-05-28]] records the user report that errors in one module could break startup of other modules after the last-good `0.2.11` release.
- [[daily/2026-05-28]] records the decision to investigate orchestration and cleanup first, then separately validate ByeDPI, FPTN, URnetwork, sing-box, and WARP evidence.
- [[daily/2026-05-28]] records that WARP success after full app restart must not be used as evidence that same-process recovery after ByeDPI works; this keeps the focus on switch lifecycle contamination.
