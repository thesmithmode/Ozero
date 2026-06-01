package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EngineWarpTimeoutSentinelTest {

    @Test
    fun `WARP_READY_TIMEOUT_MS = 30s because startup must prove real handshake`() {
        assertEquals(
            30_000L,
            EngineWarp.WARP_READY_TIMEOUT_MS,
            "WARP must not publish Connected without WireGuard handshake. Slow networks need more than 10s, " +
                "and timeout must stay a readiness signal instead of creating a false-connected tunnel.",
        )
    }

    @Test
    fun `WARP startup timeout fast-fails before first peer`() {
        val policy = EngineWarp(
            autoConfig = EmptyAutoConfig,
            configStore = EmptySlotStore,
            sdkBridge = NoopBridge,
            uapiPathProvider = { "/tmp" },
        ).peerWatchdogPolicy()

        assertFalse(policy.recoverBeforeFirstPeer)
        assertEquals(EngineWarp.WARP_PEER_WATCHDOG_TIMEOUT_MS, policy.timeoutMs)
    }

    @Test
    fun `awaitReady timeout log must not claim startup proceeds`() {
        val source = java.io.File(
            "src/main/java/ru/ozero/enginewarp/EngineWarp.kt",
        ).readText()

        assertFalse(
            source.contains("awaitReady timeout - \$reason - " + "proceed" + "ing"),
            "WARP timeout diagnostics must stay readiness-only; StartSequence owns policy-driven recovery.",
        )
    }

    private object EmptyAutoConfig : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> =
            Result.failure(IllegalStateException("unused"))
    }

    private object EmptySlotStore : WarpConfigSlotStore {
        override fun slots() = kotlinx.coroutines.flow.flowOf(emptyList<WarpConfigSlot>())
        override fun activeSlot() = kotlinx.coroutines.flow.flowOf<WarpConfigSlot?>(null)
        override fun activeConfig() = kotlinx.coroutines.flow.flowOf<WarpConfig?>(null)
        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ): String = error("unused")
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() = Unit
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) = Unit
    }

    private object NoopBridge : WarpSdkBridge {
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success

        override suspend fun startProxy(
            tunnelName: String,
            iniConfig: String,
            uapiPath: String,
            socksPort: Int,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.ProxyResult = WarpSdkBridge.ProxyResult.Success

        override suspend fun detachTun() = Unit
        override fun isRunning(): Boolean = false
        override fun reprotectSockets() = Unit
    }
}
