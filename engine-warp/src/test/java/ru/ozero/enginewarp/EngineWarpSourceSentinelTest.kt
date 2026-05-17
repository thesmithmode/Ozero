package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineWarpSourceSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginewarp/EngineWarp.kt")
        assertTrue(f.exists(), "EngineWarp.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `anchors — все функции-границы существуют в источнике`() {
        listOf(
            "private suspend fun resolveEndpointHost",
            "private fun resolveViaDoH",
            "private suspend fun buildResolved(",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Anchor потерян в EngineWarp.kt: '$anchor'")
        }
    }

    @Test
    fun `resolveEndpointHost не использует Thread sleep — только cooperative delay`() {
        val body = source.substringAfter("private suspend fun resolveEndpointHost")
            .substringBefore("private fun resolveViaDoH")
        assertFalse(
            body.contains("Thread.sleep"),
            "resolveEndpointHost не должен использовать Thread.sleep — блокирует dispatcher. " +
                "Использовать delay() для cooperative suspend.",
        )
        assertTrue(
            body.contains("delay("),
            "resolveEndpointHost обязан использовать delay() вместо Thread.sleep для retry-паузы.",
        )
    }

    @Test
    fun `resolveEndpointHost оборачивает InetAddress getByName в withContext IO`() {
        val body = source.substringAfter("private suspend fun resolveEndpointHost")
            .substringBefore("private fun resolveViaDoH")
        assertTrue(
            body.contains("withContext(Dispatchers.IO)"),
            "InetAddress.getByName — blocking call, обязан быть в withContext(Dispatchers.IO). " +
                "Без этого блокирует Dispatchers.Default при system DNS lookup.",
        )
    }

    @Test
    fun `buildResolved является suspend fun`() {
        assertTrue(
            source.contains("private suspend fun buildResolved("),
            "buildResolved обязан быть suspend — вызывает suspend fun resolveEndpointHost.",
        )
    }

    @Test
    fun `start с hostname endpoint gracefully возвращает Success при DNS failure`() = runTest {
        val hostnameConfig = WarpConfig(
            privateKey = "p",
            publicKey = "P",
            peerPublicKey = "PP",
            peerEndpoint = "nonexistent.warp.invalid:2408",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "",
            accountLicense = "L",
        )
        val store = FakeStore(hostnameConfig)
        val auto = FakeAuto(Result.success(RegisteredWarpConfig(hostnameConfig, "[Interface]\n[Peer]\n")))
        val e = EngineWarp(
            autoConfig = auto,
            configStore = store,
            sdkBridge = FakeBridge(),
            uapiPathProvider = { "/tmp" },
            socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
            ipv6EnabledProvider = { false },
            handshakeChecker = { _, _ -> true },
        )
        val r = e.start(EngineConfig.Warp, Upstream.None)
        assertIs<StartResult.Success>(r)
    }

    private class FakeAuto(private val result: Result<RegisteredWarpConfig>) : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> = result
    }

    private class FakeStore(activeConfig: WarpConfig) : WarpConfigSlotStore {
        private val slot = WarpConfigSlot("id", "name", activeConfig, isActive = true, rawIniOverride = null)
        override fun slots(): Flow<List<WarpConfigSlot>> = MutableStateFlow(listOf(slot))
        override fun activeSlot(): Flow<WarpConfigSlot?> = MutableStateFlow(slot)
        override fun activeConfig(): Flow<WarpConfig?> = MutableStateFlow(slot.config)
        override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?) = "id"
        override suspend fun setActive(id: String) {}
        override suspend fun rename(id: String, name: String) {}
        override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?) {}
        override suspend fun delete(id: String) {}
        override suspend fun clear() {}
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) {}
    }

    private class FakeBridge : WarpSdkBridge {
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success
        override suspend fun detachTun() {}
        override fun isRunning(): Boolean = false
    }
}
