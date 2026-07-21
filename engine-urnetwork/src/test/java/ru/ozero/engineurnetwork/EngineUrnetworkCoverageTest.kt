package ru.ozero.engineurnetwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineUrnetworkCoverageTest {

    @Test
    fun `start rejects wrong config and upstream`() = runTest {
        val fixture = fixture()

        assertThrows<IllegalArgumentException> {
            fixture.engine.start(EngineConfig.ByeDpi(socksPort = 1), Upstream.None)
        }
        assertThrows<IllegalArgumentException> {
            fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.Socks5("127.0.0.1", 1080))
        }
        fixture.close()
    }

    @Test
    fun `start returns failure when bootstrapper fails`() = runTest {
        val fixture = fixture(bootstrap = UrnetworkJwtBootstrapper.Result.Failed("bootstrap failed"))

        val result = fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertEquals(StartResult.Failure("bootstrap failed"), result)
        assertEquals(0, fixture.bridge.startCalls)
        fixture.close()
    }

    @Test
    fun `start returns failure when client jwt still missing after bootstrap`() = runTest {
        val fixture = fixture(store = InMemoryUrnetworkConfigStore())

        val result = fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertIs<StartResult.Failure>(result)
        assertTrue(result.reason.contains("client jwt missing"))
        assertEquals(0, fixture.bridge.startCalls)
        fixture.close()
    }

    @Test
    fun `start failure from bridge is returned without stats polling`() = runTest {
        val fixture = fixture(bridge = RecordingBridge(startResult = UrnetworkSdkBridge.StartResult.Failed("sdk down")))

        val result = fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)
        runCurrent()

        assertEquals(StartResult.Failure("sdk down"), result)
        assertTrue(fixture.engine.statsLabel(EngineStats()) == null)
        fixture.close()
    }

    @Test
    fun `successful start applies selected location performance and provide settings`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "client",
                selectedLocation = UrnetworkLocationSelection(" de ", " Bavaria ", " Munich "),
                windowType = UrnetworkWindowType.SPEED,
                fixedIpSize = true,
                allowDirect = false,
                provideEnabled = false,
                provideControlMode = UrnetworkProvideControlMode.AUTO,
                provideNetworkMode = UrnetworkProvideNetworkMode.ALL,
            ),
        )
        val bridge = RecordingBridge()
        val fixture = fixture(store = store, bridge = bridge)

        val result = fixture.engine.start(EngineConfig.Urnetwork(jwtToken = "", region = "US"), Upstream.None)

        assertEquals(StartResult.Success(0), result)
        assertEquals("client", bridge.lastClientJwt)
        assertEquals(UrnetworkLocationSelection("DE", "Bavaria", "Munich"), bridge.recordedPreferredLocation)
        assertEquals(UrnetworkWindowType.SPEED, bridge.windowType)
        assertEquals(true, bridge.fixedIpSize)
        assertEquals(false, bridge.allowDirect)
        assertEquals(false, bridge.providePaused)
        assertEquals(UrnetworkProvideControlMode.ALWAYS, bridge.controlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, bridge.networkMode)
        fixture.close()
    }

    @Test
    fun `successful start tolerates optional bridge configuration failures`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byClientJwt = "client",
                selectedLocation = UrnetworkLocationSelection(" ", null, null),
                provideEnabled = true,
            ),
        )
        val bridge = RecordingBridge(throwOptionalConfiguration = true)
        val fixture = fixture(store = store, bridge = bridge)

        val result = fixture.engine.start(EngineConfig.Urnetwork(jwtToken = "", region = "BR"), Upstream.None)

        assertEquals(StartResult.Success(0), result)
        assertEquals(1, bridge.startCalls)
        assertNull(bridge.recordedPreferredLocation)
        fixture.close()
    }

    @Test
    fun `second successful start cancels previous stats polling job`() = runTest {
        val bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot(peers = 1))
        val fixture = fixture(bridge = bridge)

        assertEquals(StartResult.Success(0), fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None))
        assertEquals(StartResult.Success(0), fixture.engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None))
        fixture.engine.stop()

        assertEquals(2, bridge.startCalls)
        fixture.close()
    }

    @Test
    fun `attachTun maps bridge success and failure`() = runTest {
        val success = fixture(bridge = RecordingBridge(attachResult = UrnetworkSdkBridge.AttachResult.Success))
        assertEquals(TunAttachResult.Success, success.engine.attachTun(44))
        success.close()

        val failure = fixture(
            bridge = RecordingBridge(attachResult = UrnetworkSdkBridge.AttachResult.Failed("attach bad")),
        )
        assertEquals(TunAttachResult.Failure("attach bad"), failure.engine.attachTun(45))
        failure.close()
    }

    @Test
    fun `exitNodeStrategy returns auto unavailable and concrete location`() = runTest {
        val auto = fixture(bridge = RecordingBridge(selected = null))
        assertIs<ExitNodeStrategy.AutoSelected>(auto.engine.exitNodeStrategy(0))
        auto.close()

        val pending = fixture(bridge = RecordingBridge(selected = Location("DE"), locationInfo = null))
        assertIs<ExitNodeStrategy.Unavailable>(pending.engine.exitNodeStrategy(0))
        pending.close()

        val bestAvailable = fixture(bridge = RecordingBridge(selected = Location(null, bestAvailable = true)))
        assertIs<ExitNodeStrategy.AutoSelected>(bestAvailable.engine.exitNodeStrategy(0))
        bestAvailable.close()

        val concrete = fixture(
            bridge = RecordingBridge(
                selected = Location("DE"),
                locationInfo = UrnetworkSdkBridge.LocationInfo(country = null, countryCode = "DE", name = "Germany"),
            ),
        )
        val strategy = concrete.engine.exitNodeStrategy(0)
        assertEquals(ExitNodeStrategy.LocationOnly("Germany", "DE"), strategy)
        concrete.close()
    }

    @Test
    fun `statsLabel prefers runtime peers then provider state then connected status`() {
        val peers = fixture(bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot(peers = 3)))
        assertEquals("3 peers", peers.engine.statsLabel(EngineStats(activeConnections = 1)))
        peers.close()

        val provider = fixture(
            bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot(providerStateAdded = 1)),
        )
        assertEquals("connected", provider.engine.statsLabel(EngineStats()))
        provider.close()

        val status = fixture(
            bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot(connectionStatus = "connected")),
        )
        assertEquals("connected", status.engine.statsLabel(EngineStats()))
        status.close()

        val fallbackStatus = fixture(
            bridge = RecordingBridge(
                snapshot = UrnetworkSdkBridge.RuntimeSnapshot(),
                fallbackConnectionStatus = "CONNECTED",
            ),
        )
        assertEquals("connected", fallbackStatus.engine.statsLabel(EngineStats()))
        fallbackStatus.close()

        val activeConnections = fixture(bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot()))
        assertEquals("2 peers", activeConnections.engine.statsLabel(EngineStats(activeConnections = 2)))
        activeConnections.close()

        val snapshotFallbackPeers = fixture(
            bridge = RecordingBridge(
                snapshotThrows = true,
                fallbackPeerCount = 4,
                fallbackConnectionStatus = null,
            ),
        )
        assertEquals("4 peers", snapshotFallbackPeers.engine.statsLabel(EngineStats()))
        snapshotFallbackPeers.close()

        val snapshotFallbackStatus = fixture(
            bridge = RecordingBridge(
                snapshotThrows = true,
                fallbackPeerCount = 0,
                fallbackConnectionStatus = "CONNECTED",
            ),
        )
        assertEquals("connected", snapshotFallbackStatus.engine.statsLabel(EngineStats()))
        snapshotFallbackStatus.close()

        val snapshotFallbackThrows = fixture(
            bridge = RecordingBridge(
                snapshotThrows = true,
                peerCountThrows = true,
                connectionStatusThrows = true,
            ),
        )
        assertNull(snapshotFallbackThrows.engine.statsLabel(EngineStats()))
        snapshotFallbackThrows.close()

        val empty = fixture(bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot()))
        assertNull(empty.engine.statsLabel(EngineStats()))
        empty.close()
    }

    @Test
    fun `buildManualConfig maps nullable settings to urnetwork config`() {
        val fixture = fixture()

        assertEquals(
            EngineConfig.Urnetwork(jwtToken = "", region = null),
            fixture.engine.buildManualConfig(null),
        )
        assertEquals(
            EngineConfig.Urnetwork(jwtToken = "jwt", region = "DE"),
            fixture.engine.buildManualConfig(SettingsModel(urnetworkJwt = "jwt", urnetworkCountryCode = "DE")),
        )
        fixture.close()
    }

    @Test
    fun `recover fails when bridge stopped reconnects selected location and best available`() = runTest {
        val stopped = fixture(bridge = RecordingBridge(running = false))
        assertEquals(EnginePlugin.RecoverResult.Failed("bridge not running"), stopped.engine.recover())
        stopped.close()

        val selectedBridge = RecordingBridge(selected = Location("FR"))
        val selected = fixture(bridge = selectedBridge)
        assertEquals(EnginePlugin.RecoverResult.Success, selected.engine.recover())
        assertEquals(1, selectedBridge.connectToCalls)
        assertEquals(0, selectedBridge.bestAvailableCalls)
        selected.close()

        val bestBridge = RecordingBridge(selected = null)
        val best = fixture(bridge = bestBridge)
        assertEquals(EnginePlugin.RecoverResult.Success, best.engine.recover())
        assertEquals(0, bestBridge.connectToCalls)
        assertEquals(1, bestBridge.bestAvailableCalls)
        best.close()
    }

    @Test
    fun `recover converts bridge reconnect throw to failure`() = runTest {
        val bridge = RecordingBridge(selected = Location("IT"), connectToThrows = true)
        val fixture = fixture(bridge = bridge)

        val result = fixture.engine.recover()

        assertIs<EnginePlugin.RecoverResult.Failed>(result)
        assertTrue(result.reason.contains("recover"))
        fixture.close()
    }

    @Test
    fun `tunSpec probe preflight and policy expose urnetwork contract`() = runTest {
        val fixture = fixture()

        val spec = fixture.engine.tunSpec()
        assertEquals("URnetwork", spec.sessionName)
        assertEquals(1440, spec.mtu)
        assertEquals("169.254.2.1", spec.ipv4Address)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), spec.dnsServers)
        assertTrue(spec.excludeRfc1918)
        assertIs<UrnetworkPreflight>(fixture.engine.preflight())
        assertEquals(
            ru.ozero.enginescore.ProbeResult.Failure("URnetwork does not provide a SOCKS endpoint"),
            fixture.engine.probe(),
        )
        assertEquals(8_000L, fixture.engine.stopTimeoutMs())
        assertTrue(fixture.engine.peerWatchdogPolicy().recoverBeforeFirstPeer)
        fixture.close()
    }

    @Test
    fun `awaitReady accepts runtime readiness signals and times out when absent`() = runTest {
        val readySignals = listOf(
            UrnetworkSdkBridge.RuntimeSnapshot(tunnelStarted = true, connectIssued = true),
            UrnetworkSdkBridge.RuntimeSnapshot(providerStateAdded = 1),
            UrnetworkSdkBridge.RuntimeSnapshot(peers = 1),
            UrnetworkSdkBridge.RuntimeSnapshot(connectionStatus = "connected"),
        )
        readySignals.forEach { snapshot ->
            val fixture = fixture(
                bridge = RecordingBridge(snapshot = snapshot),
                startupReadyTimeoutMs = 50L,
                startupReadyPollMs = 1L,
            )
            assertEquals(EnginePlugin.ReadyResult.Ready, fixture.engine.awaitReady())
            fixture.close()
        }

        val timeout = fixture(
            bridge = RecordingBridge(snapshot = UrnetworkSdkBridge.RuntimeSnapshot()),
            startupReadyTimeoutMs = 2L,
            startupReadyPollMs = 1L,
        )
        assertIs<EnginePlugin.ReadyResult.Timeout>(timeout.engine.awaitReady())
        timeout.close()
    }

    private fun fixture(
        store: InMemoryUrnetworkConfigStore = InMemoryUrnetworkConfigStore(UrnetworkConfig(byClientJwt = "client")),
        bridge: RecordingBridge = RecordingBridge(),
        bootstrap: UrnetworkJwtBootstrapper.Result = UrnetworkJwtBootstrapper.Result.AlreadyPresent,
        startupReadyTimeoutMs: Long = 8_000L,
        startupReadyPollMs: Long = 200L,
    ): Fixture {
        val dispatcher = StandardTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        return Fixture(
            engine = EngineUrnetwork(
                configStore = store,
                sdkBridge = bridge,
                jwtBootstrapper = StaticBootstrapper(bootstrap),
                pluginScope = scope,
                statsPollIntervalMs = 10_000L,
                startupReadyTimeoutMs = startupReadyTimeoutMs,
                startupReadyPollMs = startupReadyPollMs,
            ),
            bridge = bridge,
            scope = scope,
        )
    }

    private data class Fixture(
        val engine: EngineUrnetwork,
        val bridge: RecordingBridge,
        val scope: CoroutineScope,
    ) {
        fun close() {
            scope.cancel()
        }
    }

    private class StaticBootstrapper(
        private val result: UrnetworkJwtBootstrapper.Result,
    ) : UrnetworkJwtBootstrapper {
        override suspend fun ensureClientJwt(): UrnetworkJwtBootstrapper.Result = result
    }

    private data class Location(
        override val countryCode: String?,
        override val bestAvailable: Boolean = false,
    ) : UrnetworkSdkBridge.LocationToken

    private class RecordingBridge(
        private val startResult: UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success,
        private val attachResult: UrnetworkSdkBridge.AttachResult = UrnetworkSdkBridge.AttachResult.Success,
        private val running: Boolean = true,
        private val selected: UrnetworkSdkBridge.LocationToken? = null,
        private val locationInfo: UrnetworkSdkBridge.LocationInfo? = null,
        private val snapshot: UrnetworkSdkBridge.RuntimeSnapshot = UrnetworkSdkBridge.RuntimeSnapshot(),
        private val connectToThrows: Boolean = false,
        private val throwOptionalConfiguration: Boolean = false,
        private val snapshotThrows: Boolean = false,
        private val peerCountThrows: Boolean = false,
        private val connectionStatusThrows: Boolean = false,
        private val fallbackPeerCount: Int = snapshot.peers,
        private val fallbackConnectionStatus: String? = snapshot.connectionStatus,
    ) : UrnetworkSdkBridge {
        var startCalls = 0
        var connectToCalls = 0
        var bestAvailableCalls = 0
        var lastClientJwt: String? = null
        var recordedPreferredLocation: UrnetworkLocationSelection? = null
        var windowType: UrnetworkWindowType? = null
        var fixedIpSize: Boolean? = null
        var allowDirect: Boolean? = null
        var providePaused: Boolean? = null
        var controlMode: UrnetworkProvideControlMode? = null
        var networkMode: UrnetworkProvideNetworkMode? = null

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            lastClientJwt = byClientJwt
            return startResult
        }

        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = running
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult = attachResult
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) {
            connectToCalls++
            if (connectToThrows) error("connect failed")
        }
        override fun connectBestAvailable() {
            bestAvailableCalls++
        }
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = selected
        override fun selectedLocationInfo(): UrnetworkSdkBridge.LocationInfo? = locationInfo
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setPreferredLocation(selection: UrnetworkLocationSelection?) {
            recordedPreferredLocation = selection
            if (throwOptionalConfiguration) error("preferred failed")
        }
        override fun setProvidePaused(paused: Boolean) {
            providePaused = paused
            if (throwOptionalConfiguration) error("provide failed")
        }
        override fun isProvidePaused(): Boolean = providePaused == true
        override fun applyPerformanceProfile(
            windowType: UrnetworkWindowType,
            fixedIpSize: Boolean,
            allowDirect: Boolean,
        ) {
            this.windowType = windowType
            this.fixedIpSize = fixedIpSize
            this.allowDirect = allowDirect
            if (throwOptionalConfiguration) error("profile failed")
        }
        override fun setProvideControlMode(mode: UrnetworkProvideControlMode) {
            controlMode = mode
            if (throwOptionalConfiguration) error("control failed")
        }
        override fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {
            networkMode = mode
            if (throwOptionalConfiguration) error("network failed")
        }
        override fun runtimeSnapshot(): UrnetworkSdkBridge.RuntimeSnapshot {
            if (snapshotThrows) error("snapshot failed")
            return snapshot
        }
        override fun peerCount(): Int {
            if (peerCountThrows) error("peers failed")
            return fallbackPeerCount
        }
        override fun connectionStatus(): String? {
            if (connectionStatusThrows) error("status failed")
            return fallbackConnectionStatus
        }
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }
}
