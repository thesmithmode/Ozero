package ru.ozero.coreorchestrator

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.ProbeResult
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HealthMonitorTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private fun makeMonitor(engine: Engine): HealthMonitor =
        HealthMonitor(
            engine = engine,
            scope = scope,
            probeIntervalMs = 30_000L,
            failThreshold = 3,
        )

    @Test
    fun initialStatusIsHealthy() {
        val engine = mockk<Engine>()
        val monitor = makeMonitor(engine)
        assertEquals(HealthStatus.Healthy, monitor.status.value)
    }

    @Test
    fun remainsHealthyOnSuccessfulProbes() = scope.runTest {
        val engine = mockk<Engine> {
            coEvery { probe() } returns ProbeResult.Success(latencyMs = 10L)
        }
        val monitor = makeMonitor(engine)
        monitor.start()
        advanceTimeBy(90_001L)
        assertEquals(HealthStatus.Healthy, monitor.status.value)
        monitor.stop()
    }

    @Test
    fun degradesAfterThreeConsecutiveFails() = scope.runTest {
        val engine = mockk<Engine> {
            coEvery { probe() } returns ProbeResult.Failure(reason = "timeout")
        }
        val monitor = makeMonitor(engine)
        monitor.start()
        advanceTimeBy(90_001L)
        assertEquals(HealthStatus.Degraded, monitor.status.value)
        monitor.stop()
    }

    @Test
    fun recoversToHealthyAfterSuccessFollowingFails() = scope.runTest {
        var callCount = 0
        val engine = mockk<Engine> {
            coEvery { probe() } answers {
                callCount++
                if (callCount <= 3) ProbeResult.Failure("err") else ProbeResult.Success(10L)
            }
        }
        val monitor = makeMonitor(engine)
        monitor.start()
        advanceTimeBy(90_001L)
        assertEquals(HealthStatus.Degraded, monitor.status.value)
        advanceTimeBy(30_001L)
        assertEquals(HealthStatus.Healthy, monitor.status.value)
        monitor.stop()
    }

    @Test
    fun stopResetsStatusToHealthy() = scope.runTest {
        val engine = mockk<Engine> {
            coEvery { probe() } returns ProbeResult.Failure("err")
        }
        val monitor = makeMonitor(engine)
        monitor.start()
        advanceTimeBy(90_001L)
        assertEquals(HealthStatus.Degraded, monitor.status.value)
        monitor.stop()
        assertEquals(HealthStatus.Healthy, monitor.status.value)
    }
}
