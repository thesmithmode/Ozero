package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineWatchdogInfiniteRetryTest {

    private class FakeFailingPlugin(
        private val result: () -> EnginePlugin.RecoverResult,
    ) : EnginePlugin {
        override val id = EngineId.WARP
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = false,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )
        val statsFlow = MutableStateFlow(EngineStats(activeConnections = 1))
        val recoverCalls = AtomicInteger(0)

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 0)
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("test")
        override fun stats(): Flow<EngineStats> = statsFlow

        override suspend fun recover(): EnginePlugin.RecoverResult {
            recoverCalls.incrementAndGet()
            return result()
        }
    }

    private fun buildWatchdog(
        plugin: EnginePlugin,
        scope: CoroutineScope,
        stopVpnInvocations: AtomicReference<Int>,
        healthMonitor: HealthMonitor = HealthMonitor(),
        controller: TunnelController = TunnelController().apply {
            onProbing(EngineId.WARP)
            onConnecting(EngineId.WARP)
            onEngineStarted(EngineId.WARP, socksPort = 0)
        },
    ): Pair<EngineWatchdogCoordinator, TunnelController> {
        val watchdog = EngineWatchdogCoordinator(
            scope = scope,
            healthMonitor = healthMonitor,
            enginePlugins = setOf(plugin),
            tunnelController = controller,
            chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true),
            notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true),
            tunFdRef = AtomicReference<ParcelFileDescriptor?>(mockk<ParcelFileDescriptor>(relaxed = true)),
            lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null),
            statsJobRef = AtomicReference<Job?>(null),
            stopping = AtomicBoolean(false),
            starting = AtomicBoolean(false),
            killswitchProvider = { false },
            stopVpnRequest = { stopVpnInvocations.updateAndGet { it + 1 } },
        )
        return watchdog to controller
    }

    @Test
    fun `Failed recover reaches stopVpn after bounded retries`() = runTest {
        val plugin = FakeFailingPlugin {
            EnginePlugin.RecoverResult.Failed("UAPI недоступен")
        }
        val stopVpnCount = AtomicReference(0)
        val (watchdog, _) = buildWatchdog(plugin, backgroundScope, stopVpnCount)

        plugin.statsFlow.value = EngineStats(activeConnections = 1)
        watchdog.startPeerWatchdog(EngineId.WARP)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS + 100L)
        runCurrent()
        plugin.statsFlow.value = EngineStats(activeConnections = 0)
        runCurrent()

        val cycleMs = EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS +
            EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS +
            5_000L
        repeat(5) {
            advanceTimeBy(cycleMs)
            runCurrent()
        }

        assertEquals(
            EngineWatchdogCoordinator.PEER_WATCHDOG_MAX_FAILED_RECOVERS,
            plugin.recoverCalls.get(),
            "recover should stop after the bounded Failed retry budget.",
        )
        assertEquals(
            1,
            stopVpnCount.get(),
            "Persistent Failed recover must reach stopVpnRequest when killswitch is off.",
        )
        watchdog.cancelWatchers()
    }

    @Test
    fun `NotSupported не вызывает stopVpn — VPN продолжает работать, watchdog только останавливается`() = runTest {
        val plugin = FakeFailingPlugin {
            EnginePlugin.RecoverResult.NotSupported
        }
        val stopVpnCount = AtomicReference(0)
        val (watchdog, _) = buildWatchdog(plugin, backgroundScope, stopVpnCount)

        plugin.statsFlow.value = EngineStats(activeConnections = 1)
        watchdog.startPeerWatchdog(EngineId.WARP)
        runCurrent()
        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS + 100L)
        runCurrent()
        plugin.statsFlow.value = EngineStats(activeConnections = 0)
        runCurrent()

        advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS + 10_000L)
        runCurrent()

        assertEquals(
            0,
            stopVpnCount.get(),
            "NotSupported НЕ должен убивать VPN — это значит 'engine не поддерживает recover', " +
                "не 'engine сломан'. UI покажет degraded indicator. " +
                "Защита от регрессии: вернуть handleEngineFailure на NotSupported → юзер увидит красную кнопку.",
        )
        watchdog.cancelWatchers()
    }

    @Test
    fun `stagnation watchdog successful recover keeps VPN running`() = runTest {
        var nowMs = 0L
        val controller = TunnelController(
            stagnationMonitor = StatsStagnationMonitor(thresholdMs = 1L, nowMs = { nowMs }),
        ).apply {
            onProbing(EngineId.WARP)
            onConnecting(EngineId.WARP)
            onEngineStarted(EngineId.WARP, socksPort = 0)
        }
        val plugin = FakeFailingPlugin {
            EnginePlugin.RecoverResult.Success
        }
        val stopVpnCount = AtomicReference(0)
        val (watchdog, _) = buildWatchdog(
            plugin = plugin,
            scope = backgroundScope,
            stopVpnInvocations = stopVpnCount,
            controller = controller,
        )

        controller.updateStats(TunnelStats(txPackets = 1, txBytes = 10, rxPackets = 1, rxBytes = 20, timestampMs = 1))
        nowMs = 2
        controller.updateStats(TunnelStats(txPackets = 1, txBytes = 10, rxPackets = 1, rxBytes = 20, timestampMs = 2))
        watchdog.startStagnationWatchdog(EngineId.WARP)
        runCurrent()

        advanceTimeBy(EngineWatchdogCoordinator.STAGNATION_RECOVER_THRESHOLD_MS + 1_000L)
        runCurrent()

        assertTrue(plugin.recoverCalls.get() >= 1)
        assertEquals(0, stopVpnCount.get())
        watchdog.cancelWatchers()
    }

    @Test
    fun `health degraded with killswitch off does not stop VPN from watcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val healthMonitor = HealthMonitor(
            intervalMs = 1L,
            probeTimeoutMs = 1,
            failuresBeforeDegraded = 1,
            dispatcher = dispatcher,
            probe = { _, _, _ -> error("probe fail") },
        )
        val plugin = FakeFailingPlugin {
            EnginePlugin.RecoverResult.Success
        }
        val stopVpnCount = AtomicReference(0)
        val (watchdog, controller) = buildWatchdog(
            plugin = plugin,
            scope = backgroundScope,
            stopVpnInvocations = stopVpnCount,
            healthMonitor = healthMonitor,
        )

        watchdog.startHealthKillswitchWatcher(EngineId.WARP)
        healthMonitor.start(socksPort = 1080)
        advanceTimeBy(2L)
        runCurrent()

        assertEquals(0, stopVpnCount.get())
        assertTrue(controller.state.value is TunnelState.Connected)
        healthMonitor.shutdown()
        watchdog.cancelWatchers()
    }
}
