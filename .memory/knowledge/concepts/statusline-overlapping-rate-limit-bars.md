---
title: "Statusline Overlapping Rate Limit Bars"
aliases: [rate-limit-statusline-overlap, dual-limit-bar, codex-statusline-bars]
tags: [ui, statusline, rate-limit, cli, gotcha]
sources:
  - "daily/2026-05-08.md"
created: 2026-06-12
updated: 2026-06-12
---

# Statusline Overlapping Rate Limit Bars

A compact statusline can show multiple rate-limit windows in one bar only if each cell is evaluated independently for every window. Sequential min/max rendering hides low-percentage long-window usage and cannot represent overlap accurately.

## Key Points

- Two rate-limit windows such as 5h and 7d should be rendered per cell, not by drawing one bar after the other.
- Overlap requires a distinct state: both limits present in the same cell should use a combined color or glyph.
- Any nonzero percentage should render at least one occupied block, otherwise small values like 2% disappear after rounding.
- The rendering model should treat the bar as 20 independent cells whose membership is computed against both percentages.

## Details

The bug in the statusline was that the 5h bar dominated rendering while the 7d bar disappeared when its percentage rounded to zero blocks. This made the UI report only one active limit even when the long-window limit was nonzero. The root problem was sequential rendering: once the short-window bar determined the visible cells, the long-window state had no independent way to appear.

The fix is per-cell rendering. For each cell from 0 to 19, compute whether it falls within the 5h percentage and whether it falls within the 7d percentage. A cell covered by both windows renders as overlap, a cell covered only by 5h renders as short-window usage, a cell covered only by 7d renders as long-window usage, and the rest stays empty.

## Related Concepts

- [[concepts/chart-nice-max-dynamic-scaling]] - Small UI metrics need explicit scaling rules so values do not visually disappear.
- [[concepts/speed-chart-bucket-alignment]] - Time-series UI needs stable bucket semantics rather than position-dependent drift.
- [[concepts/memory-instruction-token-budget-maintenance]] - Statusline work came from the same tool-maintenance session focused on operational feedback quality.

## Sources

- [[daily/2026-05-08.md]] - Session 16:14: statusline showed only 5h while 7d disappeared at small percentages; fix used per-cell 0..19 membership, overlap rendering, and minimum one block for any value above zero.
