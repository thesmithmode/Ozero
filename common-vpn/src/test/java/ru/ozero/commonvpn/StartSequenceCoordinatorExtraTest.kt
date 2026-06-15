package ru.ozero.commonvpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
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
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
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
import kotlin.test.assertIs
import kotlin.test.assertFalse
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
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    @Test
    fun `auto candidate without preflight is still accepted`() = runTest {
        val first = FakeEnginePlugin(
            id = EngineId.WARP,
            preflightResult = null,
            socksPort = 2120,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            first,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP),
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, first.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `auto proxy retries next candidate without notifying first failure`() = runTest {
        val failing = FakeEnginePlugin(
            id = EngineId.WARP,
            startResult = StartResult.Failure("warp unavailable"),
            capabilities = standaloneProxyCapabilities(),
        )
        val succeeding = FakeEnginePlugin(
            id = EngineId.MASTERDNS,
            socksPort = 2130,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            failing,
            succeeding,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.MASTERDNS),
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, failing.startedConfigs.size)
        assertEquals(1, succeeding.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, any()) }
        verify(exactly = 1) { fixture.statsLogger.start() }
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `auto preflight skips failed candidate and starts accepted candidate`() = runTest {
        val rejected = FakeEnginePlugin(
            id = EngineId.WARP,
            preflightResult = EnginePreflight.Result.Fail("no route"),
            capabilities = standaloneProxyCapabilities(),
        )
        val accepted = FakeEnginePlugin(
            id = EngineId.MASTERDNS,
            preflightResult = EnginePreflight.Result.Ok,
            socksPort = 2131,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            rejected,
            accepted,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.MASTERDNS),
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, rejected.startedConfigs.size)
        assertEquals(1, accepted.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, any()) }
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `auto preflight exception skips candidate and starts next accepted candidate`() = runTest {
        val rejected = FakeEnginePlugin(
            id = EngineId.WARP,
            preflightThrows = true,
            capabilities = standaloneProxyCapabilities(),
        )
        val accepted = FakeEnginePlugin(
            id = EngineId.MASTERDNS,
            preflightResult = EnginePreflight.Result.Ok,
            socksPort = 2132,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            rejected,
            accepted,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.MASTERDNS),
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, rejected.startedConfigs.size)
        assertEquals(1, accepted.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        assertFalse(fixture.stopRequested.get())
    }

    @Test
    fun `manual proxy with zero socks port stops chain and reports endpoint failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.SINGBOX,
            socksPort = 0,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.SINGBOX,
            ),
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
    }

    @Test
    fun `manual proxy start failure reports runtime reason without health or stats`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.MASTERDNS,
            startResult = StartResult.Failure("native missing"),
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.MASTERDNS,
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.MASTERDNS, "native missing") }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual proxy awaitReady timeout stops chain and reports proxy failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.WARP,
            socksPort = 2124,
            capabilities = standaloneProxyCapabilities(),
            readyResult = EnginePlugin.ReadyResult.Timeout("not ready"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.WARP,
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, "proxy awaitReady fail") }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual proxy without matching plugin requests stop`() = runTest {
        val fixture = startFixture(
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.SINGBOX,
            ),
        )

        fixture.coordinator.run()

        assertEquals(true, fixture.stopRequested.get())
        val state = fixture.tunnelController.state.value
        assertTrue(
            state is TunnelState.Failed ||
                state is TunnelState.Idle,
        )
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
    }

    @Test
    fun `manual tun without matching plugin requests stop`() = runTest {
        val fixture = startFixture(
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.SINGBOX,
            ),
        )

        fixture.coordinator.run()

        assertEquals(true, fixture.stopRequested.get())
        val state = fixture.tunnelController.state.value
        assertTrue(
            state is TunnelState.Failed ||
                state is TunnelState.Idle,
        )
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
    }

    @Test
    fun `proxy auto mode with unsupported engine requests stop without start`() = runTest {
        val first = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            capabilities = standaloneProxyCapabilities(providesLocalSocksWithoutUpstream = false),
        )
        val fixture = startFixture(
            first,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = null,
                engineAutoPriority = listOf(EngineId.BYEDPI),
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, first.startedConfigs.size)
        assertEquals(true, fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match {
                    it.contains("no engine reachable")
                },
            )
        }
    }

    @Test
    fun `manual proxy disallowed by engine capability reports unreachable engine`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            capabilities = standaloneProxyCapabilities(providesLocalSocksWithoutUpstream = false),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.BYEDPI,
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, engine.startedConfigs.size)
        assertTrue(fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.contains("no engine reachable") },
            )
        }
    }

    @Test
    fun `stopping state returns before settings read and without side effects`() = runTest {
        val fixture = startFixture(
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.WARP,
            ),
            stopping = true,
        )

        fixture.coordinator.run()

        assertEquals(0, fixture.settingsRepository.reads)
        assertEquals(false, fixture.stopRequested.get())
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
        assertIs<TunnelState.Idle>(fixture.tunnelController.state.value)
    }

    @Test
    fun `empty auto priority in tun mode requests stop after settings read`() = runTest {
        val fixture = startFixture(
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = null,
                engineAutoPriority = emptyList(),
            ),
        )

        fixture.coordinator.run()

        assertEquals(1, fixture.settingsRepository.reads)
        assertEquals(true, fixture.stopRequested.get())
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
    }

    @Test
    fun `settings timeout falls back to default auto tun settings`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2133,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(),
            settingsFlow = emptyFlow(),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(listOf(false), fixture.killswitchValues)
        assertEquals(1, engine.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `tun killswitch startup establishes lockdown fd before engine selection`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2134,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                killswitchEnabled = true,
                ipv6Enabled = true,
                customDnsServers = listOf("1.1.1.1"),
            ),
        )
        val lockdownFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 52
        }
        val lockdownBuilder = mockk<VpnService.Builder> {
            every { establish() } returns lockdownFd
        }
        val regularBuilder = mockk<VpnService.Builder> {
            every { establish() } returns tunFd
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any(), any()) } returns lockdownBuilder
        every {
            fixture.tunBuilderHelper.buildTunBuilder(any(), true, listOf("1.1.1.1"))
        } returns regularBuilder
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(listOf(true), fixture.killswitchValues)
        assertEquals(lockdownFd, fixture.state.lockdownStartupFdRef.get())
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `tun killswitch startup ignores lockdown establish failure and still starts engine`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2135,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                killswitchEnabled = true,
            ),
        )
        val regularFd = fixture.establishedTunFd()
        val lockdownBuilder = mockk<VpnService.Builder> {
            every { establish() } throws IllegalStateException("lockdown busy")
        }
        val regularBuilder = mockk<VpnService.Builder> {
            every { establish() } returns regularFd
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), false, emptyList(), false) } returns lockdownBuilder
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), false, emptyList()) } returns regularBuilder
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
    }

    @Test
    fun `allowlist failure falls back to empty set and still starts engine`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2121,
            capabilities = tunnelCapabilities(),
        )
        val splitRules = FailingSplitTunnelRulesProvider()
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.ALLOWLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(1, splitRules.allowlistReads)
        assertEquals(0, splitRules.blocklistReads)
        assertEquals(1, engine.startedConfigs.size)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match { it.mode == SplitTunnelMode.ALLOWLIST && it.allowlist.isEmpty() && it.blocklist.isEmpty() },
                false,
                emptyList(),
            )
        }
    }

    @Test
    fun `manual tun native tunnel nonzero code stops chain and reports failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2123,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 9

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(EngineId.BYEDPI, "tunnel code=9")
        }
        verify(exactly = 0) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual tun establish null stops chain and reports vpn slot failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2125,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
        )
        fixture.establishedTunFd(fd = null)

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertTrue(fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                match { it.contains("establishTun fail") },
            )
        }
    }

    @Test
    fun `manual tun establish throw requests stop before native tunnel start`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2126,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
        )
        val builder = mockk<VpnService.Builder> {
            every { establish() } throws IllegalStateException("vpn busy")
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns builder
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(1, engine.stopCalls)
        assertTrue(fixture.stopRequested.get())
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
    }

    @Test
    fun `manual tun closes fd and stops chain when stopping flips after establish`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2136,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
        )
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { fd } returns 51
        }
        val builder = mockk<VpnService.Builder> {
            every { establish() } answers {
                fixture.state.stopping.set(true)
                tunFd
            }
        }
        every { fixture.tunBuilderHelper.buildTunBuilder(any(), any(), any()) } returns builder
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        verify(exactly = 1) { tunFd.close() }
        assertEquals(1, engine.stopCalls)
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
    }

    @Test
    fun `manual tun native tunnel exception stops chain and reports failure`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2137,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } throws IllegalStateException("hev down")

        fixture.coordinator.run()

        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.BYEDPI,
                "tunnel threw: hev down",
            )
        }
    }

    @Test
    fun `custom tun establish null with handled watchdog does not request service stop`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
            failureHandled = true,
        )
        val builder = mockTunBuilder(fd = null)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(0, engine.attachCalls)
        assertFalse(fixture.stopRequested.get())
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.WARP,
                match { it.contains("VPN slot") },
            )
        }
    }

    @Test
    fun `custom tun without tun spec returns without starting chain`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
            tunSpec = null,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )

        fixture.coordinator.run()

        assertEquals(0, engine.startedConfigs.size)
        assertEquals(0, engine.attachCalls)
        assertFalse(fixture.stopRequested.get())
        verify(exactly = 0) { fixture.engineWatchdog.handleEngineFailure(any(), any()) }
    }

    @Test
    fun `custom tun establish throw with unhandled watchdog requests service stop`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
            failureHandled = false,
        )
        val builder = mockk<VpnService.Builder> {
            every { establish() } throws IllegalStateException("slot busy")
        }
        every { builder.addRoute(any<String>(), any()) } returns builder
        every { builder.addDisallowedApplication(any()) } returns builder
        every { builder.addAllowedApplication(any()) } returns builder
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder

        fixture.coordinator.run()

        assertTrue(fixture.stopRequested.get())
        assertEquals(0, engine.startedConfigs.size)
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.WARP,
                match { it.contains("VPN slot") },
            )
        }
    }

    @Test
    fun `custom tun chain failure after establish does not attach tun`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
            startResult = StartResult.Failure("custom chain failed"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )
        val tun = mockTunFd(rawFd = 47, dupFd = 48)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns mockTunBuilder(fd = tun)

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(0, engine.attachCalls)
        verify(exactly = 1) { fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, "custom chain failed") }
    }

    @Test
    fun `custom tun attach throw closes duplicate and tun and reports failure`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
            attachThrows = true,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )
        val tun = mockTunFd(rawFd = 53, dupFd = 54)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns mockTunBuilder(fd = tun)

        fixture.coordinator.run()

        assertEquals(listOf(54), engine.attachedFds)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) { tun.close() }
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(
                EngineId.WARP,
                "attachTun threw: attach down",
            )
        }
    }

    @Test
    fun `custom tun attach failure closes tun stops chain and reports failure`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Failure("attach denied"),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )
        val tun = mockTunFd(rawFd = 43, dupFd = 44)
        val builder = mockTunBuilder(fd = tun)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(listOf(44), engine.attachedFds)
        assertEquals(1, engine.stopCalls)
        verify(exactly = 1) { tun.close() }
        verify(exactly = 1) {
            fixture.engineWatchdog.handleEngineFailure(EngineId.WARP, "attachTun: attach denied")
        }
    }

    @Test
    fun `custom tun success starts peer and stagnation watchdogs without native gateway`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )
        val tun = mockTunFd(rawFd = 45, dupFd = 46)
        val builder = mockTunBuilder(fd = tun)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns builder

        fixture.coordinator.run()

        assertEquals(listOf(46), engine.attachedFds)
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        verify(exactly = 0) { fixture.tunnelGateway.start(any()) }
        verify(exactly = 0) { fixture.engineWatchdog.startHealthKillswitchWatcher(any()) }
        verify(exactly = 1) { fixture.engineWatchdog.startPeerWatchdog(EngineId.WARP) }
        verify(exactly = 1) { fixture.engineWatchdog.startStagnationWatchdog(EngineId.WARP) }
    }

    @Test
    fun `custom tun success closes startup lockdown fd before attach`() = runTest {
        val engine = FakeTunEnginePlugin(
            id = EngineId.WARP,
            attachResult = TunAttachResult.Success,
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.WARP,
            ),
        )
        val lockdownFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val tun = mockTunFd(rawFd = 49, dupFd = 50)
        fixture.state.lockdownStartupFdRef.set(lockdownFd)
        every { fixture.tunBuilderHelper.applyEngineTunSpec(any(), any()) } returns mockTunBuilder(fd = tun)

        fixture.coordinator.run()

        verify(exactly = 1) { lockdownFd.close() }
        assertEquals(listOf(50), engine.attachedFds)
        assertEquals(null, fixture.state.lockdownStartupFdRef.get())
    }

    @Test
    fun `blocklist failure falls back to empty set and still starts engine`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2122,
            capabilities = tunnelCapabilities(),
        )
        val splitRules = FailingSplitTunnelRulesProvider()
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
                splitMode = SplitTunnelMode.BLOCKLIST,
            ),
            splitTunnelRulesProvider = splitRules,
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0

        fixture.coordinator.run()

        assertEquals(0, splitRules.allowlistReads)
        assertEquals(1, splitRules.blocklistReads)
        assertEquals(1, engine.startedConfigs.size)
        verify(exactly = 1) {
            fixture.tunBuilderHelper.buildTunBuilder(
                match { it.mode == SplitTunnelMode.BLOCKLIST && it.allowlist.isEmpty() && it.blocklist.isEmpty() },
                false,
                emptyList(),
            )
        }
    }

    @Test
    fun `manual tun success tolerates health monitor and session recorder failures`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.BYEDPI,
            socksPort = 2127,
            capabilities = tunnelCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.TUN,
                manualEngine = EngineId.BYEDPI,
            ),
            sessionStatsRecorder = FailingSessionStatsRecorder(),
        )
        fixture.establishedTunFd()
        every { fixture.tunnelGateway.start(any()) } returns 0
        coEvery { fixture.healthMonitor.start(any()) } throws IllegalStateException("health down")

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(-1L, fixture.state.sessionIdRef.get())
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        verify(exactly = 1) { fixture.engineWatchdog.startHealthKillswitchWatcher(EngineId.BYEDPI) }
        verify(exactly = 1) { fixture.engineWatchdog.startStagnationWatchdog(EngineId.BYEDPI) }
        verify(exactly = 1) { fixture.statsLogger.start() }
    }

    @Test
    fun `manual proxy success tolerates health monitor and session recorder failures`() = runTest {
        val engine = FakeEnginePlugin(
            id = EngineId.WARP,
            socksPort = 2128,
            capabilities = standaloneProxyCapabilities(),
        )
        val fixture = startFixture(
            engine,
            settings = SettingsModel(
                trafficMode = TrafficMode.PROXY,
                manualEngine = EngineId.WARP,
            ),
            sessionStatsRecorder = FailingSessionStatsRecorder(),
        )
        coEvery { fixture.healthMonitor.start(any()) } throws IllegalStateException("proxy health down")

        fixture.coordinator.run()

        assertEquals(1, engine.startedConfigs.size)
        assertEquals(-1L, fixture.state.sessionIdRef.get())
        assertIs<TunnelState.Connected>(fixture.tunnelController.state.value)
        verify(exactly = 1) { fixture.engineWatchdog.startHealthKillswitchWatcher(EngineId.WARP) }
        verify(exactly = 0) { fixture.engineWatchdog.startStagnationWatchdog(any()) }
        verify(exactly = 1) { fixture.statsLogger.start() }
    }

    private fun startFixture(
        vararg engines: EnginePlugin,
        settings: SettingsModel,
        settingsFlow: Flow<SettingsModel>? = null,
        stopping: Boolean = false,
        splitTunnelRulesProvider: SplitTunnelRulesProvider = SplitTunnelRulesProvider.NoOp,
        sessionStatsRecorder: SessionStatsRecorder = RecordingSessionStatsRecorder(),
        failureHandled: Boolean = false,
    ): StartFixture {
        val tunnelController = TunnelController()
        val healthMonitor = mockk<HealthMonitor>(relaxed = true)
        val statsLogger = mockk<TunnelStatsLogger>(relaxed = true)
        val engineWatchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        val tunnelGateway = mockk<HevTunnelGateway>(relaxed = true)
        val tunBuilderHelper = mockk<TunBuilderHelper>(relaxed = true)
        val stopRequested = AtomicBoolean(false)
        val killswitchValues = mutableListOf<Boolean>()
        val settingsRepository = StaticSettingsRepository(settingsFlow ?: flowOf(settings))
        coEvery { healthMonitor.start(any()) } returns Unit
        every { statsLogger.start() } returns Unit
        every { engineWatchdog.startHealthKillswitchWatcher(any()) } returns Unit
        every { engineWatchdog.startStagnationWatchdog(any()) } returns Unit
        every { engineWatchdog.startPeerWatchdog(any()) } returns Unit
        every { engineWatchdog.handleEngineFailure(any(), any()) } answers {
            if (!failureHandled) {
                tunnelController.onEngineDied(firstArg(), secondArg())
                stopRequested.set(true)
            }
            failureHandled
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
            sessionStatsRecorder = sessionStatsRecorder,
        )
        val state = StartSequenceState(
            tunFdRef = AtomicReference<android.os.ParcelFileDescriptor?>(null),
            tunIfaceNameRef = AtomicReference<String?>(null),
            lockdownStartupFdRef = AtomicReference<android.os.ParcelFileDescriptor?>(null),
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
            killswitchValues = killswitchValues,
            settingsRepository = settingsRepository,
            stopRequested = stopRequested,
            tunnelController = tunnelController,
            tunnelGateway = tunnelGateway,
            tunBuilderHelper = tunBuilderHelper,
            engineWatchdog = engineWatchdog,
            statsLogger = statsLogger,
            healthMonitor = healthMonitor,
        )
    }

    private data class StartFixture(
        val coordinator: StartSequenceCoordinator,
        val state: StartSequenceState,
        val killswitchValues: List<Boolean>,
        val tunnelController: TunnelController,
        val tunnelGateway: HevTunnelGateway,
        val tunBuilderHelper: TunBuilderHelper,
        val engineWatchdog: EngineWatchdogCoordinator,
        val statsLogger: TunnelStatsLogger,
        val healthMonitor: HealthMonitor,
        val settingsRepository: StaticSettingsRepository,
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

    private class FailingSplitTunnelRulesProvider : SplitTunnelRulesProvider {
        var allowlistReads = 0
            private set
        var blocklistReads = 0
            private set

        override suspend fun allowlistPackages(): Set<String> {
            allowlistReads++
            error("allowlist unavailable")
        }

        override suspend fun blocklistPackages(): Set<String> {
            blocklistReads++
            error("blocklist unavailable")
        }
    }

    private class FakeEnginePlugin(
        override val id: EngineId,
        private val socksPort: Int = 2080,
        override val capabilities: EngineCapabilities,
        private val startResult: StartResult = StartResult.Success(socksPort),
        private val readyResult: EnginePlugin.ReadyResult = EnginePlugin.ReadyResult.Ready,
        private val preflightResult: EnginePreflight.Result? = null,
        private val preflightThrows: Boolean = false,
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
            EnginePreflight { _ ->
                if (preflightThrows) error("preflight down")
                result
            }
        } ?: if (preflightThrows) {
            EnginePreflight { _ -> error("preflight down") }
        } else {
            null
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

    private class FakeTunEnginePlugin(
        override val id: EngineId,
        private val attachResult: TunAttachResult,
        private val startResult: StartResult = StartResult.Success(0),
        private val tunSpec: TunSpec? = TunSpec(
            sessionName = "warp",
            mtu = 1280,
            blocking = true,
            ipv4Address = "10.0.0.2",
            ipv4PrefixLength = 32,
            dnsServers = listOf("1.1.1.1"),
        ),
        private val attachThrows: Boolean = false,
    ) : EnginePlugin, TunFdAcceptor {
        override val capabilities = EngineCapabilities(
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
        val attachedFds = mutableListOf<Int>()
        var stopCalls = 0
        val attachCalls: Int
            get() = attachedFds.size

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startedConfigs += config
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Success(1L)
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats(activeConnections = 0))
        override fun buildManualConfig(settings: SettingsModel?): EngineConfig =
            EngineConfig.WarpProxy(socksPort = 0)

        override suspend fun tunSpec(): TunSpec? = tunSpec

        override suspend fun attachTun(tunFd: Int): TunAttachResult {
            attachedFds += tunFd
            if (attachThrows) error("attach down")
            return attachResult
        }
    }

    private fun mockTunFd(rawFd: Int, dupFd: Int): ParcelFileDescriptor {
        val duplicate = mockk<ParcelFileDescriptor>(relaxed = true) {
            every { detachFd() } returns dupFd
        }
        return mockk(relaxed = true) {
            every { fd } returns rawFd
            every { dup() } returns duplicate
        }
    }

    private fun mockTunBuilder(fd: ParcelFileDescriptor?): VpnService.Builder {
        val builder = mockk<VpnService.Builder>(relaxed = true)
        every { builder.addRoute(any<String>(), any()) } returns builder
        every { builder.addDisallowedApplication(any()) } returns builder
        every { builder.addAllowedApplication(any()) } returns builder
        every { builder.establish() } returns fd
        return builder
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

    private class FailingSessionStatsRecorder : SessionStatsRecorder {
        override suspend fun startSession(engineId: String, startedAt: Long): Long =
            error("session store down")

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
