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
}
