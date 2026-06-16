package ru.ozero.app.ui.stats

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficChartDataTest {

    @Test
    fun `empty chart has no buckets and no lines`() {
        assertTrue(TrafficChartData.Empty.buckets.isEmpty())
        assertTrue(TrafficChartData.Empty.lines.isEmpty())
    }

    @Test
    fun `engine all constant is stable for aggregate selection`() {
        assertEquals("__all__", ENGINE_ID_ALL)
    }

    @Test
    fun `traffic summary stores aggregate counters`() {
        val summary = TrafficSummary(
            totalRx = 10,
            totalTx = 20,
            sessionCount = 3,
            totalDurationMs = 40,
        )

        assertEquals(10, summary.totalRx)
        assertEquals(20, summary.totalTx)
        assertEquals(3, summary.sessionCount)
        assertEquals(40, summary.totalDurationMs)
    }

    @Test
    fun `engine summary stores per engine counters`() {
        val summary = EngineSummary(
            engineId = "warp",
            rx = 100,
            tx = 200,
            sessionCount = 4,
        )

        assertEquals("warp", summary.engineId)
        assertEquals(100, summary.rx)
        assertEquals(200, summary.tx)
        assertEquals(4, summary.sessionCount)
    }
}
