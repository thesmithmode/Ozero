package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxEngineExitIpProbeSentinelTest {

    @Test
    fun `singbox tun mode does not expose local socks endpoint for exit ip probe`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        val startBlock = source.substringAfter("override suspend fun start")
            .substringBefore("private fun buildPendingConfig")
        assertTrue(
            startBlock.contains("buildPendingConfig(config)") &&
                !startBlock.contains("allocateChainPort()") &&
                !startBlock.contains("pendingSocksPort = probePort"),
            "TUN mode must not create a localhost SOCKS listener solely for exit-IP probing.",
        )
        assertTrue(
            source.contains("ConfigBuilder.buildSingboxConfig(bean)") &&
                source.contains("ConfigBuilder.buildSingboxAutoConfig(beans)") &&
                source.contains("ConfigBuilder.buildProfileChainConfig(bean, wrappers)"),
            "TUN configs must be built without a probe SOCKS port.",
        )
        assertTrue(
            source.contains("ExitNodeStrategy.DirectHttp"),
            "TUN exit-node probing must avoid publishing a local SOCKS route when no chain SOCKS port is active.",
        )
    }

    @Test
    fun `singbox tun attach publishes socks port only after runtime health succeeds`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("override suspend fun stop")
        val healthIdx = attachBlock.indexOf("runtimeRunning")
        assertTrue(
            healthIdx >= 0 && !attachBlock.contains("activeSocksPort = pendingSocksPort"),
            "attachTun must not publish a TUN-only SOCKS probe port.",
        )
        assertTrue(
            attachBlock.contains("clearPendingStart()") &&
                attachBlock.contains("return TunAttachResult.Failure(\"sing-box runtime failed to start\")"),
            "failed runtime health must clear pending/active SOCKS state before exitNodeStrategy can observe it",
        )
    }

    @Test
    fun `singbox clears active socks port on process death and routed probe failure`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            source.contains("private fun clearRuntimeState()"),
            "SingboxEngine must have one runtime-state cleanup path for stale activeSocksPort",
        )
        assertTrue(
            source.substringAfter("IBinder.DeathRecipient {").substringBefore("deathRecipient = recipient")
                .contains("clearRuntimeState()"),
            "DeathRecipient must clear activeSocksPort when :engine_singbox dies",
        )
        assertTrue(
            source.substringAfter("onServiceDisconnected").substringBefore("onBindingDied")
                .contains("clearRuntimeState()"),
            "onServiceDisconnected must clear activeSocksPort after system disconnect",
        )
        assertTrue(
            source.substringAfter("onBindingDied").substringBefore("private fun bindOrFail")
                .contains("clearRuntimeState()"),
            "onBindingDied must clear activeSocksPort after binding death",
        )
        assertTrue(
            source.substringAfter("override suspend fun probe()").substringBefore("override fun stats()")
                .contains("activeSocksPort = 0"),
            "routed probe failure must clear activeSocksPort so exitNodeStrategy cannot reuse stale SOCKS",
        )
    }

    @Test
    fun `singbox stop waits for remote stop before close`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        val stopBlock = source.substringAfter("override suspend fun stop()")
            .substringBefore("override fun stopTimeoutMs()")
        val stopIdx = stopBlock.indexOf("stopAndWait(REMOTE_STOP_TIMEOUT_MS)")
        val closeIdx = stopBlock.indexOf("close()")
        assertTrue(
            stopIdx >= 0 && closeIdx > stopIdx,
            "SingboxEngine.stop must wait for remote runtime stop before unbind/close to avoid rapid restart racing old libbox",
        )
        assertTrue(
            source.contains("override fun stopTimeoutMs(): Long = ENGINE_STOP_TIMEOUT_MS"),
            "SingboxEngine stop timeout must exceed remote stopAndWait timeout so ChainOrchestrator does not cancel it early",
        )
    }
}
