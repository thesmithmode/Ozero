package ru.ozero.app.ui

data class SpeedSample(
    val tsMs: Long,
    val rxBps: Float,
    val txBps: Float,
)

internal fun bucketizeTimeAligned(
    samples: List<SpeedSample>,
    windowMs: Long,
    bucketCount: Int,
): List<Pair<Float, Float>> {
    if (bucketCount <= 0) return emptyList()
    val bucketMs = windowMs / bucketCount
    if (bucketMs <= 0L) return emptyList()
    val latestSampleMs = samples.maxOfOrNull { it.tsMs }
        ?: return List(bucketCount) { 0f to 0f }
    val latestBucketStart = (latestSampleMs / bucketMs) * bucketMs
    val firstBucketStart = latestBucketStart - (bucketCount - 1L) * bucketMs
    val sumsRx = FloatArray(bucketCount)
    val sumsTx = FloatArray(bucketCount)
    val counts = IntArray(bucketCount)
    for (s in samples) {
        val idx = ((s.tsMs - firstBucketStart) / bucketMs).toInt()
        if (idx in 0 until bucketCount) {
            sumsRx[idx] += s.rxBps
            sumsTx[idx] += s.txBps
            counts[idx]++
        }
    }
    return List(bucketCount) { i ->
        if (counts[i] > 0) sumsRx[i] / counts[i] to sumsTx[i] / counts[i] else 0f to 0f
    }
}
