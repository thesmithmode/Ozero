---
title: Auto-mode engine inclusion contract
sources:
  - daily/2026-05-21.md
created: 2026-06-12
updated: 2026-06-12
---
# Auto-mode engine inclusion contract

## Key Points
- Auto mode must include every available VPN-capable engine by default.
- Non-VPN helper modules such as MTProxy-like sidecars can stay out of auto mode.
- Adding a new engine module requires updating `engineAutoPriority` or the equivalent discovery path.
- Auto-mode order remains configurable, but omission of a VPN engine is an architecture bug.

## Details

The daily log records a user report that auto mode did not include all available modules. The resulting fix reconciled the engine list and added MasterDNS to `engineAutoPriority` with test updates. The durable rule is that auto mode is an engine orchestration feature, not a hand-curated subset of older engines.

This concept is separate from post-start liveness detection. Inclusion answers whether an engine is a candidate at all; health monitoring answers when auto mode should rotate after a candidate starts but fails to carry traffic. Both are needed for reliable automatic fallback, but missing inclusion is a static configuration defect.

## Related Concepts
- [[concepts/auto-mode-traffic-fail-blindspot]]
- [[concepts/engine-masterdns]]
- [[concepts/new-engine-module-ci-checklist]]
- [[concepts/vpn-engine-pipeline]]

## Sources
- [[daily/2026-05-21.md]] records the user requirement that every VPN-capable module automatically appear in auto mode.
- [[daily/2026-05-21.md]] records that MasterDNS was added to `engineAutoPriority` and covered by test updates.
