package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SingboxStatsTest {

    @Test
    fun `defaults are zeroed for idle runtime`() {
        val stats = SingboxStats()

        assertEquals(0L, stats.txRateProxy)
        assertEquals(0L, stats.rxRateProxy)
        assertEquals(0L, stats.txTotal)
        assertEquals(0L, stats.rxTotal)
        assertEquals(0, stats.activeConnections)
    }

    @Test
    fun `copy and equality preserve all counters`() {
        val stats = SingboxStats(
            txRateProxy = 11L,
            rxRateProxy = 22L,
            txTotal = 33L,
            rxTotal = 44L,
            activeConnections = 5,
        )
        val copied = stats.copy()

        assertEquals(stats, copied)
        assertEquals(11L, copied.txRateProxy)
        assertEquals(22L, copied.rxRateProxy)
        assertEquals(33L, copied.txTotal)
        assertEquals(44L, copied.rxTotal)
        assertEquals(5, copied.activeConnections)
    }
}
