package ru.ozero.commonvpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.TunSpec
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
import kotlin.test.assertTrue

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
    fun `auto preflight rejects all candidates and requests service stop`() = runTest {
        val first = FakeEnginePlugin(
            id = EngineId.WARP,
            preflightResult = EnginePreflight.Result.Fail("offline"),
            capabilities = standaloneProxyCapabilities(),
        )
        val second = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            preflightResult = EnginePreflight.Result.Fail("blocked"),
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

        assertEquals(0, first.startedConfigs.size)
        assertEquals(0, second.startedConfigs.size)
        assertEquals(true, fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.WARP,
                match { it.contains("no engine reachable") },
            )
        }
    }

    @Test
    fun `killswitch no reachable engine keeps startup lockdown active`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            preflightResult = EnginePreflight.Result.Fail("blocked"),
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.BYEDPI),
                killswitchEnabled = true,
            ),
        )
        val startupFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val startupBuilder = mockk<VpnService.Builder> {
            every { establish() } returns startupFd
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns startupBuilder
        every { fixture.engineWatchdog.handleEngineFailure(any(), any()) } answers {
            fixture.tunnelController.onKillswitchEngaged(firstArg(), secondArg())
            true
        }

        fixture.coordinator.run()

        assertTrue(fixture.tunnelController.killswitchActive.value)
        assertFalse(fixture.stopRequested.get())
        assertEquals(startupFd, fixture.state.lockdownStartupFdRef.get())
        verify(exactly = 0) { startupFd.close() }
    }

    @Test
    fun `custom tun establish throw keeps startup lockdown and avoids stopVpnRequest`() = runTest {
        val engine = object : EnginePlugin, TunFdAcceptor {
            override val id = EngineId.WARP
            override val capabilities = tunnelCapabilities()
            override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
                StartResult.Success(10000)
            override suspend fun stop() = Unit
            override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
            override fun stats(): Flow<EngineStats> = emptyFlow()
            override suspend fun tunSpec(): TunSpec? = TunSpec(
                sessionName = "test",
                mtu = 1400,
                blocking = true,
                ipv4Address = "10.0.0.2",
                ipv4PrefixLength = 32,
                dnsServers = listOf("1.1.1.1"),
            )
            override fun buildManualConfig(settings: SettingsModel?): EngineConfig {
                return EngineConfig.WarpProxy(socksPort = 10000)
            }
            override suspend fun attachTun(tunFd: Int): TunAttachResult = TunAttachResult.Success
        }

        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
                killswitchEnabled = true,
            ),
        )
        val startupFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val startupBuilder = mockk<VpnService.Builder> {
            every { establish() } returns startupFd
        }
        val engineBuilder = mockk<VpnService.Builder>(relaxed = true) {
            every { establish() } throws IllegalStateException("slot busy")
        }

        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns startupBuilder
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns engineBuilder
        every { fixture.engineWatchdog.handleEngineFailure(any(), any()) } answers {
            fixture.tunnelController.onKillswitchEngaged(firstArg(), secondArg())
            true
        }

        fixture.coordinator.run()

        assertTrue(fixture.tunnelController.killswitchActive.value)
        assertFalse(fixture.stopRequested.get())
        assertEquals(startupFd, fixture.state.lockdownStartupFdRef.get())
        assertFalse(fixture.stopRequested.get())
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.WARP,
                match { it.startsWith("VPN slot") },
            )
        }
        verify(exactly = 0) { startupFd.close() }
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
    fun `settings read timeout falls back to defaults and still starts the default candidate`() = runTest {
        val defaultEngine = SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY.first()
        val engine = FakeEnginePlugin(id = defaultEngine, socksPort = 2098, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = defaultEngine),
            settingsFlow = emptyFlow(),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        val job = backgroundScope.launch { fixture.coordinator.run() }
        advanceTimeBy(StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS)
        advanceUntilIdle()
        job.join()

        assertEquals(1, fixture.settingsRepository.reads)
        assertEquals(1, engine.startedConfigs.size)
        assertEquals(listOf(false), fixture.killswitchValues)
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `allowlist split mode reads allowlist packages and passes them to the TUN builder`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2099, capabilities = tunnelCapabilities())
        val splitRules = StaticSplitTunnelRulesProvider(
            allowlist = setOf("com.example.browser", "com.example.app"),
            blocklist = setOf("com.example.blocked"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.ALLOWLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        val tunFd = fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(1, splitRules.allowlistReads)
        assertEquals(0, splitRules.blocklistReads)
        assertEquals(1, fixture.settingsRepository.reads)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match {
                    it.mode == SplitTunnelMode.ALLOWLIST &&
                        it.allowlist == setOf("com.example.browser", "com.example.app") &&
                        it.blocklist.isEmpty()
                },
                false,
                emptyList(),
            )
        }
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === tunFd && it.socksPort == 2099 }) }
        assertEquals(1, engine.startedConfigs.size)
    }

    @Test
    fun `allowlist split mode timeout falls back to empty allowlist`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2100, capabilities = tunnelCapabilities())
        val splitRules = DelayedSplitTunnelRulesProvider(
            allowlist = setOf("com.example.browser"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.ALLOWLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        val tunFd = fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        val job = backgroundScope.launch { fixture.coordinator.run() }
        advanceTimeBy(StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS)
        advanceUntilIdle()
        job.join()

        assertEquals(1, splitRules.allowlistReads)
        assertEquals(0, splitRules.blocklistReads)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match {
                    it.mode == SplitTunnelMode.ALLOWLIST &&
                        it.allowlist.isEmpty() &&
                        it.blocklist.isEmpty()
                },
                false,
                emptyList(),
            )
        }
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === tunFd && it.socksPort == 2100 }) }
        assertEquals(1, engine.startedConfigs.size)
    }

    @Test
    fun `blocklist split mode reads blocklist packages and passes them to the TUN builder`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2101, capabilities = tunnelCapabilities())
        val splitRules = StaticSplitTunnelRulesProvider(
            allowlist = setOf("com.example.allowed"),
            blocklist = setOf("com.example.blocked", "com.example.blocked2"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.BLOCKLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        val tunFd = fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(0, splitRules.allowlistReads)
        assertEquals(1, splitRules.blocklistReads)
        assertEquals(1, fixture.settingsRepository.reads)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match {
                    it.mode == SplitTunnelMode.BLOCKLIST &&
                        it.allowlist.isEmpty() &&
                        it.blocklist == setOf("com.example.blocked", "com.example.blocked2")
                },
                false,
                emptyList(),
            )
        }
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === tunFd && it.socksPort == 2101 }) }
        assertEquals(1, engine.startedConfigs.size)
    }

    @Test
    fun `blocklist split mode timeout falls back to empty blocklist`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2102, capabilities = tunnelCapabilities())
        val splitRules = DelayedSplitTunnelRulesProvider(
            blocklist = setOf("com.example.blocked"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.BLOCKLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        val tunFd = fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        val job = backgroundScope.launch { fixture.coordinator.run() }
        advanceTimeBy(StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS)
        advanceUntilIdle()
        job.join()

        assertEquals(0, splitRules.allowlistReads)
        assertEquals(1, splitRules.blocklistReads)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match {
                    it.mode == SplitTunnelMode.BLOCKLIST &&
                        it.allowlist.isEmpty() &&
                        it.blocklist.isEmpty()
                },
                false,
                emptyList(),
            )
        }
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === tunFd && it.socksPort == 2102 }) }
        assertEquals(1, engine.startedConfigs.size)
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
    fun `killswitch startup TUN is established before engine selection and retained with final TUN`() = runTest {
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
        var buildCalls = 0
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } answers {
            if (buildCalls++ == 0) startupBuilder else finalBuilder
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } answers {
            if (buildCalls++ == 0) startupBuilder else finalBuilder
        }
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(listOf(true), fixture.killswitchValues)
        verify(exactly = 1) { startupFd.close() }
        assertEquals(null, fixture.state.lockdownStartupFdRef.get())
        assertEquals(finalFd, fixture.state.tunFdRef.get())
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === finalFd && it.socksPort == 2095 }) }
    }

    @Test
    fun `custom TUN attach failure closes descriptors stops chain and reports engine failure`() = runTest {
        val engine = object : EnginePlugin, TunFdAcceptor {
            override val id = EngineId.WARP
            override val capabilities = tunnelCapabilities()
            override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
                StartResult.Success(10000)
            override suspend fun stop() = Unit
            override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
            override fun stats(): Flow<EngineStats> = emptyFlow()
            override suspend fun tunSpec(): TunSpec? = TunSpec(
                sessionName = "test",
                mtu = 1400,
                blocking = true,
                ipv4Address = "10.0.0.2",
                ipv4PrefixLength = 32,
                dnsServers = listOf("1.1.1.1"),
            )
            override fun buildManualConfig(settings: SettingsModel?): EngineConfig =
                EngineConfig.WarpProxy(10000)
            override suspend fun attachTun(tunFd: Int): TunAttachResult =
                TunAttachResult.Failure("bad fd")
        }
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.WARP),
        )
        val dupFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { detachFd() } returns 123
        }
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 100
            every { dup() } returns dupFd
        }
        val builder = mockk<VpnService.Builder>(relaxed = true) {
            every { establish() } returns tunFd
        }
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(null, fixture.state.tunFdRef.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, "attachTun: bad fd")
        }
        verify(exactly = 1) { tunFd.close() }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `auto TUN retries next candidate after native tunnel candidate failure`() = runTest {
        val first = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2201, capabilities = tunnelCapabilities())
        val second = FakeEnginePlugin(id = EngineId.WARP, socksPort = 2202, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            first,
            second,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.BYEDPI, EngineId.WARP),
            ),
        )
        val firstFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 2201
        }
        val secondFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 2202
        }
        val firstBuilder = mockk<VpnService.Builder> {
            every { establish() } returns firstFd
        }
        val secondBuilder = mockk<VpnService.Builder> {
            every { establish() } returns secondFd
        }
        var buildCalls = 0
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } answers {
            if (buildCalls++ == 0) firstBuilder else secondBuilder
        }
        every { fixture.tunnelGateway.start(any()) } returnsMany listOf(-1, 0)

        fixture.coordinator.run()

        assertEquals(1, first.startedConfigs.size)
        assertEquals(1, first.stopCalls)
        assertEquals(1, second.startedConfigs.size)
        verify(exactly = 1) { firstFd.close() }
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(EngineId.BYEDPI, any()) }
        verify(exactly = 1) { fixture.tunnelGateway.start(match { it.tunPfd === secondFd && it.socksPort == 2202 }) }
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `manual TUN chain failure reports engine failure before establishing TUN`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            startResult = StartResult.Failure("manual failed"),
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.BYEDPI, "manual failed") }
        verify(exactly = 0) { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual proxy success with zero port stops chain and reports local proxy failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            socksPort = 0,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.SINGBOX),
        )

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.SINGBOX,
                "engine does not expose local proxy endpoint",
            )
        }
        coVerify(exactly = 0) { fixture.healthMonitor.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN awaitReady timeout stops chain after native tunnel start`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2096,
            readyResult = EnginePlugin.ReadyResult.Timeout("not ready"),
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.startsWith("awaitReady fail") },
            )
        }
        coVerify(exactly = 0) { fixture.healthMonitor.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN stopping after establish closes TUN and stops chain`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2097, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 2097
        }
        val builder = mockk<VpnService.Builder> {
            every { establish() } answers {
                fixture.state.stopping.set(true)
                tunFd
            }
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns builder
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertEquals(null, fixture.state.tunFdRef.get())
        verify(exactly = 1) { tunFd.close() }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN establish null stops started chain and requests stop`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2098, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd(null)

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertTrue(fixture.stopRequested.get())
        assertEquals(null, fixture.state.tunFdRef.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.startsWith("establishTun fail") },
            )
        }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN establish throw stops started chain and reports failure`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2099, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        val builder = mockk<VpnService.Builder> {
            every { establish() } throws IllegalStateException("vpn busy")
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns builder
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertTrue(fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.startsWith("establishTun fail") },
            )
        }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual TUN native tunnel throw stops chain and reports failure`() = runTest {
        val engine = FakeEnginePlugin(id = EngineId.BYEDPI, socksPort = 2100, capabilities = tunnelCapabilities())
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } throws IllegalStateException("hev boom")

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                "tunnel threw: hev boom",
            )
        }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual proxy health monitor throw still completes startup`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            socksPort = 2101,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.PROXY, manualEngine = EngineId.SINGBOX),
        )
        coEvery { fixture.healthMonitor.start(2101) } throws IllegalStateException("health down")

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        val connected = assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        assertEquals(EngineId.SINGBOX, connected.engineId)
        verify(exactly = 1) { fixture.statsLogger.start() }
        verify(exactly = 1) { fixture.engineWatchdog.startHealthKillswitchWatcher(EngineId.SINGBOX) }
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
    }

    @Test
    fun `custom TUN attach throw closes TUN and stops chain`() = runTest {
        val engine = ThrowingTunEngine(id = EngineId.BYEDPI, socksPort = 2102)
        val fixture = startFixture(
            engine,
            settings = SettingsModel(trafficMode = TrafficMode.TUN, manualEngine = EngineId.BYEDPI),
        )
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 2102
        }
        val dupFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { detachFd() } returns 2103
        }
        every { tunFd.dup() } returns dupFd
        val builder = mockk<VpnService.Builder>(relaxed = true) {
            every { establish() } returns tunFd
        }
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder
        fixture.establishedTunFd(tunFd)

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertEquals(null, fixture.state.tunFdRef.get())
        verify(exactly = 1) { tunFd.close() }
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                "attachTun threw: aidl dead",
            )
        }
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    private fun startFixture(
        vararg engines: EnginePlugin,
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
            false
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

    private class ThrowingTunEngine(
        override val id: EngineId,
        private val socksPort: Int,
    ) : EnginePlugin, TunFdAcceptor {
        override val capabilities: EngineCapabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = true,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
            providesLocalSocks = true,
            providesLocalSocksWithoutUpstream = false,
        )
        val startedConfigs = mutableListOf<EngineConfig>()
        var stopCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startedConfigs += config
            return StartResult.Success(socksPort)
        }

        override suspend fun stop() {
            stopCalls++
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = emptyFlow()
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig =
            EngineConfig.ByeDpi(socksPort = socksPort)
        override fun buildProxyConfig(settings: SettingsModel?): EngineConfig =
            EngineConfig.ByeDpi(socksPort = socksPort)
        override suspend fun tunSpec(): TunSpec = TunSpec(
            sessionName = "ThrowingTunEngine",
            mtu = 1500,
            blocking = true,
            ipv4Address = "10.0.0.2",
            ipv4PrefixLength = 32,
            dnsServers = listOf("1.1.1.1"),
        )
        override suspend fun attachTun(fd: Int): TunAttachResult = throw IllegalStateException("aidl dead")
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

    private class StaticSplitTunnelRulesProvider(
        private val allowlist: Set<String>,
        private val blocklist: Set<String>,
    ) : SplitTunnelRulesProvider {
        var allowlistReads = 0
            private set
        var blocklistReads = 0
            private set

        override suspend fun allowlistPackages(): Set<String> {
            allowlistReads++
            return allowlist
        }

        override suspend fun blocklistPackages(): Set<String> {
            blocklistReads++
            return blocklist
        }
    }

    private class DelayedSplitTunnelRulesProvider(
        private val allowlist: Set<String> = emptySet(),
        private val blocklist: Set<String> = emptySet(),
    ) : SplitTunnelRulesProvider {
        var allowlistReads = 0
            private set
        var blocklistReads = 0
            private set

        override suspend fun allowlistPackages(): Set<String> {
            allowlistReads++
            delay(StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS + 1)
            return allowlist
        }

        override suspend fun blocklistPackages(): Set<String> {
            blocklistReads++
            delay(StartSequenceCoordinator.SETTINGS_READ_TIMEOUT_MS + 1)
            return blocklist
        }
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
