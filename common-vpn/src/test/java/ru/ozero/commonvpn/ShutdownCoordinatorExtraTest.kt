package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShutdownCoordinatorExtraTest {

    @Test
    fun `stopVpn idempotent launch performs shutdown once`() = runTest {
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

        fixture.coordinator.stopVpn()
        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertEquals(1, fixture.sessionRecorder.ended.size)
        verify(exactly = 1) { fixture.engineWatchdog.cancelWatchers() }
        coVerify(exactly = 1) { fixture.chainOrchestrator.stop() }
        verify(exactly = 1) { fixture.tunnelGateway.stop() }
        verify(exactly = 1) { fixture.stopForegroundRequest.invoke() }
        verify(exactly = 1) { fixture.stopSelfRequest.invoke(42) }
        assertFalse(fixture.state.stopping.get())
        assertFalse(fixture.state.stopSignal.get())
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

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

    @Test
    fun `recordSessionEnd uses zero duration and zero stats when no session start`() = runTest {
        val fixture = shutdownFixture(this, sessionId = 10L, sessionStartedAt = 0L)

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        val ended = fixture.sessionRecorder.ended.single()
        assertEquals(0L, ended.durationMs)
        assertEquals(0L, ended.txBytes)
        assertEquals(0L, ended.rxBytes)
        assertEquals(SessionStatsRecorder.Status.DISCONNECTED, ended.status)
    }

    @Test
    fun `recordSessionEnd can mark failed state`() = runTest {
        val fixture = shutdownFixture(this, sessionId = 11L, sessionStartedAt = 1_000L)
        fixture.tunnelController.onProbing(ru.ozero.enginescore.EngineId.WARP)
        fixture.tunnelController.onEngineDied(ru.ozero.enginescore.EngineId.WARP, "boom")

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertEquals(SessionStatsRecorder.Status.FAILED, fixture.sessionRecorder.ended.single().status)
    }

    @Test
    fun `recordSessionEnd skips inactive session and does not call endSession`() = runTest {
        val fixture = shutdownFixture(this)

        fixture.coordinator.stopVpn()
        advanceUntilIdle()

        assertTrue(fixture.sessionRecorder.ended.isEmpty())
    }

    @Test
    fun `performShutdown with failed close calls still resets shutdown state`() = runTest {
        val faultyTun = mockk<ParcelFileDescriptor>(relaxed = true)
        every { faultyTun.close() } throws RuntimeException("close failed")
        val fixture = shutdownFixture(this, sessionId = -1L, tunFd = faultyTun)
        fixture.state.starting.set(true)
        fixture.state.stopping.set(true)
        fixture.state.stopSignal.set(true)
        fixture.state.tunIfaceNameRef.set("tunX")
        coEvery { fixture.chainOrchestrator.stop() } throws RuntimeException("chain stop failed")
        every { fixture.tunnelGateway.stop() } throws RuntimeException("gateway stop failed")

        fixture.coordinator.performShutdown(callStopSelf = false)

        coVerify(exactly = 1) { fixture.chainOrchestrator.stop() }
        verify(exactly = 1) { fixture.tunnelGateway.stop() }
        verify(exactly = 1) { faultyTun.close() }
        verify(exactly = 1) { fixture.stopForegroundRequest.invoke() }
        verify(exactly = 0) { fixture.stopSelfRequest.invoke(any()) }
        assertFalse(fixture.state.starting.get())
        assertFalse(fixture.state.stopping.get())
        assertFalse(fixture.state.stopSignal.get())
        assertEquals(null, fixture.state.tunIfaceNameRef.get())
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    private fun shutdownFixture(
        scope: kotlinx.coroutines.CoroutineScope,
        sessionId: Long = -1L,
        sessionStartedAt: Long = 0L,
        tunFd: ParcelFileDescriptor? = null,
    ): ShutdownFixture {
        val tunnelController = TunnelController()
        val healthMonitor = mockk<HealthMonitor>(relaxed = true)
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val tunnelGateway = mockk<HevTunnelGateway>(relaxed = true)
        val statsLogger = mockk<TunnelStatsLogger>(relaxed = true)
        val engineWatchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        val sessionRecorder = RecordingSessionStatsRecorder()
        val state = ShutdownState(
            tunFdRef = AtomicReference(tunFd),
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
        return ShutdownFixture(
            coordinator = coordinator,
            state = state,
            tunnelController = tunnelController,
            chainOrchestrator = chainOrchestrator,
            tunnelGateway = tunnelGateway,
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
        val chainOrchestrator: ChainOrchestrator,
        val tunnelGateway: HevTunnelGateway,
        val engineWatchdog: EngineWatchdogCoordinator,
        val sessionRecorder: RecordingSessionStatsRecorder,
        val stopForegroundRequest: () -> Unit,
        val stopSelfRequest: (Int) -> Unit,
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
