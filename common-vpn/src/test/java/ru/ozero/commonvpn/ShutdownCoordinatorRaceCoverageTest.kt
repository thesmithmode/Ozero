package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShutdownCoordinatorRaceCoverageTest {

    @Test
    fun `terminal stop is harmless when stopping predates active generation`() = runTest {
        val state = ShutdownState(
            tunFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            tunIfaceNameRef = AtomicReference<String?>(null),
            lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            sessionStartMsRef = AtomicReference(0L),
            sessionIdRef = AtomicReference(-1L),
            startJobRef = AtomicReference<Job?>(null),
            shutdownJobRef = AtomicReference<Job?>(null),
            starting = AtomicBoolean(false),
            stopping = AtomicBoolean(true),
            stopSignal = AtomicBoolean(false),
        )
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val tunnelGateway = mockk<HevTunnelGateway>(relaxed = true)
        val stopSelfRequest = mockk<(Int) -> Unit>(relaxed = true)
        val coordinator = ShutdownCoordinator(
            scope = this,
            deps = ShutdownCollaborators(
                tunnelController = TunnelController(),
                healthMonitor = mockk(relaxed = true),
                chainOrchestrator = chainOrchestrator,
                tunnelGateway = tunnelGateway,
                statsLogger = mockk(relaxed = true),
                engineWatchdog = mockk(relaxed = true),
                sessionStatsRecorder = mockk(relaxed = true),
            ),
            state = state,
            latestStartIdProvider = { 77 },
            stopForegroundRequest = mockk(relaxed = true),
            stopSelfRequest = stopSelfRequest,
        )

        coordinator.stopVpn()

        coVerify(exactly = 0) { chainOrchestrator.stop() }
        verify(exactly = 0) { tunnelGateway.stop() }
        verify(exactly = 0) { stopSelfRequest.invoke(any()) }
        assertTrue(state.stopping.get())
        assertFalse(state.stopSignal.get())
    }

    @Test
    fun `direct shutdown preserves explicit stop request generation id`() = runTest {
        val state = ShutdownState(
            tunFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            tunIfaceNameRef = AtomicReference<String?>(null),
            lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            sessionStartMsRef = AtomicReference(0L),
            sessionIdRef = AtomicReference(-1L),
            startJobRef = AtomicReference<Job?>(null),
            shutdownJobRef = AtomicReference<Job?>(null),
            starting = AtomicBoolean(false),
            stopping = AtomicBoolean(true),
            stopSignal = AtomicBoolean(true),
        )
        val stopSelfRequest = mockk<(Int) -> Unit>(relaxed = true)
        val coordinator = ShutdownCoordinator(
            scope = this,
            deps = ShutdownCollaborators(
                tunnelController = TunnelController(),
                healthMonitor = mockk(relaxed = true),
                chainOrchestrator = mockk(relaxed = true),
                tunnelGateway = mockk(relaxed = true),
                statsLogger = mockk(relaxed = true),
                engineWatchdog = mockk(relaxed = true),
                sessionStatsRecorder = mockk(relaxed = true),
            ),
            state = state,
            latestStartIdProvider = { 77 },
            stopForegroundRequest = mockk(relaxed = true),
            stopSelfRequest = stopSelfRequest,
        )

        coordinator.performShutdown(callStopSelf = true, stopRequestStartId = 10)

        verify(exactly = 1) { stopSelfRequest.invoke(10) }
        verify(exactly = 0) { stopSelfRequest.invoke(77) }
        assertFalse(state.stopping.get())
        assertFalse(state.stopSignal.get())
    }
}
