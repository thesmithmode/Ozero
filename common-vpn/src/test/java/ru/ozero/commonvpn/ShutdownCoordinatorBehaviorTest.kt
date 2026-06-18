package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShutdownCoordinatorBehaviorTest {

    @Test
    fun `stopVpn is idempotent and launches shutdown once`() = runTest {
        val fixture = shutdownFixture(this)

        fixture.coordinator.stopVpn()
        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        verify(exactly = 2) { fixture.statsLogger.cancel() }
        verify(exactly = 1) { fixture.engineWatchdog.cancelWatchers() }
        verify(exactly = 1) { fixture.healthMonitor.stop() }
        verify(exactly = 1) { fixture.stopForegroundRequest.invoke() }
        verify(exactly = 1) { fixture.stopSelfRequest.invoke(42) }
        assertFalse(fixture.state.stopping.get())
        assertFalse(fixture.state.stopSignal.get())
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    @Test
    fun `stopVpn records disconnected session using latest tunnel stats`() = runTest {
        val fixture = shutdownFixture(this, sessionId = 7L, sessionStartedAt = 1_000L)
        fixture.tunnelController.onProbing(EngineId.SINGBOX)
        fixture.tunnelController.onConnecting(EngineId.SINGBOX)
        fixture.tunnelController.onEngineStarted(EngineId.SINGBOX, 2080)
        fixture.tunnelController.updateStats(
            TunnelStats(
                txPackets = 0L,
                txBytes = 120L,
                rxPackets = 0L,
                rxBytes = 340L,
                timestampMs = 2_000L,
            ),
        )

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        val ended = fixture.sessionRecorder.ended.single()
        assertEquals(7L, ended.id)
        assertEquals(120L, ended.txBytes)
        assertEquals(340L, ended.rxBytes)
        assertEquals(SessionStatsRecorder.Status.DISCONNECTED, ended.status)
        assertTrue(ended.durationMs >= 0L)
    }

    @Test
    fun `stopVpn records failed session when prior state is failed`() = runTest {
        val fixture = shutdownFixture(this, sessionId = 8L, sessionStartedAt = 1_000L)
        fixture.tunnelController.onProbing(EngineId.WARP)
        fixture.tunnelController.onEngineDied(EngineId.WARP, "boom")

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertEquals(SessionStatsRecorder.Status.FAILED, fixture.sessionRecorder.ended.single().status)
    }

    @Test
    fun `stopVpn skips session recorder when no session is active`() = runTest {
        val fixture = shutdownFixture(this)

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertTrue(fixture.sessionRecorder.ended.isEmpty())
    }

    @Test
    fun `stopVpn uses latest start id when shutdown reaches stopSelf`() = runTest {
        var latestStartId = 10
        val fixture = shutdownFixture(this, latestStartIdProvider = { latestStartId })
        coEvery { fixture.chainOrchestrator.stop() } coAnswers {
            latestStartId = 77
        }

        fixture.coordinator.stopVpn()
        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        verify(exactly = 1) { fixture.stopSelfRequest.invoke(77) }
    }

    @Test
    fun `performShutdown resets state and can skip stopSelf for onDestroy path`() = runTest {
        val fixture = shutdownFixture(this)
        fixture.state.starting.set(true)
        fixture.state.stopping.set(true)
        fixture.state.stopSignal.set(true)
        fixture.state.tunIfaceNameRef.set("tun0")

        fixture.coordinator.performShutdown(callStopSelf = false)

        coVerify(exactly = 1) { fixture.chainOrchestrator.stop() }
        verify(exactly = 1) { fixture.tunnelGateway.stop() }
        verify(exactly = 1) { fixture.stopForegroundRequest.invoke() }
        verify(exactly = 0) { fixture.stopSelfRequest.invoke(any()) }
        assertFalse(fixture.state.starting.get())
        assertFalse(fixture.state.stopping.get())
        assertFalse(fixture.state.stopSignal.get())
        assertEquals(null, fixture.state.tunIfaceNameRef.get())
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    private fun shutdownFixture(
        scope: CoroutineScope,
        sessionId: Long = -1L,
        sessionStartedAt: Long = 0L,
        latestStartIdProvider: () -> Int = { 42 },
    ): ShutdownFixture {
        val tunnelController = TunnelController()
        val healthMonitor = mockk<HealthMonitor>(relaxed = true)
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val tunnelGateway = mockk<HevTunnelGateway>(relaxed = true)
        val statsLogger = mockk<TunnelStatsLogger>(relaxed = true)
        val engineWatchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        val sessionRecorder = RecordingSessionStatsRecorder()
        val state = ShutdownState(
            tunFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            tunIfaceNameRef = AtomicReference(null),
            lockdownStartupFdRef = AtomicReference(null),
            sessionStartMsRef = AtomicReference(sessionStartedAt),
            sessionIdRef = AtomicReference(sessionId),
            startJobRef = AtomicReference<Job?>(null),
            shutdownJobRef = AtomicReference<Job?>(null),
            starting = AtomicBoolean(false),
            stopping = AtomicBoolean(false),
            stopSignal = AtomicBoolean(false),
        )
        val stopForegroundRequest = mockk<() -> Unit>(relaxed = true)
        val stopSelfRequest = mockk<(Int) -> Unit>(relaxed = true)
        every { statsLogger.cancel() } returns Unit
        every { engineWatchdog.cancelWatchers() } returns Unit
        every { healthMonitor.stop() } returns Unit
        coEvery { chainOrchestrator.stop() } returns Unit
        every { tunnelGateway.stop() } returns Unit
        every { stopForegroundRequest.invoke() } returns Unit
        every { stopSelfRequest.invoke(any()) } returns Unit
        val coordinator = ShutdownCoordinator(
            scope = scope,
            deps = ShutdownCollaborators(
                tunnelController = tunnelController,
                healthMonitor = healthMonitor,
                chainOrchestrator = chainOrchestrator,
                tunnelGateway = tunnelGateway,
                statsLogger = statsLogger,
                engineWatchdog = engineWatchdog,
                sessionStatsRecorder = sessionRecorder,
            ),
            state = state,
            latestStartIdProvider = latestStartIdProvider,
            stopForegroundRequest = stopForegroundRequest,
            stopSelfRequest = stopSelfRequest,
        )
        return ShutdownFixture(
            coordinator = coordinator,
            state = state,
            tunnelController = tunnelController,
            healthMonitor = healthMonitor,
            chainOrchestrator = chainOrchestrator,
            tunnelGateway = tunnelGateway,
            statsLogger = statsLogger,
            engineWatchdog = engineWatchdog,
            sessionRecorder = sessionRecorder,
            stopForegroundRequest = stopForegroundRequest,
            stopSelfRequest = stopSelfRequest,
        )
    }

    private data class ShutdownFixture(
        val coordinator: ShutdownCoordinator,
        val state: ShutdownState,
        val tunnelController: TunnelController,
        val healthMonitor: HealthMonitor,
        val chainOrchestrator: ChainOrchestrator,
        val tunnelGateway: HevTunnelGateway,
        val statsLogger: TunnelStatsLogger,
        val engineWatchdog: EngineWatchdogCoordinator,
        val sessionRecorder: RecordingSessionStatsRecorder,
        val stopForegroundRequest: () -> Unit,
        val stopSelfRequest: (Int) -> Unit,
    )

    private class RecordingSessionStatsRecorder : SessionStatsRecorder {
        val ended = mutableListOf<EndedSession>()

        override suspend fun startSession(engineId: String, startedAt: Long): Long = -1L

        override suspend fun endSession(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            status: SessionStatsRecorder.Status,
        ) {
            ended += EndedSession(id, rxBytes, txBytes, durationMs, status)
        }
    }

    private data class EndedSession(
        val id: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val durationMs: Long,
        val status: SessionStatsRecorder.Status,
    )
}
