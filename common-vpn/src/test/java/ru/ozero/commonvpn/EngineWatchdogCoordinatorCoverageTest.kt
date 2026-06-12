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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class EngineWatchdogCoordinatorCoverageTest {

    @Test
    fun `health degraded with killswitch and tun enters killswitch mode`() = runTest(UnconfinedTestDispatcher()) {
        val health = degradingHealthMonitor()
        val chain = mockk<ChainOrchestrator>(relaxed = true)
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val statsJob = Job()
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = this,
            healthMonitor = health,
            chain = chain,
            notificationFactory = notification,
            controller = controller,
            tunFd = mockk(relaxed = true),
            statsJob = statsJob,
            killswitch = true,
        )

        watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
        health.start(1080)
        advanceTimeBy(1)

        assertTrue(controller.killswitchActive.value)
        assertTrue(statsJob.isCancelled)
        verify(exactly = 1) { notification.notifyStats(any()) }
        watchdog.cancelWatchers()
        health.shutdown()
    }

    @Test
    fun `degraded health without killswitch keeps connection`() = runTest(UnconfinedTestDispatcher()) {
        val health = degradingHealthMonitor()
        val stopCount = AtomicReference(0)
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = this,
            healthMonitor = health,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = false,
            stopCount = stopCount,
        )

        watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
        health.start(1080)
        advanceTimeBy(1)

        assertFalse(controller.killswitchActive.value)
        assertEquals(0, stopCount.get())
        assertIs<TunnelState.Connected>(controller.state.value)
        watchdog.cancelWatchers()
        health.shutdown()
    }

    @Test
    fun `cancelWatchers cancels health peer and stagnation jobs`() = runTest(UnconfinedTestDispatcher()) {
        val health = degradingHealthMonitor()
        val plugin = FakeWatchdogPlugin()
        val watchdog = watchdog(
            scope = this,
            healthMonitor = health,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startHealthKillswitchWatcher(plugin.id)
        watchdog.startPeerWatchdog(plugin.id)
        watchdog.startStagnationWatchdog(plugin.id)
        watchdog.cancelWatchers()
        health.start(1080)
        advanceTimeBy(1)
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)

        assertEquals(0, plugin.recoverCalls)
        health.shutdown()
    }

    @Test
    fun `peer watchdog recovers after peers disappear after first peer`() = runTest(UnconfinedTestDispatcher()) {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 1), EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.Success),
        )
        val watchdog = watchdog(
            scope = this,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startPeerWatchdog(plugin.id)
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS)
        runCurrent()
        watchdog.cancelWatchers()

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `peer watchdog not supported stops retrying after threshold`() = runTest(UnconfinedTestDispatcher()) {
        val plugin = FakeWatchdogPlugin(
            stats = listOf(EngineStats(activeConnections = 1), EngineStats(activeConnections = 0)),
            recoverResults = listOf(EnginePlugin.RecoverResult.NotSupported),
        )
        val watchdog = watchdog(
            scope = this,
            plugins = setOf(plugin),
            controller = connectedController(plugin.id),
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startPeerWatchdog(plugin.id)
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS * 2)
        runCurrent()
        watchdog.cancelWatchers()

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `stagnation watchdog recovers after consecutive stagnant polls`() = runTest(UnconfinedTestDispatcher()) {
        val plugin = FakeWatchdogPlugin(recoverResults = listOf(EnginePlugin.RecoverResult.Success))
        var clock = 1_000L
        val controller = TunnelController(StatsStagnationMonitor(nowMs = { clock })).apply {
            onProbing(plugin.id)
            onConnecting(plugin.id)
            onEngineStarted(plugin.id, socksPort = 1080)
        }
        val watchdog = watchdog(
            scope = this,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 1))
        clock += StatsStagnationMonitor.STAGNATION_THRESHOLD_MS
        controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 2))
        watchdog.startStagnationWatchdog(plugin.id)
        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS)
        runCurrent()
        watchdog.cancelWatchers()

        assertEquals(1, plugin.recoverCalls)
    }

    @Test
    fun `stagnation watchdog resets when traffic changes`() = runTest(UnconfinedTestDispatcher()) {
        val plugin = FakeWatchdogPlugin(recoverResults = listOf(EnginePlugin.RecoverResult.Success))
        val controller = connectedController(plugin.id)
        val watchdog = watchdog(
            scope = this,
            plugins = setOf(plugin),
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startStagnationWatchdog(plugin.id)
        controller.updateStats(stats(txBytes = 100, rxBytes = 100, timestampMs = 1))
        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_POLL_MS)
        controller.updateStats(stats(txBytes = 101, rxBytes = 100, timestampMs = 2))
        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_POLL_MS)

        assertEquals(0, plugin.recoverCalls)
        watchdog.cancelWatchers()
    }

    @Test
    fun `starting replacement watcher cancels previous health watcher`() = runTest(UnconfinedTestDispatcher()) {
        val health = degradingHealthMonitor()
        val controller = connectedController(EngineId.BYEDPI)
        val watchdog = watchdog(
            scope = this,
            healthMonitor = health,
            controller = controller,
            tunFd = mockk(relaxed = true),
            killswitch = true,
        )

        watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
        watchdog.startHealthKillswitchWatcher(EngineId.BYEDPI)
        health.start(1080)
        advanceTimeBy(1)

        assertTrue(controller.killswitchActive.value)
        watchdog.cancelWatchers()
        health.shutdown()
    }

    @Test
    fun `handleEngineFailure ignores failures while stopping`() = runTest(UnconfinedTestDispatcher()) {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = this,
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
    fun `handleEngineFailure without killswitch stops vpn and marks engine dead`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = this,
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
    fun `handleEngineFailure ignores inactive engine`() = runTest(UnconfinedTestDispatcher()) {
        val controller = connectedController(EngineId.BYEDPI)
        val stopCount = AtomicReference(0)
        val watchdog = watchdog(
            scope = this,
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
    fun `handleEngineFailure enters killswitch when tun missing and killswitch enabled`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        val controller = connectedController(EngineId.BYEDPI)
        val statsJob = Job()
        val notification = mockk<OzeroNotificationFactory>(relaxed = true)
        val watchdog = watchdog(
            scope = this,
            controller = controller,
            tunFd = null,
            statsJob = statsJob,
            killswitch = true,
            notificationFactory = notification,
        )

        val handled = watchdog.handleEngineFailure(EngineId.BYEDPI, "boom")

        assertTrue(handled)
        assertTrue(controller.killswitchActive.value)
        assertTrue(statsJob.isCancelled)
    }

    private fun TestScope.watchdog(
        scope: CoroutineScope = this,
        healthMonitor: HealthMonitor = HealthMonitor(),
        plugins: Set<EnginePlugin> = emptySet(),
        chain: ChainOrchestrator = mockk(relaxed = true),
        notificationFactory: OzeroNotificationFactory = mockk(relaxed = true),
        controller: TunnelController,
        tunFd: ParcelFileDescriptor?,
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
            lockdownStartupFdRef = AtomicReference(null),
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

    private fun TestScope.degradingHealthMonitor(): HealthMonitor = HealthMonitor(
        intervalMs = 1,
        failuresBeforeDegraded = 1,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
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

        override fun stats(): Flow<EngineStats> {
            val index = statsCalls.coerceAtMost(stats.lastIndex)
            statsCalls++
            return flowOf(stats[index])
        }

        override suspend fun recover(): EnginePlugin.RecoverResult {
            val index = recoverCalls.coerceAtMost(recoverResults.lastIndex)
            recoverCalls++
            return recoverResults.getOrElse(index) { EnginePlugin.RecoverResult.Success }
        }
    }
}
