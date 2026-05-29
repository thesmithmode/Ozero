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
    fun `sentinel WarpUapi readState использует findUapiSocket — паритет с sockets-subdir migration`() {
        val uapiFile = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginewarp/WarpUapi.kt",
        )
        assertTrue(uapiFile.exists(), "WarpUapi.kt не найден: $uapiFile")
        val uapiSrc = uapiFile.readText()
        val readStateBody = uapiSrc.substringAfter("fun readState(")
            .substringBefore("private fun querySocket")
        assertTrue(
            readStateBody.contains("findUapiSocket"),
            "WarpUapi.readState обязан использовать findUapiSocket — иначе legacy путь " +
                "\$uapiPath/\$tunnelName.sock не существует после amneziawg-go sockets/ migration (v0.1.8)",
        )
        assertFalse(
            readStateBody.contains("File(uapiPath, \"\$tunnelName.sock\")"),
            "WarpUapi.readState больше НЕ строит legacy путь напрямую — это путь to sockets/ migration regression " +
                "(stats null → false DEGRADED в HealthMonitor)",
        )
    }

    @Test
    fun `sentinel startStatsPoll пишет periodic WARP stats в boot log`() {
        assertTrue(
            source.contains("STATS_LOG_EVERY"),
            "startStatsPoll обязан логировать periodic stats — без этого не отследить " +
                "медленность/прерывистость WARP в boot.log",
        )
        val body = source.substringAfter("private fun startStatsPoll(")
            .substringBefore("private data class ResolvedWarp")
        assertTrue(
            body.contains("PersistentLoggers.trace") && body.contains("warp stats"),
            "stats poll обязан вызывать PersistentLoggers.trace с \"warp stats\" prefix — boot.log readable, не info/warn",
        )
        assertTrue(
            body.contains("Δtx") && body.contains("Δrx"),
            "stats log обязан содержать дельту (Δtx/Δrx) для outage diagnostics, не только absolute",
        )
    }

    @Test
    fun `awaitReady timeout логирует dirListing — диагностика UAPI socket path`() {
        assertTrue(
            source.contains("WarpSocketDiagnostics.listSocketCandidates(uapiPath)"),
            "awaitReady timeout обязан логировать содержимое uapiPath и подпапки wireguard/ — " +
                "иначе нельзя различить 'am-go не создал socket' vs 'socket в неожиданном пути'",
        )
        val diagFile = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginewarp/WarpSocketDiagnostics.kt",
        )
        assertTrue(
            diagFile.exists(),
            "WarpSocketDiagnostics вынесен в отдельный файл — иначе EngineWarp превышает TooManyFunctions=20",
        )
        val diagSrc = diagFile.readText()
        assertTrue(
            diagSrc.contains("fun listSocketCandidates"),
            "listSocketCandidates должен жить в WarpSocketDiagnostics — discriminating helper",
        )
        assertTrue(
            diagSrc.contains("wireguard"),
            "listSocketCandidates обязан проверять подпапку wireguard/ — возможный путь am-go default",
        )
    }

    @Test
    fun `sentinel awaitReady при Ready ставит activeConnections=1 — amber flash fix`() {
        val awaitBody = source.substringAfter("override suspend fun awaitReady()")
            .substringBefore("override suspend fun recover()")
        assertTrue(
            awaitBody.contains("activeConnections = 1") || awaitBody.contains("activeConnections=1"),
            "awaitReady обязан ставить activeConnections=1 при Ready — " +
                "без этого UI мигает amber (0 connections → 1) до первого stats poll. " +
                "Регрессия 2026-05-26: amber flash fix.",
        )
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
    fun `resolveEndpointHost использует DoH provider текущего конфига`() {
        val body = source.substringAfter("private suspend fun resolveEndpointHost")
            .substringBefore("private fun resolveViaDoH")
        assertTrue(
            body.contains("val provider = cfg.doHProvider"),
            "Endpoint hostname resolve обязан брать DoH provider из текущего WARP slot config. " +
                "Иначе первый resolve уходит через system DNS и может раскрыть локального DNS провайдера.",
        )
        assertFalse(
            body.contains("resolvedConfig?.doHProvider"),
            "resolvedConfig содержит предыдущий slot или null на первом старте — " +
                "его нельзя использовать для DNS policy.",
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
        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = "id"
        override suspend fun setActive(id: String) {}
        override suspend fun rename(id: String, name: String) {}
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) {
        }
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
        override fun reprotectSockets() {}
    }
}
