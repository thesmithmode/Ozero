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
import ru.ozero.enginescore.EnginePreflight
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
    fun `auto candidates use effective settings default priority`() {
        val byedpi = DecisionPlugin(
            EngineId.BYEDPI,
            manualConfig = EngineConfig.ByeDpi(socksPort = 2081),
            capabilities = proxyCapabilities(providesStandalone = false),
        )
        val coordinator = coordinator(byedpi)

        val candidates = coordinator.autoCandidates(SettingsModel(), TrafficMode.TUN)

        assertEquals(listOf(EngineId.BYEDPI), candidates.map { it.first })
    }

    @Test
    fun `run in auto proxy mode skips failed preflight candidate and starts next accepted engine`() =
        kotlinx.coroutines.test.runTest {
            val failed = DecisionPlugin(
                EngineId.WARP,
                manualConfig = EngineConfig.WarpProxy(2080),
                proxyConfig = EngineConfig.WarpProxy(2080),
                capabilities = proxyCapabilities(providesStandalone = true),
                preflightResult = EnginePreflight.Result.Fail("offline"),
            )
            val accepted = DecisionPlugin(
                EngineId.BYEDPI,
                manualConfig = EngineConfig.ByeDpi(socksPort = 2081),
                proxyConfig = EngineConfig.ByeDpi(socksPort = 2081),
                capabilities = proxyCapabilities(providesStandalone = true),
                preflightResult = EnginePreflight.Result.Ok,
            )
            val coordinator = coordinator(
                failed,
                accepted,
                settings = SettingsModel(
                    trafficMode = TrafficMode.PROXY,
                    manualEngine = null,
                    engineAutoPriority = listOf(EngineId.WARP, EngineId.BYEDPI),
                ),
            )

            coordinator.run()

            assertEquals(0, failed.startCalls)
            assertEquals(1, accepted.startCalls)
        }

    @Test
    fun `build config returns null for missing plugin and selects proxy config only in proxy mode`() {
        val plugin = DecisionPlugin(
            EngineId.WARP,
            manualConfig = EngineConfig.WarpProxy(2080),
            proxyConfig = EngineConfig.WarpProxy(2081),
            capabilities = proxyCapabilities(providesStandalone = true),
        )
        val coordinator = coordinator(plugin)

        assertEquals(EngineConfig.WarpProxy(2080), coordinator.buildConfig(EngineId.WARP, TrafficMode.TUN))
        assertEquals(EngineConfig.WarpProxy(2081), coordinator.buildConfig(EngineId.WARP, TrafficMode.PROXY))
        assertEquals(null, coordinator.buildConfig(EngineId.BYEDPI, TrafficMode.TUN))
    }

    @Test
    fun `resolve target falls back to first plugin only when priority has no registered plugin`() {
        val first = DecisionPlugin(
            EngineId.SINGBOX,
            manualConfig = EngineConfig.Singbox(beanBlob = byteArrayOf(1), protocolType = 1, proxyMode = true),
        )
        val coordinator = coordinator(first)

        assertEquals(EngineId.SINGBOX, coordinator.resolveTarget(null, SettingsModel()))
        assertEquals(null, coordinator().resolveTarget(null, SettingsModel()))
    }

    @Test
    fun `engine custom tun detection covers missing regular and tun acceptor plugins`() =
        kotlinx.coroutines.test.runTest {
            val regular = DecisionPlugin(EngineId.BYEDPI, manualConfig = EngineConfig.ByeDpi(socksPort = 2082))
            val tun = DecisionTunPlugin(EngineId.WARP)
            val coordinator = coordinator(regular, tun)

            assertFalse(coordinator.engineNeedsCustomTun(EngineId.BYEDPI))
            assertTrue(coordinator.engineNeedsCustomTun(EngineId.WARP))
            assertFalse(coordinator.engineNeedsCustomTun(EngineId.MASTERDNS))
        }

    @Test
    fun `reportEngineFailure can suppress watchdog notification`() {
        val watchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        val coordinator = coordinator(watchdog = watchdog)

        coordinator.reportEngineFailure(EngineId.BYEDPI, "candidate failed", notifyFailure = false)
        coordinator.reportEngineFailure(EngineId.BYEDPI, "terminal failed", notifyFailure = true)

        io.mockk.verify(exactly = 1) { watchdog.handleEngineFailure(EngineId.BYEDPI, "terminal failed") }
    }

    private fun coordinator(
        vararg plugins: EnginePlugin,
        watchdog: EngineWatchdogCoordinator = mockk(relaxed = true),
        settings: SettingsModel = SettingsModel(),
    ): StartSequenceCoordinator {
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
                engineWatchdog = watchdog,
                statsLogger = mockk(relaxed = true),
                splitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
                settingsRepository = StaticSettingsRepository(settings),
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
        settings: SettingsModel,
    ): EngineId? = callPrivate("resolveTargetForUi", manualEngine, settings)

    private fun StartSequenceCoordinator.engineAllowed(engineId: EngineId, trafficMode: TrafficMode): Boolean =
        callPrivate("engineAllowedForTrafficMode", engineId, trafficMode)

    private fun StartSequenceCoordinator.autoCandidates(
        settings: SettingsModel,
        trafficMode: TrafficMode,
    ): List<Pair<EngineId, EngineConfig>> = callPrivate("autoCandidates", settings, trafficMode)

    private fun StartSequenceCoordinator.buildConfig(
        engineId: EngineId,
        trafficMode: TrafficMode,
    ): EngineConfig? = callPrivate("buildEngineConfig", engineId, SettingsModel(), trafficMode)

    private fun StartSequenceCoordinator.reportEngineFailure(
        engineId: EngineId,
        reason: String,
        notifyFailure: Boolean,
    ) {
        callPrivate<Unit>("reportEngineFailure", engineId, reason, notifyFailure)
    }

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
        private val preflightResult: EnginePreflight.Result? = null,
        private val preflightThrows: Boolean = false,
    ) : EnginePlugin {
        var startCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(2080).also { startCalls += 1 }

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig? = manualConfig
        override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = proxyConfig
        override fun preflight(): EnginePreflight? {
            if (preflightResult == null && !preflightThrows) return null
            return EnginePreflight { _ ->
                if (preflightThrows) {
                    error("preflight failed")
                }
                preflightResult ?: EnginePreflight.Result.Ok
            }
        }
    }

    private class DecisionTunPlugin(
        override val id: EngineId,
    ) : EnginePlugin, ru.ozero.enginescore.TunFdAcceptor {
        override val capabilities: EngineCapabilities = proxyCapabilities(providesStandalone = false)
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(0)

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.WarpProxy(0)
        override suspend fun tunSpec(): ru.ozero.enginescore.TunSpec? = null
        override suspend fun attachTun(tunFd: Int): ru.ozero.enginescore.TunAttachResult =
            ru.ozero.enginescore.TunAttachResult.Success
    }

    private class StaticSettingsRepository(settings: SettingsModel = SettingsModel()) : SettingsRepository {
        override val settings: Flow<SettingsModel> = flowOf(settings)
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
