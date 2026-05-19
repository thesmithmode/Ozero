---
title: "Chart Y-Axis Dynamic Scaling with 1-2-5 Steps"
aliases: [chart-nice-max, speed-chart-scaling, dynamic-y-axis, chart-timeframes]
tags: [ui, compose, chart, pattern]
sources:
  - "daily/2026-05-12.md"
  - "daily/2026-05-18.md"
  - "daily/2026-05-19.md"
created: 2026-05-12
updated: 2026-05-19
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

### Timeframe Architecture: M1 Baseline + Bucket Aggregation (2026-05-18)

The original 30-second baseline was too short to show meaningful speed trends — the chart appeared noisy and the user could not distinguish sustained throughput from burst patterns. The refactored timeframe system uses M1 (60 seconds) as the base timeframe with 60 data points (1 point/second), and higher timeframes (M5, M30, H1) use bucket aggregation rather than simple downsampling.

**Timeframes:**

| Timeframe | Duration | Points | Method |
|-----------|----------|--------|--------|
| M1 | 60s | 60 | Raw 1s samples |
| M5 | 5min | 60 | 5s bucket average |
| M30 | 30min | 60 | 30s bucket average |
| H1 | 1hr | 60 | 60s bucket average |

All timeframes produce exactly 60 data points for consistent chart rendering. M1 uses raw samples from the 1s throttle. Higher timeframes divide the history into equal-duration buckets and compute the arithmetic mean of samples within each bucket. This preserves the shape of throughput trends better than taking every Nth sample (which may hit peaks or valleys by chance).

**Why bucket aggregation over downsampling:** Simple downsampling (take every 5th point for M5) loses information about what happened between sampled points. A 4-second spike followed by 1 second of silence appears as either full-spike or zero depending on which sample is picked. Bucket averaging captures the true average throughput across each 5-second window — a spike shows as elevated average, silence shows as reduced average.

### Bucket Alignment Correctness Fix (2026-05-19)

The M5/M30/H1 bucket aggregation introduced in 2026-05-18 had a subtle correctness bug: `bucketize(samples, windowMs, bucketCount)` grouped samples by index position within the sliding window. As the window advanced (new sample appended, oldest dropped), sample indices shifted — a sample at index 45 moved to index 44, potentially changing its bucket assignment. This produced "drift" in the historical portion of the chart: past averages changed even when the underlying data did not. User-visible symptom: "букву M колбасит".

Fix: `bucketizeTimeAligned(samples, bucketSec)` uses wall-clock bucket IDs (`(sample.timestampMs / 1000L) / bucketSec`). A sample's bucket ID is derived from its absolute timestamp, not its position — stable regardless of window shift.

`SpeedSample(timestampMs: Long, bytesPerSec: Float)` typed data class replaces bare `Pair<Long, Float>` to carry the timestamp required for time-aligned bucketing. `speedHistory` is now `List<SpeedSample>`.

See [[concepts/speed-chart-bucket-alignment]] for the full drift mechanism and fix details.

## Related Concepts

- [[concepts/libhev-tunnel-stats]] - EWMA α=0.4 smoothing feeds into the chart; throttling is a second-layer smoothing on top of EWMA
- [[concepts/per-engine-ui]] - MainScreen chart is part of the main UI, not per-engine settings
- [[concepts/speed-chart-bucket-alignment]] - The drift bug and time-aligned fix for M5/M30/H1 bucket aggregation

## Sources

- [[daily/2026-05-12.md]] - Session 11:59: chartNiceMax 17 thresholds (1-2-5 step), SPEED_SAMPLE_INTERVAL_MS=1000 throttle, sentinel tests in MainScreenChartTest.kt; user demanded planning-first before implementation
- [[daily/2026-05-18.md]] - Session 18:52: 30s baseline → M1 (60s); M5/M30/H1 use bucket aggregation instead of downsample; all timeframes output 60 data points
- [[daily/2026-05-19.md]] - Session 00:51: "букву M колбасит" — index-based bucketing causes drift; `bucketizeTimeAligned` with epoch-anchored bucket IDs; `SpeedSample` typed wrapper replaces `Pair<Long, Float>`
