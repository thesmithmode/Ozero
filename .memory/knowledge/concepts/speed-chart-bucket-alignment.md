---
title: "Speed Chart Time-Aligned Bucket Aggregation"
aliases: [chart-bucket-alignment, bucketize-time-aligned, sliding-window-drift]
tags: [ui, compose, chart, pattern, gotcha]
sources:
  - "daily/2026-05-19.md"
created: 2026-05-19
updated: 2026-05-19
---

# Speed Chart Time-Aligned Bucket Aggregation

Position-based bucket aggregation for M5/M30/H1 speed chart timeframes causes historical data to "drift" as the sliding window advances. When buckets are formed by index position within the window rather than by absolute timestamp, each window shift remaps which samples fall into which bucket, changing bucket averages for historical data. The fix anchors buckets to wall-clock grid boundaries (multiples of `bucketSec` from epoch), making bucket membership stable regardless of window position.

## Key Points

- `bucketize(samples, windowMs, bucketCount)` grouping by index position = bug: window shift changes index→sample mapping → historical averages "drift"
- `SpeedSample(timestampMs: Long, bytesPerSec: Float)` typed wrapper replaces bare `Pair<Long, Float>` — required for timestamp-anchored grouping
- `bucketizeTimeAligned(samples, bucketSec)` computes bucket ID as `(sample.timestampMs / 1000) / bucketSec` — stable across window shifts
- All higher timeframes (M5=5s buckets, M30=30s buckets, H1=60s buckets) output exactly 60 data points
- User-visible symptom: "букву M колбасит" — historical portion of the graph deforms visually as new data arrives

## Details

### The Drift Mechanism

The original `bucketize` function grouped `speedHistory` samples by their position within the display window: sample at index `i` went into bucket `i / samplesPerBucket`. As the sliding window advanced (new sample appended, oldest dropped), sample indices shifted. A sample previously at index 45 is now at index 44, potentially moving it from bucket 8 to bucket 7. The bucket average for bucket 7 changes even though the underlying data did not change — producing the visual "drift" effect where the left (historical) portion of the chart appears unstable.

### Time-Aligned Fix

`bucketizeTimeAligned` computes each sample's bucket ID from its absolute timestamp:

```kotlin
fun bucketizeTimeAligned(samples: List<SpeedSample>, bucketSec: Int): List<Float> {
    val buckets = samples.groupBy { sample ->
        (sample.timestampMs / 1000L) / bucketSec  // wall-clock bucket ID
    }
    return buckets.entries
        .sortedBy { it.key }
        .takeLast(60)
        .map { entry -> entry.value.map { it.bytesPerSec }.average().toFloat() }
}
```

A sample timestamped at 14:27:43 with `bucketSec=5` always maps to bucket `(14*3600+27*60+43)/5 = 10366`. When the window slides forward one second, this sample is still in bucket 10366. Bucket averages are stable.

### SpeedSample Type

The introduction of `SpeedSample(timestampMs: Long, bytesPerSec: Float)` as a typed data class (replacing `Pair<Long, Float>`) was necessary for the time-aligned approach. `speedHistory` is now `List<SpeedSample>` rather than `List<Pair<Long, Float>>`.

## Related Concepts

- [[concepts/chart-nice-max-dynamic-scaling]] — M5/M30/H1 timeframes with 60-point output first established there; bucket alignment is the correctness fix for that architecture
- [[concepts/libhev-tunnel-stats]] — EWMA α=0.4 smoothing feeds into SpeedSample values

## Sources

- [[daily/2026-05-19.md]] — Session 00:51: "букву M колбасит" — chart deforms historical data when window slides; root cause = index-based bucketing; fix = `bucketizeTimeAligned` anchored to wall-clock grid; `SpeedSample` typed data class introduced
