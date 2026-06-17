package ru.ozero.app.vpn

import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.EngineWatchdogCoordinator
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.SessionStatsRecorder
import ru.ozero.commonvpn.SplitTunnelRulesProvider
import ru.ozero.commonvpn.StartSequenceCollaborators
import ru.ozero.commonvpn.StartSequenceCoordinator
import ru.ozero.commonvpn.StartSequenceState
import ru.ozero.commonvpn.TunBuilderHelper
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelStatsLogger
import ru.ozero.enginebyedpi.ByeDpiEngine
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StartSequenceByeDpiSettingsIntegrationTest {

    private val healthMonitor = HealthMonitor(intervalMs = Long.MAX_VALUE)

    @AfterEach
    fun tearDown() {
        healthMonitor.shutdown()
    }

    @Test
    fun `manual ByeDPI CMD settings reach runtime config through start sequence`() = runTest {
        val plugin = RecordingByeDpiPlugin()
        val fixture = startFixture(
            plugin = plugin,
            settings = SettingsModel.DEFAULT.copy(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.BYEDPI,
                byedpiUseUiMode = false,
                byedpiWinningArgs = "  -Y -Ar -s5  ",
            ),
        )

        fixture.coordinator.run()

        val config = assertIs<EngineConfig.ByeDpi>(plugin.startedConfigs.single())
        assertEquals("-Y -Ar -s5", config.args)
        assertEquals(ByeDpiEngine.AUTO_ROTATE_PORT, config.socksPort)
        assertTrue("-Ku" !in config.args)
        assertTrue("-An" !in config.args)
        assertEquals(1, plugin.startCalls)
        assertEquals(0, plugin.manualConfigCalls)
        assertEquals(1, plugin.proxyConfigCalls)
        assertEquals(listOf("BYEDPI:PROXY"), fixture.sessionRecorder.engineIds)
    }

    @Test
    fun `manual ByeDPI UI settings override stale winning args during start sequence`() = runTest {
        val plugin = RecordingByeDpiPlugin()
        val fixture = startFixture(
            plugin = plugin,
            settings = SettingsModel.DEFAULT.copy(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.BYEDPI,
                byedpiUseUiMode = true,
                byedpiWinningArgs = "-Y -Ar -s5",
                byedpiUiSettings = ByeDpiUiSettings.DEFAULT.copy(
                    desyncMethod = ByeDpiUiSettings.DesyncMethod.SPLIT,
                    splitPosition = 7,
                ),
            ),
        )

        fixture.coordinator.run()

        val config = assertIs<EngineConfig.ByeDpi>(plugin.startedConfigs.single())
        assertTrue("-s7" in config.args)
        assertEquals(ByeDpiEngine.AUTO_ROTATE_PORT, config.socksPort)
        assertTrue("-Y -Ar -s5" !in config.args)
        assertEquals(1, plugin.startCalls)
        assertEquals(0, plugin.manualConfigCalls)
        assertEquals(1, plugin.proxyConfigCalls)
        assertEquals(listOf("BYEDPI:PROXY"), fixture.sessionRecorder.engineIds)
    }

    private fun startFixture(
        plugin: RecordingByeDpiPlugin,
        settings: SettingsModel,
    ): StartFixture {
        val tunnelController = TunnelController()
        val sessionRecorder = RecordingSessionStatsRecorder()
        val collaborators = StartSequenceCollaborators(
            enginePlugins = setOf(plugin),
            chainOrchestrator = ChainOrchestrator(setOf(plugin)),
            tunnelController = tunnelController,
            tunnelGateway = object : HevTunnelGateway {
                override fun start(config: ru.ozero.commonvpn.HevTunnelConfig): Int = error("TUN gateway unused")
                override fun stop() = Unit
            },
            healthMonitor = healthMonitor,
            tunBuilderHelper = mockk<TunBuilderHelper>(relaxed = true),
            engineWatchdog = mockk<EngineWatchdogCoordinator>(relaxed = true),
            statsLogger = mockk<TunnelStatsLogger>(relaxed = true),
            splitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
            settingsRepository = StaticSettingsRepository(settings),
            sessionStatsRecorder = sessionRecorder,
        )
        val state = StartSequenceState(
            tunFdRef = AtomicReference(null),
            tunIfaceNameRef = AtomicReference(null),
            lockdownStartupFdRef = AtomicReference(null),
            sessionStartMsRef = AtomicReference(0L),
            sessionIdRef = AtomicReference(-1L),
            stopping = AtomicBoolean(false),
        )
        return StartFixture(
            coordinator = StartSequenceCoordinator(
                packageName = "ru.ozero.test",
                deps = collaborators,
                state = state,
                killswitchSetter = {},
                stopVpnRequest = {},
            ),
            sessionRecorder = sessionRecorder,
        )
    }

    private data class StartFixture(
        val coordinator: StartSequenceCoordinator,
        val sessionRecorder: RecordingSessionStatsRecorder,
    )

    private class RecordingByeDpiPlugin : EnginePlugin {
        private val delegate = ByeDpiEngine()
        val startedConfigs = mutableListOf<EngineConfig>()
        var startCalls = 0
        var manualConfigCalls = 0
        var proxyConfigCalls = 0

        override val id: EngineId = EngineId.BYEDPI
        override val capabilities: EngineCapabilities = delegate.capabilities

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            assertEquals(Upstream.None, upstream)
            startCalls += 1
            startedConfigs += config
            return StartResult.Success(socksPort = 49_152)
        }

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = emptyFlow()
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig? {
            manualConfigCalls += 1
            return delegate.buildManualConfig(settings)
        }

        override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? {
            proxyConfigCalls += 1
            return delegate.buildProxyConfig(settings)
        }

        override suspend fun awaitReady(): EnginePlugin.ReadyResult = EnginePlugin.ReadyResult.Ready
    }

    private class StaticSettingsRepository(settings: SettingsModel) : SettingsRepository {
        override val settings: Flow<SettingsModel> = flowOf(settings)
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
        val engineIds = mutableListOf<String>()

        override suspend fun startSession(engineId: String, startedAt: Long): Long {
            engineIds += engineId
            return engineIds.size.toLong()
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
