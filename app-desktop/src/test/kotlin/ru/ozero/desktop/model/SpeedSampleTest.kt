package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedSampleTest {

    @Test
    fun `bucketizeTimeAligned returns empty for zero buckets`() {
        assertEquals(emptyList<Pair<Float, Float>>(), bucketizeTimeAligned(emptyList(), 60_000, 0))
    }

    @Test
    fun `bucketizeTimeAligned returns zeros for no samples`() {
        val result = bucketizeTimeAligned(emptyList(), 60_000, 3)
        assertEquals(3, result.size)
        result.forEach { (rx, tx) ->
            assertEquals(0f, rx)
            assertEquals(0f, tx)
        }
    }

    @Test
    fun `bucketizeTimeAligned averages within buckets`() {
        val samples = listOf(
            SpeedSample(1000L, 100f, 50f),
            SpeedSample(1500L, 200f, 100f),
            SpeedSample(2000L, 300f, 150f),
        )
        val result = bucketizeTimeAligned(samples, 3_000L, 3)
        assertEquals(3, result.size)
    }

    @Test
    fun `bucketizeTimeAligned handles single sample`() {
        val samples = listOf(SpeedSample(1000L, 100f, 50f))
        val result = bucketizeTimeAligned(samples, 1_000L, 1)
        assertEquals(1, result.size)
        assertEquals(100f, result[0].first)
        assertEquals(50f, result[0].second)
    }
}
