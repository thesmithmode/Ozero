package ru.ozero.commonvpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("LargeClass")
class StartSequenceCoordinatorExtraTest {

    @Test
    fun `no registered plugins requests stop without probing`() = runTest {
        val fixture = startFixture(
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = null,
                engineAutoPriority = emptyList(),
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, fixture.settingsRepository.reads)
        assertTrue(fixture.stopRequested.get())
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
        verify(exactly = 0) { fixture.tunnelController.onProbing(any()) }
    }

    private fun startFixture(
        vararg engines: FakeEnginePlugin,
        settings: SettingsModel,
        settingsFlow: Flow<SettingsModel>? = null,
        stopping: Boolean = false,
        splitTunnelRulesProvider: SplitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
    ): StartFixture {
        val tunnelController = TunnelController()
        val healthMonitor = mockk<HealthMonitor>(relaxed = true)
        val statsLogger = mockk<TunnelStatsLogger>(relaxed = true)
        val engineWatchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        val tunnelGateway = mockk<HevTunnelGateway>(relaxed = true)
        val tunBuilderHelper = mockk<TunBuilderHelper>(relaxed = true)
        val sessionRecorder = RecordingSessionStatsRecorder()
        val stopRequested = AtomicBoolean(false)
        val killswitchValues = mutableListOf<Boolean>()
        val settingsRepository = StaticSettingsRepository(settingsFlow ?: flowOf(settings))
        coEvery { healthMonitor.start(any()) } returns Unit
        every { statsLogger.start() } returns Unit
        every { engineWatchdog.startHealthKillswitchWatcher(any()) } returns Unit
        every { engineWatchdog.startStagnationWatchdog(any()) } returns Unit
        every { engineWatchdog.startPeerWatchdog(any()) } returns Unit
        every { engineWatchdog.handleEngineFailure(any(), any()) } answers {
            tunnelController.onEngineDied(firstArg(), secondArg())
            stopRequested.set(true)
        }
        val collaborators = StartSequenceCollaborators(
            enginePlugins = engines.toSet(),
            chainOrchestrator = ChainOrchestrator(engines.toSet()),
            tunnelController = tunnelController,
            tunnelGateway = tunnelGateway,
            healthMonitor = healthMonitor,
            tunBuilderHelper = tunBuilderHelper,
            engineWatchdog = engineWatchdog,
            statsLogger = statsLogger,
            splitTunnelRulesProvider = splitTunnelRulesProvider,
            settingsRepository = settingsRepository,
            sessionStatsRecorder = sessionRecorder,
        )
        val state = StartSequenceState(
            tunFdRef = AtomicReference(null),
            tunIfaceNameRef = AtomicReference(null),
            lockdownStartupFdRef = AtomicReference(null),
            sessionStartMsRef = AtomicReference(0L),
            sessionIdRef = AtomicReference(-1L),
            stopping = AtomicBoolean(stopping),
        )
        val coordinator = StartSequenceCoordinator(
            packageName = "ru.ozero.test",
            deps = collaborators,
            state = state,
            killswitchSetter = { killswitchValues += it },
            stopVpnRequest = { stopRequested.set(true) },
        )
        return StartFixture(
            coordinator = coordinator,
            state = state,
            tunnelController = tunnelController,
            tunnelGateway = tunnelGateway,
            healthMonitor = healthMonitor,
            tunBuilderHelper = tunBuilderHelper,
            statsLogger = statsLogger,
            engineWatchdog = engineWatchdog,
            sessionRecorder = sessionRecorder,
            settingsRepository = settingsRepository,
            killswitchValues = killswitchValues,
            stopRequested = stopRequested,
        )
    }

    private data class StartFixture(
        val coordinator: StartSequenceCoordinator,
        val state: StartSequenceState,
        val tunnelController: TunnelController,
        val tunnelGateway: HevTunnelGateway,
        val healthMonitor: HealthMonitor,
        val tunBuilderHelper: TunBuilderHelper,
        val statsLogger: TunnelStatsLogger,
        val engineWatchdog: EngineWatchdogCoordinator,
        val sessionRecorder: RecordingSessionStatsRecorder,
        val settingsRepository: StaticSettingsRepository,
        val killswitchValues: List<Boolean>,
        val stopRequested: AtomicBoolean,
    )

    private class FakeEnginePlugin(
        override val id: EngineId,
        private val socksPort: Int = 2080,
        override val capabilities: EngineCapabilities,
        private val startResult: StartResult = StartResult.Success(socksPort),
        private val readyResult: EnginePlugin.ReadyResult = EnginePlugin.ReadyResult.Ready,
        private val preflightResult: EnginePreflight.Result? = null,
    ) : EnginePlugin {
        val startedConfigs = mutableListOf<EngineConfig>()
        var stopCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startedConfigs += config
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats(activeConnections = 0))
        override suspend fun awaitReady(): EnginePlugin.ReadyResult = readyResult
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig? = engineConfig()
        override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = engineConfig()
        override fun preflight(): EnginePreflight? = preflightResult?.let { result ->
            EnginePreflight { _ -> result }
        }

        private fun engineConfig(): EngineConfig = when (id) {
            EngineId.BYEDPI -> EngineConfig.ByeDpi(socksPort = socksPort)
            EngineId.WARP -> EngineConfig.WarpProxy(socksPort = socksPort)
            EngineId.URNETWORK -> EngineConfig.Urnetwork(jwtToken = "test", socksPort = socksPort)
            EngineId.MASTERDNS -> EngineConfig.MasterDns(
                configToml = "server_addr='127.0.0.1'",
                resolvers = emptyList(),
                socksPort = socksPort,
            )
            EngineId.SINGBOX -> EngineConfig.Singbox(
                beanBlob = byteArrayOf(1),
                protocolType = 1,
                proxyMode = true,
            )
            EngineId.FPTN -> EngineConfig.Fptn(token = "test")
            EngineId.XRAY -> EngineConfig.Xray(configJson = "{}", socksPort = socksPort)
            EngineId.HYSTERIA2 -> EngineConfig.Hysteria2(configJson = "{}", socksPort = socksPort)
            EngineId.NAIVE -> EngineConfig.Naive(proxyUrl = "https://example.invalid", socksPort = socksPort)
            EngineId.TOR -> EngineConfig.Tor(socksPort = socksPort)
        }
    }

    private class StaticSettingsRepository(
        flow: Flow<SettingsModel>,
    ) : SettingsRepository {
        private val backing = flow
        var reads = 0

        override val settings: Flow<SettingsModel>
            get() {
                reads++
                return backing
            }

        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setTrafficMode(mode: TrafficMode) = Unit
        override suspend fun setManualEngine(engine: EngineId?) = Unit
        override suspend fun setEngineAutoPriority(priority: List<EngineId>) = Unit
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
        override suspend fun setByedpiUseUiMode(enabled: Boolean) = Unit
        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
    }

    private class RecordingSessionStatsRecorder : SessionStatsRecorder {
        val startedEngines = mutableListOf<String>()

        override suspend fun startSession(engineId: String, startedAt: Long): Long {
            startedEngines += engineId
            return startedEngines.size.toLong()
        }

        override suspend fun endSession(
            id: Long,
            endedAt: Long,
            rxBytes: Long,
            txBytes: Long,
            durationMs: Long,
            status: SessionStatsRecorder.Status,
        ) = Unit
    }
}
