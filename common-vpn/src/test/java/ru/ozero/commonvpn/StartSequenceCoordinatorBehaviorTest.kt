package ru.ozero.commonvpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

@Suppress("LargeClass", "TooManyFunctions")
class StartSequenceCoordinatorBehaviorTest {

    @Test
    fun `manual proxy success starts engine, health monitor, stats logger and session`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            socksPort = 2080,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.SINGBOX,
                killswitchEnabled = true,
            ),
        )

        fixture.coordinator.run()

        assertEquals(listOf(false), fixture.killswitchValues)
        assertEquals(1, engine.startedConfigs.size)
        coVerify(exactly = 1) { fixture.healthMonitor.start(2080) }
        verify(exactly = 1) { fixture.statsLogger.start() }
        assertEquals(listOf("SINGBOX:PROXY"), fixture.sessionRecorder.startedEngines)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        assertEquals(2080, (fixture.tunnelController.state.value as TunnelState.Connected).socksPort)
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `manual proxy without standalone capability fails before starting chain`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            capabilities = standaloneProxyCapabilities(providesLocalSocksWithoutUpstream = false),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.BYEDPI),
        )

        fixture.coordinator.run()

        assertEquals(0, engine.startedConfigs.size)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.contains("no engine reachable") },
            )
        }
        assertIs<TunnelState.Failed>(fixture.tunnelController.state.value)
    }

    @Test
    fun `proxy awaitReady timeout stops chain and reports engine failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.MASTERDNS,
            socksPort = 2090,
            readyResult = EnginePlugin.ReadyResult.Timeout("probe timeout"),
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.MASTERDNS),
        )

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.MASTERDNS, "proxy awaitReady fail") }
        coVerify(exactly = 0) { fixture.healthMonitor.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `auto proxy retries next candidate without terminal failure for first runtime failure`() = runTest {
        val first = FakeEnginePlugin(
            id = EngineId.WARP,
            startResult = StartResult.Failure("first failed"),
            capabilities = standaloneProxyCapabilities(),
        )
        val second = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            socksPort = 2100,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            first,
            second,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.SINGBOX),
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, first.startedConfigs.size)
        assertEquals(1, second.startedConfigs.size)
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        assertEquals(2100, (fixture.tunnelController.state.value as TunnelState.Connected).socksPort)
    }

    @Test
    fun `auto preflight rejects failed candidate and starts accepted candidate only`() = runTest {
        val rejected = FakeEnginePlugin(
            id = EngineId.WARP,
            preflightResult = EnginePreflight.Result.Fail("offline"),
            capabilities = standaloneProxyCapabilities(),
        )
        val accepted = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            preflightResult = EnginePreflight.Result.Ok,
            socksPort = 2110,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            rejected,
            accepted,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.SINGBOX),
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, rejected.startedConfigs.size)
        assertEquals(1, accepted.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `stopping state exits before settings or engine side effects`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.SINGBOX, capabilities = standaloneProxyCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.SINGBOX),
            stopping = true,
        )

        fixture.coordinator.run()

        assertEquals(0, fixture.settingsRepository.reads)
        assertEquals(0, engine.startedConfigs.size)
        assertEquals(emptyList(), fixture.killswitchValues)
    }

    @Test
    fun `manual TUN success starts native tunnel, watchdogs, stats logger and session`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2091, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                customDnsServers = listOf("1.1.1.1"),
            ),
        )
        val tunFd = fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(listOf(false), fixture.killswitchValues)
        verify(exactly = 1) {
            fixture.tunnelGateway.start(
                match { it.tunPfd === tunFd && it.socksPort == 2091 && it.socksAddress == "127.0.0.1" },
            )
        }
        assertEquals(tunFd, fixture.state.tunFdRef.get())
        assertEquals(listOf("BYEDPI"), fixture.sessionRecorder.startedEngines)
        coVerify(exactly = 1) { fixture.healthMonitor.start(2091) }
        verify(exactly = 1) { fixture.engineWatchdog.startHealthKillswitchWatcher(EngineId.BYEDPI) }
        verify(exactly = 1) { fixture.engineWatchdog.startStagnationWatchdog(EngineId.BYEDPI) }
        verify(exactly = 1) { fixture.statsLogger.start() }
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `manual TUN establish null stops chain and requests service stop`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2092, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd(fd = null)

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertEquals(true, fixture.stopRequested.get())
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN gateway nonzero code stops chain and reports engine failure`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2093, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns -7

        fixture.coordinator.run()

        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.BYEDPI, "tunnel code=-7") }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN gateway throw stops chain and reports engine failure`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2094, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } throws IllegalStateException("native down")

        fixture.coordinator.run()

        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.contains("native down") },
            )
        }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `killswitch startup TUN is established before engine selection and closed after final TUN`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2095, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                killswitchEnabled = true,
            ),
        )
        val startupFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val finalFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 2095
        }
        val startupBuilder = mockk<VpnService.Builder> {
            every { establish() } returns startupFd
        }
        val finalBuilder = mockk<VpnService.Builder> {
            every { establish() } returns finalFd
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns startupBuilder
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns finalBuilder
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(listOf(true), fixture.killswitchValues)
        verify(exactly = 1) { startupFd.close() }
        assertEquals(finalFd, fixture.state.tunFdRef.get())
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === finalFd && it.socksPort == 2095 }) }
    }

    private fun startFixture(
        vararg engines: FakeEnginePlugin,
        settings: SettingsModel,
        stopping: Boolean = false,
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
        val settingsRepository = StaticSettingsRepository(settings)
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
            splitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
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

    private fun standaloneProxyCapabilities(
        providesLocalSocksWithoutUpstream: Boolean = true,
    ): EngineCapabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = true,
        requiresServer = false,
        supportsUpstreamSocks = false,
        providesLocalSocks = true,
        providesLocalSocksWithoutUpstream = providesLocalSocksWithoutUpstream,
    )

    private fun tunnelCapabilities(): EngineCapabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = true,
        requiresServer = false,
        supportsUpstreamSocks = false,
        providesLocalSocks = true,
        providesLocalSocksWithoutUpstream = false,
    )

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
    ) {
        fun establishedTunFd(fd: ParcelFileDescriptor? = testFd()) =
            fd.also { tunFd ->
                val builder = mockk<VpnService.Builder> {
                    every { establish() } returns tunFd
                }
                every { tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns builder
                every { tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns builder
            }

        private fun testFd(): ParcelFileDescriptor = mockk(relaxed = true) {
            every { fd } returns 42
        }
    }

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
        override fun stats(): Flow<EngineStats> = emptyFlow()
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

    private class StaticSettingsRepository(value: SettingsModel) : SettingsRepository {
        var reads = 0
        private val backing = MutableStateFlow(value)
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
