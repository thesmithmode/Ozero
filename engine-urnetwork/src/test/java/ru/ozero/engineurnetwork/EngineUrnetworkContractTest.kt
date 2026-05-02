package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineUrnetworkContractTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    private fun engine(
        override: String? = null,
        byJwt: String? = null,
        bridge: FakeUrnetworkSdkBridge = FakeUrnetworkSdkBridge(),
        authService: FakeAuthService = FakeAuthService(),
    ): Triple<EngineUrnetwork, FakeUrnetworkSdkBridge, FakeUrnetworkConfigStore> {
        val store = FakeUrnetworkConfigStore(override = override, byJwt = byJwt)
        return Triple(EngineUrnetwork(store, bridge, authService), bridge, store)
    }

    @Test
    fun `id равен URNETWORK`() {
        val (e, _, _) = engine()
        assertEquals(EngineId.URNETWORK, e.id)
    }

    @Test
    fun `start без override вызывает bridge с PRESET_WALLET`() = runTest {
        val (e, bridge, _) = engine(override = null, byJwt = "fake.jwt")
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(UrnetworkDefaults.PRESET_WALLET, bridge.lastWallet)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start с override вызывает bridge с override адресом`() = runTest {
        val custom = "AAAAbbbbCCCCdddd1111222233334444555566667777"
        val (e, bridge, _) = engine(override = custom, byJwt = "fake.jwt")
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(custom, bridge.lastWallet)
    }

    @Test
    fun `stop вызывает bridge_stop`() = runTest {
        val (e, bridge, _) = engine(byJwt = "fake.jwt")
        e.start(baseConfig, Upstream.None)
        e.stop()
        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `failed bridge пробрасывается как StartResult_Failure`() = runTest {
        val bridge = FakeUrnetworkSdkBridge(
            startResult = UrnetworkSdkBridge.StartResult.Failed("AAR not built"),
        )
        val (e, _, _) = engine(byJwt = "fake.jwt", bridge = bridge)
        val result = e.start(baseConfig, Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("AAR not built"))
    }

    @Test
    fun `start читает byJwt из store и пробрасывает в bridge`() = runTest {
        val token = "eyJabc.def.ghi"
        val (e, bridge, _) = engine(byJwt = token)
        e.start(baseConfig, Upstream.None)
        assertEquals(token, bridge.lastByJwt)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start без byJwt — auto-acquire guest jwt + persist + bridge call`() = runTest {
        val auth = FakeAuthService(jwt = "guest.tok.42")
        val (e, bridge, store) = engine(byJwt = null, authService = auth)
        val r = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(r)
        assertEquals(1, auth.acquireCalls)
        assertEquals("guest.tok.42", store.byJwtFlow.value)
        assertEquals("guest.tok.42", bridge.lastByJwt)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start auth fail — Failure без вызова bridge`() = runTest {
        val auth = FakeAuthService(error = "no internet")
        val (e, bridge, store) = engine(byJwt = null, authService = auth)
        val r = e.start(baseConfig, Upstream.None)
        val f = assertIs<StartResult.Failure>(r)
        assertTrue(f.reason.contains("guest", ignoreCase = true))
        assertEquals(0, bridge.startCalls)
        assertNull(store.byJwtFlow.value)
    }

    @Test
    fun `start с уже сохранённым byJwt не вызывает auth повторно`() = runTest {
        val auth = FakeAuthService(jwt = "should-not-use")
        val (e, bridge, _) = engine(byJwt = "existing.jwt", authService = auth)
        e.start(baseConfig, Upstream.None)
        assertEquals(0, auth.acquireCalls)
        assertEquals("existing.jwt", bridge.lastByJwt)
    }

    @Test
    fun `EngineUrnetwork is TunFdAcceptor — packet pump entry point`() {
        val (e, _, _) = engine()
        assertTrue(e is ru.ozero.enginescore.TunFdAcceptor)
    }

    @Test
    fun `attachTun проксирует в bridge с тем же fd`() = runTest {
        val (e, bridge, _) = engine(byJwt = "fake.jwt")
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
    fun `start требует EngineConfig_Urnetwork — другие типы throw IllegalArgumentException`() = runTest {
        val (e, _, _) = engine()
        val wrongConfig = EngineConfig.ByeDpi(args = "", socksPort = 1080)
        val ex = runCatching { e.start(wrongConfig, Upstream.None) }.exceptionOrNull()
        assertTrue(
            ex is IllegalArgumentException,
            "EngineConfig.ByeDpi → IllegalArgumentException (require false). " +
                "Без require — silent type confusion = неправильный engine принимает неправильный config.",
        )
    }

    @Test
    fun `probe возвращает Failure — URnetwork не SOCKS engine`() = runTest {
        val (e, _, _) = engine()
        val r = e.probe()
        val f = assertIs<ru.ozero.enginescore.ProbeResult.Failure>(r)
        assertTrue(
            f.reason.contains("SOCKS", ignoreCase = true),
            "probe() reason обязан упоминать SOCKS — URnetwork работает через TUN attach, " +
                "не SOCKS proxy. Без чёткого reason — debug log путает.",
        )
    }

    @Test
    fun `stats возвращает StateFlow с initial empty EngineStats`() = runTest {
        val (e, _, _) = engine()
        val initial = e.stats().first()
        assertEquals(ru.ozero.enginescore.EngineStats(), initial)
    }

    @Test
    fun `capabilities — supportsUpstreamSocks=false (TUN-only)`() {
        val (e, _, _) = engine()
        assertEquals(false, e.capabilities.supportsUpstreamSocks)
        assertEquals(true, e.capabilities.supportsTcp)
        assertEquals(true, e.capabilities.supportsUdp)
        assertEquals(true, e.capabilities.requiresServer)
    }

    @Test
    fun `start success возвращает StartResult_Success с socksPort из config`() = runTest {
        val cfg = EngineConfig.Urnetwork(jwtToken = "", socksPort = 4242)
        val (e, _, _) = engine(byJwt = "tok")
        val r = e.start(cfg, Upstream.None)
        val s = assertIs<StartResult.Success>(r)
        assertEquals(
            4242,
            s.socksPort,
            "StartResult.Success обязан хранить socksPort из config — иначе chain orchestrator " +
                "не сможет передать его в следующее звено каскада.",
        )
    }

    private class FakeUrnetworkConfigStore(
        override: String?,
        byJwt: String? = null,
    ) : UrnetworkConfigStore {
        private val overrideFlow = MutableStateFlow(override)
        val byJwtFlow = MutableStateFlow(byJwt)
        override fun walletAddress(): Flow<String> =
            overrideFlow.map { it ?: UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = overrideFlow
        override suspend fun setWalletOverride(value: String?) {
            overrideFlow.value = value
        }
        override fun byJwt(): Flow<String?> = byJwtFlow
        override suspend fun setByJwt(value: String?) {
            byJwtFlow.value = value
        }
    }

    private class FakeAuthService(
        private val jwt: String = "fake.jwt",
        private val error: String? = null,
    ) : UrnetworkAuthService {
        var acquireCalls: Int = 0
        override suspend fun acquireGuestJwt(): GuestJwtResult {
            acquireCalls++
            return error?.let { GuestJwtResult.Error(it) } ?: GuestJwtResult.Success(jwt)
        }
    }

    private class FakeUrnetworkSdkBridge(
        private val startResult: UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success,
    ) : UrnetworkSdkBridge {
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var lastWallet: String? = null
        var lastApi: String? = null
        var lastConnect: String? = null
        var lastByJwt: String? = null
        private var running = false

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byJwt: String?,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            lastWallet = walletAddress
            lastApi = apiUrl
            lastConnect = connectUrl
            lastByJwt = byJwt
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
    }
}
