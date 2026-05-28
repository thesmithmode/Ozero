---
title: FPTN HTTP 608 regression baseline
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# FPTN HTTP 608 regression baseline

## Key Points
- `v0.2.11` is a user-confirmed behavioral baseline where FPTN worked better than in the later regression reports.
- In the 2026-05-28 trace, FPTN failed with an HTTP 608 timeout; later WARP success after full app restart must not be used as recovery evidence inside the same process.
- This signal should be treated as an engine-specific network/protocol failure plus a separate engine-switch isolation risk.
- FPTN investigation must still be considered alongside the shared engine switch/orchestration path because failures after ByeDPI could poison later starts.

## Details

The late 2026-05-28 investigation separated multiple engine symptoms from the same user trace. FPTN was reported as failing with HTTP 608 timeout. WARP connection events after a full application restart do not prove that WARP recovered after ByeDPI poisoned the current process [[daily/2026-05-28.md]].

The baseline is still important. The user identified `0.2.11` as the last release where ByeDPI, FPTN, and URnetwork at least partially worked. Future FPTN debugging should compare the current code and behavior against that baseline, while keeping shared switch cleanup as a separate hypothesis from FPTN-specific SNI or protocol failures [[daily/2026-05-28.md]].

Because the trace also showed ByeDPI stop/start hangs and later engine start failures, FPTN HTTP 608 should not be investigated in isolation. The correct split is to validate the common orchestrator recovery path first, then analyze FPTN-specific handshake and SNI behavior using sanitized logs [[daily/2026-05-28.md]].

## Related Concepts
- [[concepts/fptn-sni-bypass-method]]
- [[concepts/release-last-good-baseline-audit]]
- [[concepts/engine-failure-recovery-isolation]]
- [[connections/engine-specific-failure-diagnostics]]

## Sources
- [[daily/2026-05-28.md]]: records `v0.2.11` as the user-confirmed baseline and FPTN failing with HTTP 608 timeout in the later trace.
- [[daily/2026-05-28.md]]: records that WARP events after full app restart must not be counted as same-process recovery evidence.
