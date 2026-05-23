package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineUrnetworkContractTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    private fun engine(
        override: String? = null,
        byJwt: String? = null,
        byClientJwt: String? = null,
        provideEnabled: Boolean = true,
        bridge: FakeUrnetworkSdkBridge = FakeUrnetworkSdkBridge(),
        authService: FakeAuthService = FakeAuthService(),
    ): Triple<EngineUrnetwork, FakeUrnetworkSdkBridge, InMemoryUrnetworkConfigStore> {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                walletOverride = override,
                byJwt = byJwt,
                byClientJwt = byClientJwt,
                provideEnabled = provideEnabled,
            ),
        )
        return Triple(
            EngineUrnetwork(store, bridge, RealUrnetworkJwtBootstrapper(store, authService, null)),
            bridge,
            store,
        )
    }

    @Test
    fun `id равен URNETWORK`() {
        val (e, _, _) = engine()
        assertEquals(EngineId.URNETWORK, e.id)
    }

    @Test
    fun `start без override вызывает bridge с PRESET_WALLET`() = runTest {
        val (e, bridge, _) = engine(byJwt = "j", byClientJwt = "cj")
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(UrnetworkDefaults.PRESET_WALLET, bridge.lastWallet)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start с override вызывает bridge с override адресом`() = runTest {
        val custom = "AAAAbbbbCCCCdddd1111222233334444555566667777"
        val (e, bridge, _) = engine(override = custom, byJwt = "j", byClientJwt = "cj")
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(custom, bridge.lastWallet)
    }

    @Test
    fun `stop вызывает bridge_stop`() = runTest {
        val (e, bridge, _) = engine(byJwt = "j", byClientJwt = "cj")
        e.start(baseConfig, Upstream.None)
        e.stop()
        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `failed bridge пробрасывается как StartResult_Failure`() = runTest {
        val bridge = FakeUrnetworkSdkBridge(
            startResult = UrnetworkSdkBridge.StartResult.Failed("AAR not built"),
        )
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj", bridge = bridge)
        val result = e.start(baseConfig, Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("AAR not built"))
    }

    @Test
    fun `start читает byClientJwt из store и пробрасывает в bridge`() = runTest {
        val cjwt = "client.eyJabc.def.ghi"
        val (e, bridge, _) = engine(byJwt = "j", byClientJwt = cjwt)
        e.start(baseConfig, Upstream.None)
        assertEquals(cjwt, bridge.lastByClientJwt)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start без byJwt — auto-acquire guest+client JWT и persist оба`() = runTest {
        val auth = FakeAuthService(jwt = "guest.tok.42", clientJwt = "client.tok.42")
        val (e, bridge, store) = engine(byJwt = null, byClientJwt = null, authService = auth)
        val r = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(r)
        assertEquals(1, auth.acquireGuestCalls)
        assertEquals(1, auth.acquireClientCalls)
        assertEquals("guest.tok.42", store.snapshot.byJwt)
        assertEquals("client.tok.42", store.snapshot.byClientJwt)
        assertEquals("client.tok.42", bridge.lastByClientJwt)
        assertEquals("guest.tok.42", auth.acquireClientByJwt)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start guest auth fail — Failure без вызова bridge или client jwt`() = runTest {
        val auth = FakeAuthService(guestError = "no internet")
        val (e, bridge, store) = engine(byJwt = null, authService = auth)
        val r = e.start(baseConfig, Upstream.None)
        val f = assertIs<StartResult.Failure>(r)
        assertTrue(f.reason.contains("guest", ignoreCase = true))
        assertEquals(0, bridge.startCalls)
        assertEquals(0, auth.acquireClientCalls)
        assertNull(store.snapshot.byJwt)
        assertNull(store.snapshot.byClientJwt)
    }

    @Test
    fun `start client auth fail — Failure без вызова bridge`() = runTest {
        val auth = FakeAuthService(jwt = "g", clientError = "server error")
        val (e, bridge, store) = engine(byJwt = null, byClientJwt = null, authService = auth)
        val r = e.start(baseConfig, Upstream.None)
        val f = assertIs<StartResult.Failure>(r)
        assertTrue(f.reason.contains("client", ignoreCase = true))
        assertEquals(0, bridge.startCalls)
        assertNotNull(store.snapshot.byJwt)
        assertNull(store.snapshot.byClientJwt)
    }

    @Test
    fun `start с сохранёнными jwt не вызывает auth повторно`() = runTest {
        val auth = FakeAuthService(jwt = "should-not-use", clientJwt = "should-not-use")
        val (e, bridge, _) = engine(byJwt = "existing.jwt", byClientJwt = "existing.cjwt", authService = auth)
        e.start(baseConfig, Upstream.None)
        assertEquals(0, auth.acquireGuestCalls)
        assertEquals(0, auth.acquireClientCalls)
        assertEquals("existing.cjwt", bridge.lastByClientJwt)
    }

    @Test
    fun `EngineUrnetwork is TunFdAcceptor`() {
        val (e, _, _) = engine()
        assertTrue(e is ru.ozero.enginescore.TunFdAcceptor)
    }

    @Test
    fun `attachTun проксирует в bridge с тем же fd`() = runTest {
        val (e, bridge, _) = engine(byJwt = "j", byClientJwt = "cj")
        e.start(baseConfig, Upstream.None)
        val acceptor = e as ru.ozero.enginescore.TunFdAcceptor
        val r = acceptor.attachTun(42)
        assertIs<ru.ozero.enginescore.TunAttachResult.Success>(r)
        assertEquals(1, bridge.attachTunCalls)
        assertEquals(42, bridge.lastAttachFd)
    }

    @Test
    fun `start требует Upstream_None как и ByeDpi`() = runTest {
        val (e, _, _) = engine()
        runCatching {
            e.start(baseConfig, Upstream.Socks5("127.0.0.1", 1080))
        }.fold(
            onSuccess = { result ->
                val failure = assertIs<StartResult.Failure>(result)
                assertTrue(failure.reason.contains("upstream", ignoreCase = true))
            },
            onFailure = {
                assertTrue(it is IllegalArgumentException)
            },
        )
    }

    @Test
    fun `start требует EngineConfig_Urnetwork`() = runTest {
        val (e, _, _) = engine()
        val wrongConfig = EngineConfig.ByeDpi(args = "", socksPort = 1080)
        val ex = runCatching { e.start(wrongConfig, Upstream.None) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `probe возвращает Failure — URnetwork не SOCKS engine`() = runTest {
        val (e, _, _) = engine()
        val r = e.probe()
        val f = assertIs<ru.ozero.enginescore.ProbeResult.Failure>(r)
        assertTrue(f.reason.contains("SOCKS", ignoreCase = true))
    }

    @Test
    fun `stats возвращает StateFlow с initial empty EngineStats`() = runTest {
        val (e, _, _) = engine()
        val initial = e.stats().first()
        assertEquals(ru.ozero.enginescore.EngineStats(), initial)
    }

    @Test
    fun `capabilities — supportsUpstreamSocks=false`() {
        val (e, _, _) = engine()
        assertEquals(false, e.capabilities.supportsUpstreamSocks)
        assertEquals(true, e.capabilities.supportsTcp)
        assertEquals(true, e.capabilities.supportsUdp)
        assertEquals(true, e.capabilities.requiresServer)
    }

    @Test
    fun `start success всегда возвращает socksPort=0 — URnetwork TUN-only без SOCKS`() = runTest {
        val cfg = EngineConfig.Urnetwork(jwtToken = "", socksPort = 4242)
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj")
        val r = e.start(cfg, Upstream.None)
        val s = assertIs<StartResult.Success>(r)
        assertEquals(0, s.socksPort)
    }

    @Test
    fun `tunSpec не null — URnetwork требует custom TUN params`() = runTest {
        val (e, _, _) = engine()
        val spec = e.tunSpec()
        assertNotNull(spec)
        assertEquals(1440, spec.mtu)
        assertEquals(false, spec.blocking)
        assertEquals("169.254.2.1", spec.ipv4Address)
        assertTrue(spec.dnsServers.contains("1.1.1.1"))
        assertTrue(spec.excludeRfc1918)
    }

    @Test
    fun `ipProbeRoute возвращает AutoSelected когда selectedLocation null (Best Available режим)`() = runTest {
        val (e, _, _) = engine()
        val route = e.ipProbeRoute(0)
        assertIs<ru.ozero.enginescore.IpProbeRoute.AutoSelected>(
            route,
            "selectedLocation==null = Best Available режим. UI обязан показывать 'Авто', " +
                "не ошибку 'не удалось получить IP'.",
        )
    }

    @Test
    fun `ipProbeRoute возвращает StaticLocation с кодом когда selectedLocation валидный`() = runTest {
        val bridge = FakeUrnetworkSdkBridge().also {
            it.selectedLocationResult = object : UrnetworkSdkBridge.LocationToken {
                override val countryCode = "IN"
            }
            it.locationInfoResult = UrnetworkSdkBridge.LocationInfo(
                country = "India",
                countryCode = "IN",
                name = "India",
            )
        }
        val (e, _, _) = engine(bridge = bridge)
        val route = e.ipProbeRoute(0)
        assertIs<ru.ozero.enginescore.IpProbeRoute.StaticLocation>(route)
        assertEquals("IN", route.countryCode)
        assertEquals("India", route.country)
    }

    @Test
    fun `ipProbeRoute возвращает StaticLocation с null country когда country и name оба null`() = runTest {
        val bridge = FakeUrnetworkSdkBridge().also {
            it.selectedLocationResult = object : UrnetworkSdkBridge.LocationToken {
                override val countryCode = "US"
            }
            it.locationInfoResult = UrnetworkSdkBridge.LocationInfo(
                country = null,
                countryCode = "US",
                name = null,
            )
        }
        val (e, _, _) = engine(bridge = bridge)
        val route = e.ipProbeRoute(0)
        assertIs<ru.ozero.enginescore.IpProbeRoute.StaticLocation>(route)
        assertNull(route.country)
        assertEquals("US", route.countryCode)
    }

    @Test
    fun `start с пустым selectedLocation fallback на config_region в setPreferredLocation`() = runTest {
        val bridge = FakeUrnetworkSdkBridge()
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj", bridge = bridge)
        e.start(EngineConfig.Urnetwork(jwtToken = "", region = "us"), Upstream.None)
        val pref = bridge.lastPreferredLocation
        assertNotNull(pref, "setPreferredLocation должен быть вызван с не-null selection — config.region передан")
        assertEquals("US", pref.countryCode)
        assertNull(pref.region)
        assertNull(pref.city)
    }

    @Test
    fun `start с заполненным selectedLocation игнорирует config_region — store wins`() = runTest {
        val bridge = FakeUrnetworkSdkBridge()
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                selectedLocation = UrnetworkLocationSelection(
                    countryCode = "DE",
                    region = "Bavaria",
                    city = "Munich",
                ),
            ),
        )
        val engine = EngineUrnetwork(store, bridge, RealUrnetworkJwtBootstrapper(store, FakeAuthService(), null))
        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = "us"), Upstream.None)
        val pref = bridge.lastPreferredLocation
        assertNotNull(pref)
        assertEquals("DE", pref.countryCode)
        assertEquals("Bavaria", pref.region)
        assertEquals("Munich", pref.city)
    }

    @Test
    fun `start с пустым selectedLocation И пустым config_region передаёт null в setPreferredLocation`() = runTest {
        val bridge = FakeUrnetworkSdkBridge()
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj", bridge = bridge)
        e.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)
        assertEquals(1, bridge.preferredLocationCalls)
        assertNull(bridge.lastPreferredLocation, "Пустая selection → normalized() → null → auto-best")
    }

    @Test
    fun `start с provideEnabled=true вызывает setProvidePaused(false) на bridge`() = runTest {
        val bridge = FakeUrnetworkSdkBridge()
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj", provideEnabled = true, bridge = bridge)
        e.start(baseConfig, Upstream.None)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `start с provideEnabled=false вызывает setProvidePaused(true) на bridge`() = runTest {
        val bridge = FakeUrnetworkSdkBridge()
        val (e, _, _) = engine(byJwt = "j", byClientJwt = "cj", provideEnabled = false, bridge = bridge)
        e.start(baseConfig, Upstream.None)
        assertEquals(true, bridge.lastProvidePaused)
    }

    private class FakeAuthService(
        private val jwt: String = "fake.jwt",
        private val clientJwt: String = "fake.cjwt",
        private val guestError: String? = null,
        private val clientError: String? = null,
    ) : UrnetworkAuthService {
        var acquireGuestCalls: Int = 0
        var acquireClientCalls: Int = 0
        var acquireClientByJwt: String? = null
        override suspend fun acquireGuestJwt(): GuestJwtResult {
            acquireGuestCalls++
            return guestError?.let { GuestJwtResult.Error(it) } ?: GuestJwtResult.Success(jwt)
        }
        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult {
            acquireClientCalls++
            acquireClientByJwt = byJwt
            return clientError?.let { ClientJwtResult.Error(it) } ?: ClientJwtResult.Success(clientJwt)
        }
    }

    private class FakeUrnetworkSdkBridge(
        private val startResult: UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success,
    ) : UrnetworkSdkBridge {
        var locationInfoResult: UrnetworkSdkBridge.LocationInfo? = null
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var lastWallet: String? = null
        var lastApi: String? = null
        var lastConnect: String? = null
        var lastByClientJwt: String? = null
        private var running = false

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            lastWallet = walletAddress
            lastApi = apiUrl
            lastConnect = connectUrl
            lastByClientJwt = byClientJwt
            if (startResult is UrnetworkSdkBridge.StartResult.Success) running = true
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
            running = false
        }

        override fun isRunning(): Boolean = running

        var attachTunCalls: Int = 0
        var lastAttachFd: Int? = null
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult {
            attachTunCalls++
            lastAttachFd = tunFd
            return UrnetworkSdkBridge.AttachResult.Success
        }

        var selectedLocationResult: UrnetworkSdkBridge.LocationToken? = null

        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = selectedLocationResult
        override fun selectedLocationInfo(): UrnetworkSdkBridge.LocationInfo? = locationInfoResult
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        var lastProvidePaused: Boolean? = null
        override fun setProvidePaused(paused: Boolean) {
            lastProvidePaused = paused
        }
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
        var lastAppliedWindowType: UrnetworkWindowType? = null
        var lastAppliedFixedIp: Boolean? = null
        var lastAppliedAllowDirect: Boolean? = null
        override fun applyPerformanceProfile(
            windowType: UrnetworkWindowType,
            fixedIpSize: Boolean,
            allowDirect: Boolean,
        ) {
            lastAppliedWindowType = windowType
            lastAppliedFixedIp = fixedIpSize
            lastAppliedAllowDirect = allowDirect
        }
        var lastPreferredLocation: UrnetworkLocationSelection? = null
        var preferredLocationCalls: Int = 0
        override fun setPreferredLocation(selection: UrnetworkLocationSelection?) {
            preferredLocationCalls++
            lastPreferredLocation = selection
        }
    }
}

class EngineUrnetworkPerformanceProfileTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    @Test
    fun `start применяет windowType QUALITY из configStore к bridge`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                windowType = UrnetworkWindowType.QUALITY,
                fixedIpSize = false,
            ),
        )
        val bridge = FakeProfileBridge()
        val engine = EngineUrnetwork(
            store,
            bridge,
            RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
        )
        engine.start(baseConfig, Upstream.None)
        assertEquals(UrnetworkWindowType.QUALITY, bridge.lastAppliedWindowType)
        assertEquals(false, bridge.lastAppliedFixedIp)
    }

    @Test
    fun `start применяет windowType SPEED с fixedIp true из configStore к bridge`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                windowType = UrnetworkWindowType.SPEED,
                fixedIpSize = true,
            ),
        )
        val bridge = FakeProfileBridge()
        val engine = EngineUrnetwork(
            store,
            bridge,
            RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
        )
        engine.start(baseConfig, Upstream.None)
        assertEquals(UrnetworkWindowType.SPEED, bridge.lastAppliedWindowType)
        assertEquals(true, bridge.lastAppliedFixedIp)
    }

    @Test
    fun `start применяет AUTO windowType из configStore (AUTO не skipped на уровне engine)`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                windowType = UrnetworkWindowType.AUTO,
                fixedIpSize = false,
            ),
        )
        val bridge = FakeProfileBridge()
        val engine = EngineUrnetwork(
            store,
            bridge,
            RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
        )
        engine.start(baseConfig, Upstream.None)
        assertEquals(UrnetworkWindowType.AUTO, bridge.lastAppliedWindowType)
    }

    @Test
    fun `start пробрасывает allowDirect=true в bridge — anonymization OFF по умолчанию`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                windowType = UrnetworkWindowType.QUALITY,
                fixedIpSize = false,
                allowDirect = true,
            ),
        )
        val bridge = FakeProfileBridge()
        val engine = EngineUrnetwork(
            store,
            bridge,
            RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
        )
        engine.start(baseConfig, Upstream.None)
        assertEquals(true, bridge.lastAppliedAllowDirect)
    }

    @Test
    fun `start пробрасывает allowDirect=false в bridge при AUTO — anonymization ON применяется к AUTO профилю`() =
        runTest {
            val store = InMemoryUrnetworkConfigStore(
                UrnetworkConfig(
                    byJwt = "j",
                    byClientJwt = "cj",
                    windowType = UrnetworkWindowType.AUTO,
                    fixedIpSize = false,
                    allowDirect = false,
                ),
            )
            val bridge = FakeProfileBridge()
            val engine = EngineUrnetwork(
                store,
                bridge,
                RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
            )
            engine.start(baseConfig, Upstream.None)
            assertEquals(UrnetworkWindowType.AUTO, bridge.lastAppliedWindowType)
            assertEquals(false, bridge.lastAppliedAllowDirect)
        }

    @Test
    fun `start пробрасывает allowDirect=false с QUALITY windowType — комбинация anonymization+quality`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "j",
                byClientJwt = "cj",
                windowType = UrnetworkWindowType.QUALITY,
                fixedIpSize = true,
                allowDirect = false,
            ),
        )
        val bridge = FakeProfileBridge()
        val engine = EngineUrnetwork(
            store,
            bridge,
            RealUrnetworkJwtBootstrapper(store, FakeProfileAuthService(), null),
        )
        engine.start(baseConfig, Upstream.None)
        assertEquals(UrnetworkWindowType.QUALITY, bridge.lastAppliedWindowType)
        assertEquals(true, bridge.lastAppliedFixedIp)
        assertEquals(false, bridge.lastAppliedAllowDirect)
    }

    private class FakeProfileBridge : UrnetworkSdkBridge {
        var lastAppliedWindowType: UrnetworkWindowType? = null
        var lastAppliedFixedIp: Boolean? = null
        var lastAppliedAllowDirect: Boolean? = null
        override fun applyPerformanceProfile(
            windowType: UrnetworkWindowType,
            fixedIpSize: Boolean,
            allowDirect: Boolean,
        ) {
            lastAppliedWindowType = windowType
            lastAppliedFixedIp = fixedIpSize
            lastAppliedAllowDirect = allowDirect
        }
        override suspend fun start(walletAddress: String, apiUrl: String, connectUrl: String, byClientJwt: String) =
            UrnetworkSdkBridge.StartResult.Success
        override suspend fun stop() = Unit
        override fun isRunning() = false
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused() = false
        override fun peerCount() = 0
        override fun unpaidByteCount() = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }

    private class FakeProfileAuthService : ru.ozero.engineurnetwork.auth.UrnetworkAuthService {
        override suspend fun acquireGuestJwt() = ru.ozero.engineurnetwork.auth.GuestJwtResult.Success("j")
        override suspend fun acquireClientJwt(byJwt: String) =
            ru.ozero.engineurnetwork.auth.ClientJwtResult.Success("cj")
    }
}
