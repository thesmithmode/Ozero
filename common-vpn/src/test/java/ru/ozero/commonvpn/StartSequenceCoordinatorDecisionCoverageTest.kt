package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartSequenceCoordinatorDecisionCoverageTest {

    @Test
    fun `resolve target prefers manual then priority then first plugin then null`() {
        val first = DecisionPlugin(EngineId.WARP, manualConfig = EngineConfig.WarpProxy(2080))
        val second = DecisionPlugin(EngineId.MASTERDNS, manualConfig = masterDnsConfig())
        val coordinator = coordinator(first, second)

        assertEquals(EngineId.BYEDPI, coordinator.resolveTarget(EngineId.BYEDPI, SettingsModel()))
        assertEquals(
            EngineId.MASTERDNS,
            coordinator.resolveTarget(
                null,
                SettingsModel(engineAutoPriority = listOf(EngineId.MASTERDNS, EngineId.WARP)),
            ),
        )
        assertEquals(EngineId.WARP, coordinator.resolveTarget(null, SettingsModel(engineAutoPriority = emptyList())))
        assertEquals(null, coordinator().resolveTarget(null, SettingsModel(engineAutoPriority = emptyList())))
    }

    @Test
    fun `engine allowed covers tun proxy missing false and true capability branches`() {
        val proxyOnly = DecisionPlugin(
            EngineId.WARP,
            manualConfig = EngineConfig.WarpProxy(2080),
            proxyConfig = EngineConfig.WarpProxy(2080),
            capabilities = proxyCapabilities(providesStandalone = true),
        )
        val notStandalone = DecisionPlugin(
            EngineId.BYEDPI,
            manualConfig = EngineConfig.ByeDpi(socksPort = 2081),
            proxyConfig = EngineConfig.ByeDpi(socksPort = 2081),
            capabilities = proxyCapabilities(providesStandalone = false),
        )
        val coordinator = coordinator(proxyOnly, notStandalone)

        assertTrue(coordinator.engineAllowed(EngineId.BYEDPI, TrafficMode.TUN))
        assertTrue(coordinator.engineAllowed(EngineId.WARP, TrafficMode.PROXY))
        assertFalse(coordinator.engineAllowed(EngineId.BYEDPI, TrafficMode.PROXY))
        assertFalse(coordinator.engineAllowed(EngineId.MASTERDNS, TrafficMode.PROXY))
    }

    @Test
    fun `auto candidates skip disallowed missing config and include valid proxy configs`() {
        val valid = DecisionPlugin(
            EngineId.WARP,
            manualConfig = EngineConfig.WarpProxy(2080),
            proxyConfig = EngineConfig.WarpProxy(2080),
            capabilities = proxyCapabilities(providesStandalone = true),
        )
        val noConfig = DecisionPlugin(
            EngineId.MASTERDNS,
            manualConfig = masterDnsConfig(),
            proxyConfig = null,
            capabilities = proxyCapabilities(providesStandalone = true),
        )
        val disallowed = DecisionPlugin(
            EngineId.BYEDPI,
            manualConfig = EngineConfig.ByeDpi(socksPort = 2081),
            proxyConfig = EngineConfig.ByeDpi(socksPort = 2081),
            capabilities = proxyCapabilities(providesStandalone = false),
        )
        val coordinator = coordinator(valid, noConfig, disallowed)

        val candidates = coordinator.autoCandidates(
            SettingsModel(
                trafficMode = TrafficMode.PROXY,
                engineAutoPriority = listOf(EngineId.BYEDPI, EngineId.MASTERDNS, EngineId.WARP),
            ),
            TrafficMode.PROXY,
        )

        assertEquals(listOf(EngineId.WARP), candidates.map { it.first })
    }

    @Test
    fun `auto candidates use default priority when settings are absent`() {
        val byedpi = DecisionPlugin(
            EngineId.BYEDPI,
            manualConfig = EngineConfig.ByeDpi(socksPort = 2081),
            capabilities = proxyCapabilities(providesStandalone = false),
        )
        val coordinator = coordinator(byedpi)

        val candidates = coordinator.autoCandidates(null, TrafficMode.TUN)

        assertEquals(listOf(EngineId.BYEDPI), candidates.map { it.first })
    }

    private fun coordinator(vararg plugins: EnginePlugin): StartSequenceCoordinator {
        val pluginSet = plugins.toSet()
        return StartSequenceCoordinator(
            packageName = "ru.ozero.test",
            deps = StartSequenceCollaborators(
                enginePlugins = pluginSet,
                chainOrchestrator = ChainOrchestrator(pluginSet),
                tunnelController = TunnelController(),
                tunnelGateway = mockk(relaxed = true),
                healthMonitor = mockk(relaxed = true),
                tunBuilderHelper = mockk(relaxed = true),
                engineWatchdog = mockk(relaxed = true),
                statsLogger = mockk(relaxed = true),
                splitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
                settingsRepository = StaticSettingsRepository(),
                sessionStatsRecorder = SessionStatsRecorder.NoOp,
            ),
            state = StartSequenceState(
                tunFdRef = AtomicReference<ParcelFileDescriptor?>(null),
                tunIfaceNameRef = AtomicReference<String?>(null),
                lockdownStartupFdRef = AtomicReference<ParcelFileDescriptor?>(null),
                sessionStartMsRef = AtomicReference(0L),
                sessionIdRef = AtomicReference(-1L),
                stopping = AtomicBoolean(false),
            ),
            killswitchSetter = {},
            stopVpnRequest = {},
        )
    }

    private fun StartSequenceCoordinator.resolveTarget(
        manualEngine: EngineId?,
        settings: SettingsModel?,
    ): EngineId? = callPrivate("resolveTargetForUi", manualEngine, settings)

    private fun StartSequenceCoordinator.engineAllowed(engineId: EngineId, trafficMode: TrafficMode): Boolean =
        callPrivate("engineAllowedForTrafficMode", engineId, trafficMode)

    private fun StartSequenceCoordinator.autoCandidates(
        settings: SettingsModel?,
        trafficMode: TrafficMode,
    ): List<Pair<EngineId, EngineConfig>> = callPrivate("autoCandidates", settings, trafficMode)

    @Suppress("UNCHECKED_CAST")
    private fun <T> StartSequenceCoordinator.callPrivate(name: String, vararg args: Any?): T {
        val method = StartSequenceCoordinator::class.java.declaredMethods.first { method ->
            method.name == name && method.parameterTypes.size == args.size
        }
        method.isAccessible = true
        return method.invoke(this, *args) as T
    }

    private class DecisionPlugin(
        override val id: EngineId,
        private val manualConfig: EngineConfig?,
        private val proxyConfig: EngineConfig? = manualConfig,
        override val capabilities: EngineCapabilities = proxyCapabilities(providesStandalone = false),
    ) : EnginePlugin {
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(2080)

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig? = manualConfig
        override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = proxyConfig
    }

    private class StaticSettingsRepository : SettingsRepository {
        override val settings: Flow<SettingsModel> = flowOf(SettingsModel())
        override suspend fun setSplitMode(mode: ru.ozero.enginescore.settings.SplitTunnelMode) = Unit
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
        override suspend fun setByedpiUiSettings(settings: ru.ozero.enginescore.settings.ByeDpiUiSettings) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: ru.ozero.enginescore.settings.AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
    }

    private companion object {
        fun proxyCapabilities(providesStandalone: Boolean): EngineCapabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
            providesLocalSocks = true,
            providesLocalSocksWithoutUpstream = providesStandalone,
        )

        fun masterDnsConfig(): EngineConfig = EngineConfig.MasterDns(
            configToml = "server_addr='127.0.0.1'",
            resolvers = emptyList(),
            socksPort = 2082,
        )
    }
}
