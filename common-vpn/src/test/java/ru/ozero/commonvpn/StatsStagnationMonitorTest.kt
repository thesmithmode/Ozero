package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsStagnationMonitorTest {

    @Test
    fun `first observe returns false (нет baseline)`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        assertFalse(mon.observe(0L, 0L))
    }

    @Test
    fun `same bytes до threshold returns false`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 15_000L
        assertFalse(mon.observe(100L, 200L))
    }

    @Test
    fun `same bytes после threshold returns true`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 30_001L
        assertTrue(mon.observe(100L, 200L))
    }

    @Test
    fun `bytes изменились — flag сбрасывается`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 30_001L
        assertTrue(mon.observe(100L, 200L))
        clock += 100L
        assertFalse(mon.observe(101L, 200L))
    }

    @Test
    fun `только tx изменился — flag сбрасывается`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 30_001L
        assertFalse(mon.observe(150L, 200L))
    }

    @Test
    fun `только rx изменился — flag сбрасывается`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 30_001L
        assertFalse(mon.observe(100L, 250L))
    }

    @Test
    fun `reset сбрасывает baseline`() {
        var clock = 1000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        mon.observe(100L, 200L)
        clock += 30_001L
        assertTrue(mon.observe(100L, 200L))
        mon.reset()
        clock += 30_001L
        assertFalse(mon.observe(100L, 200L), "после reset baseline=0,0 → новые 100,200 = изменение")
        clock += 30_001L
        assertTrue(mon.observe(100L, 200L), "те же 100,200 через 30s → snova stagnant")
    }

    @Test
    fun `default threshold 30_000`() {
        assertTrue(StatsStagnationMonitor.STAGNATION_THRESHOLD_MS == 30_000L)
    }
}
