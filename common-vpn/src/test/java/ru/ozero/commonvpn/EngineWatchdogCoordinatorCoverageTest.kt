package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class EngineWatchdogCoordinatorCoverageTest {

    @Test
    fun `health degraded with killswitch and tun enters killswitch mode`() = runTest {
        val health = degradingHealthMonitor()
        val chain = mockk<ChainOrchestrator>(relaxed = true)
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val statsJob = Job()
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            chain = chain,
            notificationFactory = notification,
            controller = controller,
            tunFd = mockk(relaxed = true),
            statsJob = statsJob,
            killswitch = true,
        )

        try {
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()

            assertTrue(controller.killswitchActive.value)
            assertTrue(statsJob.isCancelled)
            verify(exactly = 1) { notification.notifyStats(any()) }
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    @Test
    fun `degraded health without killswitch keeps connection`() = runTest {
        val health = degradingHealthMonitor()
        val stopCount = AtomicReference(0)
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = false,
            stopCount = stopCount,
        )

        try {
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()

            assertFalse(controller.killswitchActive.value)
            assertEquals(0, stopCount.get())
            assertIs<TunnelState.Connected>(controller.state.value)
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    @Test
    fun `cancelWatchers cancels health peer and stagnation jobs`() = runTest {
        val health = degradingHealthMonitor()
        val plugin = FakeWatchdogPlugin()
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startHealthKillswitchWatcher(plugin.id)
            watchdog.startPeerWatchdog(plugin.id)
            watchdog.startStagnationWatchdog(plugin.id)
            watchdog.cancelWatchers()
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
            runCurrent()

            assertEquals(0, plugin.recoverCalls)
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    @Test
    fun `peer watchdog recovers after peers disappear after first peer`() = runTest {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 1), EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.Success),
        )
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
            runCurrent()
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog not supported stops retrying after threshold`() = runTest {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 1), EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.NotSupported),
        )
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
            runCurrent()
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS * 2)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog recovers before first peer when policy allows it`() = runTest {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.Failed("still offline")),
            policy = EnginePlugin.PeerWatchdogPolicy(
                timeoutMs = EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS,
                recoverBeforeFirstPeer = true,
            ),
        )
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog ignores zero peers before first peer by default`() = runTest {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 0)),
            policy = EnginePlugin.PeerWatchdogPolicy(
                timeoutMs = EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS,
                recoverBeforeFirstPeer = false,
            ),
        )
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS * 3)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `startPeerWatchdog with missing plugin is no-op`() = runTest {
        val plugin = FakeWatchdogPlugin(id = EngineId.WARP)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(EngineId.BYEDPI),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startPeerWatchdog(EngineId.BYEDPI)
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS)
        runCurrent()

        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog converts recover exception into failed retry`() = runTest {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 1), EngineStats(activeConnections = 0)),
            recoverThrows = true,
            policy = EnginePlugin.PeerWatchdogPolicy(
                timeoutMs = EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS,
                recoverBeforeFirstPeer = false,
            ),
        )
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS * 2)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog swallows stats failure`() = runTest {
        val plugin = FakeWatchdogPlugin(statsThrows = true)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startPeerWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `stagnation watchdog recovers after consecutive stagnant polls`() = runTest {
        val plugin = FakeWatchdogPlugin(recoverResults = listOf(EnginePlugin.RecoverResult.Success))
        var clock = 1_000L
        val controller = TunnelController(StatsStagnationMonitor(nowMs = { clock })).apply {
            onProbing(plugin.id)
            onConnecting(plugin.id)
            onEngineStarted(plugin.id, socksPort = 1080)
        }
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 1))
            clock += StatsStagnationMonitor.STAGNATION_THRESHOLD_MS
            controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 2))
            watchdog.startStagnationWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `stagnation watchdog resets when traffic changes`() = runTest {
        val plugin = FakeWatchdogPlugin(recoverResults = listOf(EnginePlugin.RecoverResult.Success))
        val controller = connectedController(plugin.id)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startStagnationWatchdog(plugin.id)
            controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 1))
            advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_POLL_MS)
            runCurrent()
            controller.updateStats(stats(txBytes = 101, rxBytes = 100, timestampMs = 2))
            advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_POLL_MS)
            runCurrent()

            assertEquals(0, plugin.recoverCalls)
        } finally {
            watchdog.cancelWatchers()
        }
    }

    @Test
    fun `stagnation watchdog stops when recover is not supported`() = runTest {
        val plugin = FakeWatchdogPlugin(recoverResults = listOf(EnginePlugin.RecoverResult.NotSupported))
        val controller = stagnantController(plugin.id)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startStagnationWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS * 2)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `stagnation watchdog converts recover exception into failed retry`() = runTest {
        val plugin = FakeWatchdogPlugin(recoverThrows = true)
        val controller = stagnantController(plugin.id)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startStagnationWatchdog(plugin.id)
            advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS)
            runCurrent()
        } finally {
            watchdog.cancelWatchers()
        }

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `startStagnationWatchdog with missing plugin is no-op`() = runTest {
        val plugin = FakeWatchdogPlugin(id = EngineId.WARP)
        val controller = stagnantController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startStagnationWatchdog(EngineId.BYEDPI)
        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_POLL_MS)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS)
        runCurrent()

        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `starting replacement watcher cancels previous health watcher`() = runTest {
        val health = degradingHealthMonitor()
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        try {
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()

            assertTrue(controller.killswitchActive.value)
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    @Test
    fun `handleEngineFailure ignores failures while stopping`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopping = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "stopping")

        assertFalse(handled)
        assertEquals(0, stopCount.get())
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure without killswitch stops vpn and marks engine dead`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = false,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "boom")

        assertFalse(handled)
        assertEquals(1, stopCount.get())
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure for failed state with same engine enters killswitch`() = runTest {
        val controller = TunnelController().apply {
            onProbing(EngineId.BYEDPI)
            onConnecting(EngineId.BYEDPI)
            onEngineStarted(EngineId.BYEDPI, socksPort = 1080)
            onEngineDied(EngineId.BYEDPI, "already failed")
        }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "failed state retry")

        assertTrue(handled)
        assertEquals(0, stopCount.get())
        assertTrue(controller.killswitchActive.value)
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure ignores inactive engine`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "inactive")

        assertFalse(handled)
        assertEquals(0, stopCount.get())
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure active with killswitch cancels health peer and stagnation jobs`() = runTest {
        val plugin = FakeWatchdogPlugin(
            id = EngineId.BYEDPI,
            stats = listOf(EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.Success),
        )
        val health = degradingHealthMonitor()
        val stopCount = AtomicReference(0)
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
        watchdog.startPeerWatchdog(EngineId.BYEDPI)
        watchdog.startStagnationWatchdog(EngineId.BYEDPI)
        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "killswitch cancels jobs")
        runCurrent()
        health.shutdown()

        assertTrue(handled)
        assertEquals(0, stopCount.get())
        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `handleEngineFailure ignores probing state for different engine`() = runTest {
        val controller = TunnelController().apply { onProbing(EngineId.BYEDPI) }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "different probe")

        assertFalse(handled)
        assertEquals(0, stopCount.get())
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure connecting state for same engine is active`() = runTest {
        val controller = TunnelController().apply {
            onProbing(EngineId.WARP)
            onConnecting(EngineId.WARP)
        }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = null,
            killswitch = false,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "connect failed")

        assertFalse(handled)
        assertEquals(1, stopCount.get())
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure uses lockdown startup fd as blocking tun`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = null,
            lockdownFd = mockk(relaxed = true),
            killswitch = true,
            notificationFactory = notification,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "startup failed")

        assertTrue(handled)
        assertTrue(controller.killswitchActive.value)
        verify(exactly = 1) { notification.notifyStats(any()) }
    }

    @Test
    fun `handleEngineFailure enters killswitch even when chain stop throws`() = runTest {
        val chain = mockk<ChainOrchestrator>()
        val controller = connectedController(EngineId.BYEDPI)
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        coEvery { chain.stop() } throws IllegalStateException("stop failed")
        val watchdog = watchdog(
            scope = backgroundScope,
            chain = chain,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            notificationFactory = notification,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "fatal")
        runCurrent()

        assertTrue(handled)
        assertTrue(controller.killswitchActive.value)
        verify(exactly = 1) { notification.notifyStats(any()) }
    }

    @Test
    fun `handleEngineFailure failed state for same engine is still active`() = runTest {
        val controller = connectedController(EngineId.WARP).apply {
            onEngineDied(EngineId.WARP, "previous failure")
        }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = null,
            killswitch = false,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "second failure")

        assertFalse(handled)
        assertEquals(1, stopCount.get())
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure ignores idle and disconnecting states`() = runTest {
        val idleController = TunnelController()
        val idleStops = AtomicReference(0)
        val idleWatchdog = watchdog(
            scope = backgroundScope,
            controller = idleController,
            tunFd = null,
            killswitch = false,
            stopCount = idleStops,
        )

        val idleHandled = idleWatchdog.handleEngineFailure(EngineId.WARP, "idle")

        val disconnectingController = connectedController(EngineId.WARP).apply { onDisconnecting() }
        val disconnectingStops = AtomicReference(0)
        val disconnectingWatchdog = watchdog(
            scope = backgroundScope,
            controller = disconnectingController,
            tunFd = null,
            killswitch = false,
            stopCount = disconnectingStops,
        )

        val disconnectingHandled = disconnectingWatchdog.handleEngineFailure(EngineId.WARP, "disconnecting")

        assertFalse(idleHandled)
        assertFalse(disconnectingHandled)
        assertEquals(0, idleStops.get())
        assertEquals(0, disconnectingStops.get())
    }

    @Test
    fun `handleEngineFailure active with killswitch enters killswitch and cancels jobs`() = runTest {
        val plugin = FakeWatchdogPlugin(
            id = EngineId.BYEDPI,
            stats = listOf(
                EngineStats(activeConnections = 1),
                EngineStats(activeConnections = 0),
            ),
            recoverResults = listOf(EnginePlugin.RecoverResult.Success),
        )
        val controller = connectedController(EngineId.BYEDPI)
        val statsJob = Job()
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            statsJob = statsJob,
            killswitch = true,
            stopCount = stopCount,
        )

        watchdog.startPeerWatchdog(EngineId.BYEDPI)

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "peer watchdog")

        assertTrue(handled)
        assertEquals(0, stopCount.get())
        assertIs<TunnelState.Failed>(controller.state.value)
        assertTrue(controller.killswitchActive.value)
        assertTrue(statsJob.isCancelled)
        assertEquals(0, plugin.recoverCalls)
    }

    @Test
    fun `handleEngineFailure probing state without engine id is treated as active`() = runTest {
        val controller = TunnelController().apply { onProbing() }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "startup")

        assertTrue(handled)
        assertEquals(0, stopCount.get())
        assertTrue(controller.killswitchActive.value)
    }

    @Test
    fun `handleEngineFailure probing state for same engine is active`() = runTest {
        val controller = TunnelController().apply { onProbing(EngineId.WARP) }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "probe failed")

        assertTrue(handled)
        assertEquals(0, stopCount.get())
        assertTrue(controller.killswitchActive.value)
    }

    @Test
    fun `handleEngineFailure connecting state for different engine is inactive`() = runTest {
        val controller = TunnelController().apply {
            onProbing(EngineId.BYEDPI)
            onConnecting(EngineId.BYEDPI)
        }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "stale connect failure")

        assertFalse(handled)
        assertEquals(0, stopCount.get())
        assertEquals(TunnelState.Connecting(EngineId.BYEDPI), controller.state.value)
    }

    @Test
    fun `handleEngineFailure stops vpn when tun missing and killswitch enabled`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val statsJob = Job()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = null,
            statsJob = statsJob,
            killswitch = true,
            notificationFactory = notification,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "boom")

        assertFalse(handled)
        assertFalse(controller.killswitchActive.value)
        assertFalse(statsJob.isCancelled)
        assertEquals(1, stopCount.get())
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure connected with killswitch but no blocking tun stops vpn`() = runTest {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = null,
            lockdownFd = null,
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "no fd")

        assertFalse(handled)
        assertEquals(1, stopCount.get())
        assertFalse(controller.killswitchActive.value)
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun `handleEngineFailure failed state with different engine is ignored`() = runTest {
        val controller = connectedController(EngineId.BYEDPI).apply {
            onEngineDied(EngineId.BYEDPI, "first")
        }
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopCount = stopCount,
        )

        val handled = watchdog.handleEngineFailure(EngineId.WARP, "other")

        assertFalse(handled)
        assertEquals(0, stopCount.get())
        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
    }

    @Test
    fun `health degraded with killswitch true but no fd logs without stop`() = runTest {
        val health = degradingHealthMonitor()
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            controller = controller,
            tunFd = null,
            lockdownFd = null,
            killswitch = true,
            stopCount = stopCount,
        )

        try {
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()

            assertFalse(controller.killswitchActive.value)
            assertEquals(0, stopCount.get())
            assertIs<TunnelState.Connected>(controller.state.value)
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    @Test
    fun `health degraded while stopping does not enter killswitch`() = runTest {
        val health = degradingHealthMonitor()
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = backgroundScope,
            healthMonitor = health,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
            stopping = true,
            stopCount = stopCount,
        )

        try {
            watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
            health.start(1080)
            advanceTimeBy(1)
            runCurrent()

            assertFalse(controller.killswitchActive.value)
            assertEquals(0, stopCount.get())
            assertIs<TunnelState.Connected>(controller.state.value)
        } finally {
            watchdog.cancelWatchers()
            health.shutdown()
        }
    }

    private fun TestScope.watchdog(
        scope: CoroutineScope = backgroundScope,
        healthMonitor: HealthMonitor = HealthMonitor(),
        plugins: Set<EnginePlugin> = emptySet(),
        chain: ChainOrchestrator = mockk(relaxed = true),
        notificationFactory: OzeroNotificationFactory = mockk(relaxed = true),
        controller: TunnelController,
        tunFd: ParcelFileDescriptor?,
        lockdownFd: ParcelFileDescriptor? = null,
        statsJob: Job? = null,
        killswitch: Boolean,
        stopping: Boolean = false,
        stopCount: AtomicReference<Int> = AtomicReference(0),
    ): EngineWatchdogCoordinator {
        coEvery { chain.stop() } returns Unit
        every { notificationFactory.notifyStats(any()) } returns Unit
        return EngineWatchdogCoordinator(
            scope = scope,
            healthMonitor = healthMonitor,
            enginePlugins = plugins,
            tunnelController = controller,
            chainOrchestrator = chain,
            notificationFactory = notificationFactory,
            tunFdRef = AtomicReference(tunFd),
            lockdownStartupFdRef = AtomicReference(lockdownFd),
            statsJobRef = AtomicReference(statsJob),
            stopping = AtomicBoolean(stopping),
            starting = AtomicBoolean(true),
            killswitchProvider = { killswitch },
            stopVpnRequest = { stopCount.updateAndGet { it + 1 } },
        )
    }

    private fun connectedController(engineId: EngineId): TunnelController = TunnelController().apply {
        onProbing(engineId)
        onConnecting(engineId)
        onEngineStarted(engineId, socksPort = 1080)
    }

    private fun stagnantController(engineId: EngineId): TunnelController {
        var clock = 1_000L
        return TunnelController(StatsStagnationMonitor(nowMs = { clock })).apply {
            onProbing(engineId)
            onConnecting(engineId)
            onEngineStarted(engineId, socksPort = 1080)
            updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 1))
            clock += StatsStagnationMonitor.STAGNATION_THRESHOLD_MS
            updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 2))
        }
    }

    private fun TestScope.degradingHealthMonitor(): HealthMonitor = HealthMonitor(
        intervalMs = 1,
        failuresBeforeDegraded = 1,
        dispatcher = StandardTestDispatcher(testScheduler),
        probe = { _, _, _ -> error("down") },
    )

    private fun stats(txBytes: Long, rxBytes: Long, timestampMs: Long): TunnelStats = TunnelStats(
        txPackets = txBytes,
        txBytes = txBytes,
        rxPackets = rxBytes,
        rxBytes = rxBytes,
        timestampMs = timestampMs,
    )

    private class FakeWatchdogPlugin(
        override val id: EngineId = EngineId.WARP,
        private val stats: List<EngineStats> = listOf(EngineStats(activeConnections = 0)),
        private val recoverResults: List<EnginePlugin.RecoverResult> = emptyList(),
        private val policy: EnginePlugin.PeerWatchdogPolicy = EnginePlugin.PeerWatchdogPolicy(),
        private val recoverThrows: Boolean = false,
        private val statsThrows: Boolean = false,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )
        var recoverCalls = 0
            private set
        private var statsCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(1080)

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Success(1)
        override fun peerWatchdogPolicy(): EnginePlugin.PeerWatchdogPolicy = policy

        override fun stats(): Flow<EngineStats> {
            if (statsThrows) error("stats down")
            val index = statsCalls.coerceAtMost(stats.lastIndex)
            statsCalls++
            return flowOf(stats[index])
        }

        override suspend fun recover(): EnginePlugin.RecoverResult {
            recoverCalls++
            if (recoverThrows) error("recover down")
            val index = (recoverCalls - 1).coerceAtMost(recoverResults.lastIndex)
            return recoverResults.getOrElse(index) { EnginePlugin.RecoverResult.Success }
        }
    }
}
