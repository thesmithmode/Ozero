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
    ): Pair<EngineWatchdogCoordinator, TunnelController> {
        val controller = TunnelController().apply {
            onProbing(EngineId.WARP)
            onConnecting(EngineId.WARP)
            onEngineStarted(EngineId.WARP, socksPort = 0)
        }
        val watchdog = EngineWatchdogCoordinator(
            scope = scope,
            healthMonitor = HealthMonitor(),
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
    fun `Failed recover не вызывает stopVpn — бесконечный retry, юзер видит жёлтый`() = runTest {
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

        assertTrue(
            plugin.recoverCalls.get() >= 3,
            "recover должен вызываться многократно. Got: ${plugin.recoverCalls.get()}",
        )
        assertEquals(
            0,
            stopVpnCount.get(),
            "stopVpnRequest НИ РАЗУ не должен вызываться при Failed recover — " +
                "юзер хочет бесконечный retry. Регрессия 2026-05-20 (da4e2cda): " +
                "consecutiveRecoverFailures>=3 → hardRestart → handleEngineFailure → stop VPN.",
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
}
