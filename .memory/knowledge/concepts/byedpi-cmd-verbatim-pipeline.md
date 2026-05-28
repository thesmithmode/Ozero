---
title: ByeDPI CMD verbatim pipeline
sources:
  - daily/2026-05-21.md
created: 2026-05-28
updated: 2026-05-28
---
# ByeDPI CMD verbatim pipeline

## Key Points
- CMD mode must pass `byedpiWinningArgs.trim()` verbatim to ByeDPI.
- Auto-appending `-Ku -a1 -An` mutates user strategy topology and can override intended argument sections.
- If evolution needs UDP desync, the fix belongs in `AutoStrategyPicker`, not in the final CMD output path.
- Matching args in the reference app means the root cause is in pipeline, initialization, or build differences, not the args text.

## Details

The CMD-mode YouTube regression was traced to `ensureUdpDesync`, a legacy mutation that appended `-Ku -a1 -An` when the user strategy did not contain `-Ku`. For the reported strategy this added another `-a` section and changed the effective topology, so a strategy that worked in original ByeByeDPI failed in Ozero.

The durable contract is that CMD mode is manual mode: the winning args are trimmed only at the edges and otherwise sent unchanged. UI mode and auto strategy selection may generate their own args, but the final CMD path must not repair or normalize them.

## Related Concepts
- [[concepts/byedpi-args-parsing]]
- [[concepts/byedpi-udp-quic-routing]]
- [[concepts/byedpi-ensure-udp-desync]]
- [[connections/byedpi-reference-parity]]

## Sources
- [[daily/2026-05-21.md]] records that `ensureUdpDesync` appended `-Ku -a1 -An`, broke the reported CMD strategy, and was removed in favor of verbatim `trim()`.
- [[daily/2026-05-21.md]] records the lesson that identical args working in the reference app shifts investigation to pipeline, init order, or build parity.
