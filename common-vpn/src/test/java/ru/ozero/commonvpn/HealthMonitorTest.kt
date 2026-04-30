package ru.ozero.commonvpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HealthMonitorTest {

    @Test
    fun `initial status UNKNOWN`() {
        val mon = HealthMonitor()
        assertEquals(HealthMonitor.Status.UNKNOWN, mon.status.value)
    }

    @Test
    fun `probe success keeps status HEALTHY`() = runTest {
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            probe = { _, _, _ -> 50L },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `consecutive failures below threshold keeps UNKNOWN`() = runTest {
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            probe = { _, _, _ -> throw RuntimeException("connection refused") },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(250L)
        assertEquals(HealthMonitor.Status.UNKNOWN, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `consecutive failures above threshold transitions to DEGRADED`() = runTest {
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            probe = { _, _, _ -> throw RuntimeException("connection refused") },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(450L)
        assertEquals(HealthMonitor.Status.DEGRADED, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `success after failures resets counter`() = runTest {
        var failNext = true
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            probe = { _, _, _ ->
                if (failNext) throw RuntimeException("fail") else 10L
            },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(250L)
        failNext = false
        advanceTimeBy(200L)
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `stop resets status to UNKNOWN`() = runTest {
        val mon = HealthMonitor(
            intervalMs = 100L,
            probe = { _, _, _ -> 10L },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.stop()
        assertEquals(HealthMonitor.Status.UNKNOWN, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `defaults`() {
        assertEquals(30_000L, HealthMonitor.DEFAULT_INTERVAL_MS)
        assertEquals(3_000, HealthMonitor.DEFAULT_PROBE_TIMEOUT_MS)
        assertEquals(3, HealthMonitor.DEFAULT_FAILURES_BEFORE_DEGRADED)
    }
}
