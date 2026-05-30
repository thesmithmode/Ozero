package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SingboxEngineExitIpProbeSentinelTest {

    @Test
    fun `singbox exit ip probe uses local socks endpoint instead of direct app fetch`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginesingbox/SingboxEngine.kt",
        ).readText()

        assertTrue(
            source.contains("pendingSocksPort = probePort") &&
                source.contains("activeSocksPort = pendingSocksPort"),
            "TUN mode must keep the probe SOCKS port pending until sing-box runtime accepts startWithConfig.",
        )
        assertTrue(
            source.contains("ConfigBuilder.buildSingboxConfig(bean, probeSocksPort)") &&
                source.contains("ConfigBuilder.buildSingboxAutoConfig(beans, probeSocksPort)") &&
                source.contains("ConfigBuilder.buildProfileChainConfig(bean, wrappers, probeSocksPort)"),
            "All sing-box TUN configs must receive probeSocksPort so exit-IP probe uses the real outbound graph.",
        )
        assertTrue(
            source.contains("ExitNodeStrategy.ViaSocks(\"127.0.0.1\", port)"),
            "exitNodeStrategy for sing-box must route HTTP probe through the active local SOCKS endpoint.",
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
        val activeIdx = attachBlock.indexOf("activeSocksPort = pendingSocksPort")
        assertTrue(
            healthIdx >= 0 && activeIdx > healthIdx,
            "attachTun must check runtime health before publishing activeSocksPort, otherwise exit-IP probe can use stale port",
        )
        assertTrue(
            attachBlock.contains("clearPendingStart()") &&
                attachBlock.contains("return TunAttachResult.Failure(\"sing-box runtime failed to start\")"),
            "failed runtime health must clear pending/active SOCKS state before exitNodeStrategy can observe it",
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
