package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShutdownCoordinatorExtraTest {

    @Test
    fun `recordSessionEnd swallows recorder failure and still resets shutdown state`() = runTest {
        val fixture = shutdownFixture(this, sessionId = 9L, sessionStartedAt = 1_000L)
        fixture.tunnelController.onProbing(ru.ozero.enginescore.EngineId.SINGBOX)
        fixture.tunnelController.onConnecting(ru.ozero.enginescore.EngineId.SINGBOX)
        fixture.tunnelController.onEngineStarted(ru.ozero.enginescore.EngineId.SINGBOX, 2080)
        fixture.tunnelController.updateStats(
            TunnelStats(
                txPackets = 1L,
                txBytes = 2L,
                rxPackets = 3L,
                rxBytes = 4L,
                timestampMs = 2_000L,
            ),
        )
        fixture.sessionRecorder.throwOnEnd = true

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertTrue(fixture.sessionRecorder.ended.isEmpty())
        assertFalse(fixture.state.stopping.get())
        assertFalse(fixture.state.stopSignal.get())
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    private fun shutdownFixture(
        scope: kotlinx.coroutines.CoroutineScope,
        sessionId: Long = -1L,
        sessionStartedAt: Long = 0L,
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
            tunIfaceNameRef = AtomicReference<String?>(null),
            lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null),
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
            latestStartIdProvider = { 42 },
            stopForegroundRequest = stopForegroundRequest,
            stopSelfRequest = stopSelfRequest,
        )
        return ShutdownFixture(coordinator, state, tunnelController, sessionRecorder)
    }

    private data class ShutdownFixture(
        val coordinator: ShutdownCoordinator,
        val state: ShutdownState,
        val tunnelController: TunnelController,
        val sessionRecorder: RecordingSessionStatsRecorder,
    )

    private class RecordingSessionStatsRecorder : SessionStatsRecorder {
        val ended = mutableListOf<EndedSession>()
        var throwOnEnd: Boolean = false

        override suspend fun startSession(engineId: String, startedAt: Long): Long = -1L

        override suspend fun endSession(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            status: SessionStatsRecorder.Status,
        ) {
            if (throwOnEnd) error("endSession failed")
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
