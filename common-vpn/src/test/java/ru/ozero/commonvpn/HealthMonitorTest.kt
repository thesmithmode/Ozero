package ru.ozero.commonvpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            dispatcher = dispatcher,
            probe = { _, _, _ -> 50L },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        runCurrent()
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `consecutive failures below threshold keeps UNKNOWN`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            dispatcher = dispatcher,
            probe = { _, _, _ -> throw java.io.IOException("connection refused") },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(HealthMonitor.Status.UNKNOWN, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `consecutive failures above threshold transitions to DEGRADED`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            dispatcher = dispatcher,
            probe = { _, _, _ -> throw java.io.IOException("connection refused") },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(450L)
        runCurrent()
        assertEquals(HealthMonitor.Status.DEGRADED, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `success after failures resets counter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var failNext = true
        val mon = HealthMonitor(
            intervalMs = 100L,
            failuresBeforeDegraded = 3,
            dispatcher = dispatcher,
            probe = { _, _, _ ->
                if (failNext) throw java.io.IOException("fail") else 10L
            },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(250L)
        runCurrent()
        failNext = false
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `stop resets status to UNKNOWN`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mon = HealthMonitor(
            intervalMs = 100L,
            dispatcher = dispatcher,
            probe = { _, _, _ -> 10L },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        runCurrent()
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

    @Test
    fun `success transitions UNKNOWN to HEALTHY и обратно — recovery после фейлов`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fail = true
        val mon = HealthMonitor(
            intervalMs = 50L,
            failuresBeforeDegraded = 2,
            dispatcher = dispatcher,
            probe = { _, _, _ ->
                if (fail) throw java.io.IOException("fail") else 5L
            },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        runCurrent()
        assertEquals(HealthMonitor.Status.DEGRADED, mon.status.value)
        fail = false
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `start повторный — сбрасывает счётчик и status`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fail = true
        val mon = HealthMonitor(
            intervalMs = 50L,
            failuresBeforeDegraded = 2,
            dispatcher = dispatcher,
            probe = { _, _, _ ->
                if (fail) throw java.io.IOException("fail") else 5L
            },
        )
        mon.start(socksPort = 1080)
        advanceTimeBy(150L)
        runCurrent()
        assertEquals(HealthMonitor.Status.DEGRADED, mon.status.value)
        fail = false
        mon.start(socksPort = 1080)
        assertEquals(HealthMonitor.Status.UNKNOWN, mon.status.value)
        advanceTimeBy(60L)
        runCurrent()
        assertEquals(HealthMonitor.Status.HEALTHY, mon.status.value)
        mon.shutdown()
    }

    @Test
    fun `shutdown cancels scope — повторный start больше не работает`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var probeCalls = 0
        val mon = HealthMonitor(
            intervalMs = 50L,
            dispatcher = dispatcher,
            probe = { _, _, _ ->
                probeCalls++
                10L
            },
        )
        mon.shutdown()
        mon.start(socksPort = 1080)
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(0, probeCalls, "после shutdown scope cancelled — probe не вызывается")
    }
}
