package ru.ozero.commonvpn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OzeroVpnServiceStartCoordinatorTest {

    @Test
    fun `start resets runtime flags and runs immediate start sequence`() = runTest {
        val fixture = fixture()
        fixture.stopSignal.set(true)
        fixture.restartCancelled.set(true)
        fixture.restartInProgress.set(true)

        fixture.coordinator.start()
        runCurrent()

        assertEquals(false, fixture.stopSignal.get())
        assertEquals(false, fixture.restartCancelled.get())
        assertEquals(false, fixture.restartInProgress.get())
        assertEquals(false, fixture.starting.get())
        assertEquals(1, fixture.closeStaleTunCalls)
        assertEquals(1, fixture.killswitchReleasedCalls)
        assertEquals(1, fixture.loadCalls)
        assertEquals(1, fixture.startSequenceCalls)
        assertNotNull(fixture.startJobRef.get())
    }

    @Test
    fun `duplicate start is ignored while first start is active`() = runTest {
        val blocker = CompletableDeferred<Unit>()
        val fixture = fixture(startSequence = { blocker.await() })

        fixture.coordinator.start()
        fixture.coordinator.start()
        runCurrent()

        assertEquals(1, fixture.closeStaleTunCalls)
        assertEquals(1, fixture.loadCalls)
        assertEquals(1, fixture.startSequenceCalls)

        blocker.complete(Unit)
        runCurrent()
        assertEquals(false, fixture.starting.get())
    }

    @Test
    fun `start waits previous shutdown and clears shutdown reference`() = runTest {
        val shutdown = Job()
        val fixture = fixture()
        fixture.shutdownJobRef.set(shutdown)

        fixture.coordinator.start()
        runCurrent()

        assertEquals(0, fixture.startSequenceCalls)
        assertNull(fixture.shutdownJobRef.get())

        shutdown.complete()
        runCurrent()

        assertEquals(1, fixture.startSequenceCalls)
        assertEquals(false, fixture.starting.get())
    }

    @Test
    fun `start aborts after shutdown when stopping remains active`() = runTest {
        val shutdown = Job()
        val fixture = fixture()
        fixture.shutdownJobRef.set(shutdown)
        fixture.stopping.set(true)

        fixture.coordinator.start()
        runCurrent()
        shutdown.complete()
        runCurrent()

        assertEquals(0, fixture.startSequenceCalls)
        assertEquals(false, fixture.starting.get())
        assertEquals(false, fixture.restartInProgress.get())
    }

    @Test
    fun `start while stopping active skips sequence and still drops in-flight start flag`() = runTest {
        val fixture = fixture()
        fixture.stopping.set(true)

        fixture.coordinator.start()
        runCurrent()

        assertEquals(1, fixture.closeStaleTunCalls)
        assertEquals(1, fixture.killswitchReleasedCalls)
        assertEquals(1, fixture.loadCalls)
        assertEquals(0, fixture.startSequenceCalls)
        assertEquals(false, fixture.starting.get())
    }

    @Test
    fun `external vpn detection does not delay start sequence`() = runTest {
        val fixture = fixture(externalVpnActive = true)

        fixture.coordinator.start()
        runCurrent()

        assertEquals(1, fixture.closeStaleTunCalls)
        assertEquals(1, fixture.killswitchReleasedCalls)
        assertEquals(1, fixture.loadCalls)
        assertEquals(1, fixture.startSequenceCalls)
    }

    @Test
    fun `close stale tun and load library exceptions do not prevent start`() = runTest {
        val fixture = fixture(
            closeStaleTun = { error("close failed") },
            loadTunnelLibrary = { error("load failed") },
        )

        fixture.coordinator.start()
        runCurrent()

        assertEquals(1, fixture.startSequenceCalls)
        assertEquals(false, fixture.starting.get())
    }

    private fun TestScope.fixture(
        externalVpnActive: Boolean = false,
        closeStaleTun: () -> Unit = {},
        loadTunnelLibrary: () -> Unit = {},
        startSequence: suspend () -> Unit = {},
    ): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val fixture = Fixture(scope)
        fixture.coordinator = OzeroVpnServiceStartCoordinator(
            deps = OzeroVpnServiceStartCoordinator.Dependencies(
                scope = scope,
                startJobRef = fixture.startJobRef,
                shutdownJobRef = fixture.shutdownJobRef,
                starting = fixture.starting,
                stopping = fixture.stopping,
                stopSignal = fixture.stopSignal,
                runtimeConfigRestartCancelled = fixture.restartCancelled,
                runtimeConfigRestartInProgress = fixture.restartInProgress,
                shutdownJoinTimeoutMs = 100L,
                closeStaleTun = {
                    fixture.closeStaleTunCalls++
                    runCatching { closeStaleTun() }
                },
                onKillswitchReleased = { fixture.killswitchReleasedCalls++ },
                logActiveExternalVpn = { externalVpnActive },
                loadTunnelLibrary = {
                    fixture.loadCalls++
                    runCatching { loadTunnelLibrary() }
                },
                isTunnelLibraryLoaded = { true },
                startSequence = {
                    fixture.startSequenceCalls++
                    startSequence()
                },
            ),
        )
        return fixture
    }

    private class Fixture(
        val scope: CoroutineScope,
    ) {
        val startJobRef = AtomicReference<Job?>()
        val shutdownJobRef = AtomicReference<Job?>()
        val starting = AtomicBoolean(false)
        val stopping = AtomicBoolean(false)
        val stopSignal = AtomicBoolean(false)
        val restartCancelled = AtomicBoolean(false)
        val restartInProgress = AtomicBoolean(false)
        var closeStaleTunCalls = 0
        var killswitchReleasedCalls = 0
        var loadCalls = 0
        var startSequenceCalls = 0
        lateinit var coordinator: OzeroVpnServiceStartCoordinator
    }
}
