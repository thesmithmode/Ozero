---
title: Auto-mode runtime proof feedback loop
sources:
  - daily/2026-05-21.md
created: 2026-06-12
updated: 2026-06-12
---
# Auto-mode runtime proof feedback loop

## Key Points
- Auto mode correctness starts with a complete candidate list of VPN-capable engines.
- Candidate inclusion does not prove runtime health after an engine starts.
- User-visible runtime symptoms still require device evidence, logs, and engine-specific probes.
- CI and sentinels should cover static contracts, while runtime proof covers traffic behavior.

## Details

The same daily log contains two complementary auto-mode lessons. First, all VPN-capable engines must be present in auto-mode priority selection, with MasterDNS added as a missing candidate. Second, the ByeDPI YouTube investigation showed that even after code changes and green CI, traffic behavior could not be claimed fixed without device-level proof.

This forms a feedback loop: static orchestration coverage ensures the engine can be selected; startup sequencing and sentinels prevent stale lifecycle regressions; runtime logs and device checks prove the selected engine actually carries the target traffic. Treating any one layer as sufficient creates false confidence.

## Related Concepts
- [[concepts/auto-mode-engine-inclusion-contract]]
- [[concepts/auto-mode-traffic-fail-blindspot]]
- [[concepts/byedpi-youtube-device-verification-boundary]]
- [[concepts/dev-ci-workflow-dispatch-nonzero-tests-contract]]

## Sources
- [[daily/2026-05-21.md]] records the static auto-mode omission fix and the unresolved ByeDPI runtime verification requirement.
- [[daily/2026-05-21.md]] records that code-level hypotheses and CI green status were insufficient for the YouTube CMD mode claim.
