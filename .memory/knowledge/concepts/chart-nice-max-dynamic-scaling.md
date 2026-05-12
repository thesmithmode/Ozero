---
title: "Chart Y-Axis Dynamic Scaling with 1-2-5 Steps"
aliases: [chart-nice-max, speed-chart-scaling, dynamic-y-axis]
tags: [ui, compose, chart, pattern]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# Chart Y-Axis Dynamic Scaling with 1-2-5 Steps

The speed chart in MainScreen used a fixed Y-axis maximum, causing the graph to "телепаться по дну" (flutter at the bottom) when actual speeds were much lower than the fixed max. The fix: `chartNiceMax` selects from 17 predefined thresholds following the 1-2-5 step pattern (1/2/5/10/20/50/100/200/500 KB/s, 1/2/5/10/20/50/100/200 MB/s), choosing the smallest threshold that exceeds the current peak speed. Combined with throttling `speedHistory` to one sample per second via `SPEED_SAMPLE_INTERVAL_MS=1000`.

## Key Points

- 17 fixed thresholds following 1-2-5 pattern prevent jarring axis jumps that a pure "next power of 10" approach would cause
- `chartNiceMax(peakBytes)` returns the smallest threshold ≥ peakBytes — O(1) linear scan over 17 values
- Speed samples throttled to 1/second via `SPEED_SAMPLE_INTERVAL_MS = 1000` gate in `MainViewModel` — prevents chart jitter from sub-second EWMA fluctuations
- Sentinel tests in `MainScreenChartTest.kt` verify threshold selection and sample interval constant
- User stopped initial implementation mid-code, demanded explicit plan first — reinforced planning-first discipline

## Details

### The 1-2-5 Step Pattern

The 1-2-5 step pattern is a standard engineering scale (used in oscilloscopes, scientific instruments, and charting libraries) that provides visually even divisions. Each decade (10× range) has exactly 3 steps: 1×, 2×, 5×. This gives finer granularity than powers of 10 without the visual noise of arbitrary values.

For Ozero's speed chart, the 17 thresholds span from 1 KB/s to 200 MB/s:

```
1, 2, 5, 10, 20, 50, 100, 200, 500 KB/s
1, 2, 5, 10, 20, 50, 100, 200 MB/s
```

When peak speed is 37 KB/s, the axis max is 50 KB/s. When it jumps to 1.2 MB/s, the axis max becomes 2 MB/s. The transition is smooth because the steps are at most 2.5× apart.

### Speed Sample Throttling

Without throttling, the EWMA-smoothed speed (α=0.4, see [[concepts/libhev-tunnel-stats]]) still produces multiple updates per second as `TunnelController.updateStats` fires. Each update appended to `speedHistory` and triggered recomposition. At sub-second intervals, the chart line jittered visually even when throughput was stable.

The fix gates sample collection in `MainViewModel`: a new speed value is only appended to `speedHistory` if at least `SPEED_SAMPLE_INTERVAL_MS` (1000ms) has elapsed since the last sample. This produces a clean 1-point-per-second history that renders as a smooth chart line.

## Related Concepts

- [[concepts/libhev-tunnel-stats]] - EWMA α=0.4 smoothing feeds into the chart; throttling is a second-layer smoothing on top of EWMA
- [[concepts/per-engine-ui]] - MainScreen chart is part of the main UI, not per-engine settings

## Sources

- [[daily/2026-05-12.md]] - Session 11:59: chartNiceMax 17 thresholds (1-2-5 step), SPEED_SAMPLE_INTERVAL_MS=1000 throttle, sentinel tests in MainScreenChartTest.kt; user demanded planning-first before implementation
