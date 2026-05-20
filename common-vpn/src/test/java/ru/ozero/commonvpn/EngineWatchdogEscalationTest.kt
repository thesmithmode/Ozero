package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class EngineWatchdogEscalationTest {

    private class FakeRecoverPlugin(
        private val recoverResultProvider: () -> EnginePlugin.RecoverResult,
        private val hardRestartResultProvider: () -> EnginePlugin.RecoverResult,
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
        val hardRestartCalls = AtomicInteger(0)

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 0)
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("test")
        override fun stats(): Flow<EngineStats> = statsFlow

        override suspend fun recover(): EnginePlugin.RecoverResult {
            recoverCalls.incrementAndGet()
            return recoverResultProvider()
        }

        override suspend fun hardRestart(): EnginePlugin.RecoverResult {
            hardRestartCalls.incrementAndGet()
            return hardRestartResultProvider()
        }
    }

    private fun buildWatchdog(
        plugin: EnginePlugin,
        controller: TunnelController,
        scope: CoroutineScope,
        stopVpnInvocations: AtomicReference<Int>,
    ): EngineWatchdogCoordinator {
        val healthMonitor = HealthMonitor()
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        val tunFdRef = AtomicReference<ParcelFileDescriptor?>(mockk<ParcelFileDescriptor>(relaxed = true))
        val statsJobRef = AtomicReference<Job?>(null)
        val stopping = AtomicBoolean(false)
        val starting = AtomicBoolean(false)
        return EngineWatchdogCoordinator(
            scope = scope,
            healthMonitor = healthMonitor,
            enginePlugins = setOf(plugin),
            tunnelController = controller,
            chainOrchestrator = chainOrchestrator,
            notificationFactory = notificationFactory,
            tunFdRef = tunFdRef,
            statsJobRef = statsJobRef,
            stopping = stopping,
            starting = starting,
            killswitchProvider = { false },
            stopVpnRequest = { stopVpnInvocations.updateAndGet { it + 1 } },
        )
    }

    @Test
    fun `3 подряд Failed recover вызывают hardRestart ровно один раз`() =
        runTest(UnconfinedTestDispatcher()) {
            val plugin = FakeRecoverPlugin(
                recoverResultProvider = { EnginePlugin.RecoverResult.Failed("upstream timeout") },
                hardRestartResultProvider = { EnginePlugin.RecoverResult.Success },
            )
            val controller = TunnelController().apply {
                onProbing(EngineId.WARP)
                onConnecting(EngineId.WARP)
                onEngineStarted(EngineId.WARP, socksPort = 0)
            }
            val stopVpnCount = AtomicReference(0)
            val watchdog = buildWatchdog(
                plugin = plugin,
                controller = controller,
                scope = this,
                stopVpnInvocations = stopVpnCount,
            )

            plugin.statsFlow.value = EngineStats(activeConnections = 1)
            watchdog.startPeerWatchdog(EngineId.WARP)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS + 50L)

            plugin.statsFlow.value = EngineStats(activeConnections = 0)

            val ticks = EngineWatchdogCoordinator.RECOVER_FAILURE_ESCALATION_THRESHOLD
            repeat(ticks) {
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS + 2_000L)
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS + 1_000L)
            }

            assertEquals(
                EngineWatchdogCoordinator.RECOVER_FAILURE_ESCALATION_THRESHOLD,
                plugin.recoverCalls.get(),
                "recover() обязан быть вызван столько раз, сколько threshold (3) — счётчик растёт по Failed",
            )
            assertEquals(
                1,
                plugin.hardRestartCalls.get(),
                "hardRestart обязан вызваться РОВНО один раз после $ticks подряд Failed recover",
            )
            watchdog.cancelWatchers()
        }

    @Test
    fun `hardRestart Failed → handleEngineFailure → stopVpn вызван`() =
        runTest(UnconfinedTestDispatcher()) {
            val plugin = FakeRecoverPlugin(
                recoverResultProvider = { EnginePlugin.RecoverResult.Failed("upstream timeout") },
                hardRestartResultProvider = { EnginePlugin.RecoverResult.Failed("kill process не удался") },
            )
            val controller = TunnelController().apply {
                onProbing(EngineId.WARP)
                onConnecting(EngineId.WARP)
                onEngineStarted(EngineId.WARP, socksPort = 0)
            }
            val stopVpnCount = AtomicReference(0)
            val watchdog = buildWatchdog(
                plugin = plugin,
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                stopVpnInvocations = stopVpnCount,
            )

            plugin.statsFlow.value = EngineStats(activeConnections = 1)
            watchdog.startPeerWatchdog(EngineId.WARP)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS + 50L)
            plugin.statsFlow.value = EngineStats(activeConnections = 0)

            val ticks = EngineWatchdogCoordinator.RECOVER_FAILURE_ESCALATION_THRESHOLD
            repeat(ticks) {
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS + 2_000L)
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS + 1_000L)
            }

            assertEquals(1, plugin.hardRestartCalls.get())
            assertTrue(
                stopVpnCount.get() >= 1,
                "hardRestart Failed → handleEngineFailure обязан вызвать stopVpnRequest. Got: ${stopVpnCount.get()}",
            )
            watchdog.cancelWatchers()
        }

    @Test
    fun `Success recover сбрасывает счётчик consecutiveRecoverFailures`() =
        runTest(UnconfinedTestDispatcher()) {
            var failCount = 0
            val plugin = FakeRecoverPlugin(
                recoverResultProvider = {
                    if (failCount < 2) {
                        failCount += 1
                        EnginePlugin.RecoverResult.Failed("transient")
                    } else {
                        EnginePlugin.RecoverResult.Success
                    }
                },
                hardRestartResultProvider = { EnginePlugin.RecoverResult.Success },
            )
            val controller = TunnelController().apply {
                onProbing(EngineId.WARP)
                onConnecting(EngineId.WARP)
                onEngineStarted(EngineId.WARP, socksPort = 0)
            }
            val stopVpnCount = AtomicReference(0)
            val watchdog = buildWatchdog(
                plugin = plugin,
                controller = controller,
                scope = this,
                stopVpnInvocations = stopVpnCount,
            )

            plugin.statsFlow.value = EngineStats(activeConnections = 1)
            watchdog.startPeerWatchdog(EngineId.WARP)
            advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_POLL_MS + 50L)
            plugin.statsFlow.value = EngineStats(activeConnections = 0)

            repeat(5) {
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_TIMEOUT_MS + 2_000L)
                advanceTimeBy(EngineWatchdogCoordinator.PEER_WATCHDOG_RECOVER_GRACE_MS + 1_000L)
            }

            assertEquals(
                0,
                plugin.hardRestartCalls.get(),
                "hardRestart НЕ должен вызываться когда Success прерывает серию — счётчик сбрасывается",
            )
            watchdog.cancelWatchers()
        }
}
