---
title: Auto-mode traffic-fail blind spot after engine start
sources:
  - daily/2026-05-15 (1).md
  - daily/2026-05-21.md
created: 2026-05-15
updated: 2026-06-12
---
# Auto-mode traffic-fail blind spot after engine start

## Key Points
- Auto mode can treat an engine as healthy once startup and preflight succeed.
- Post-start traffic failures can remain invisible when the engine reports connected but no user traffic passes.
- This runtime blind spot is separate from candidate-list completeness: every VPN-capable engine must still be included.
- A durable fix needs post-start liveness evidence, not only a successful `start()` result.

## Details

Auto mode iterates through engine candidates and stops when a candidate appears to start successfully. That protects against startup and preflight failures, but not against a later "connected but dead" state where the VPN tunnel exists and readiness signals look valid while application traffic does not flow.

The 2026-05-21 log adds a complementary static rule: auto mode must include every available VPN-capable module, with non-VPN sidecars excluded by design. MasterDNS was added to `engineAutoPriority` after the omission was reported. Candidate inclusion and post-start liveness are distinct correctness layers; fixing one does not prove the other.

## Related Concepts
- [[concepts/auto-mode-engine-inclusion-contract]]
- [[concepts/vpn-engine-pipeline]]
- [[concepts/engine-await-ready-pattern]]
- [[connections/auto-mode-runtime-proof-feedback-loop]]

## Sources
- [[daily/2026-05-15 (1).md]] records the original blind spot: auto mode did not rotate after a post-start traffic failure.
- [[daily/2026-05-21.md]] records the later rule that missing VPN-capable engines in auto mode are an architecture bug and that MasterDNS was added to `engineAutoPriority`.
