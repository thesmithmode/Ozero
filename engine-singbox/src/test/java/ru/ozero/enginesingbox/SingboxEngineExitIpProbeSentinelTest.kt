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
            source.contains("activeSocksPort = probePort"),
            "TUN mode должен выделять локальный SOCKS endpoint для exit-IP probe.",
        )
        assertTrue(
            source.contains("ConfigBuilder.buildSingboxConfig(bean, probeSocksPort)") &&
                source.contains("ConfigBuilder.buildSingboxAutoConfig(beans, probeSocksPort)") &&
                source.contains("ConfigBuilder.buildProfileChainConfig(bean, wrappers, probeSocksPort)"),
            "Все TUN-конфиги sing-box должны получать probeSocksPort, иначе UI снова уйдёт в direct fetch.",
        )
        assertTrue(
            source.contains("ExitNodeStrategy.ViaSocks(\"127.0.0.1\", port)"),
            "exitNodeStrategy для sing-box должен вести HTTP проверку через локальный SOCKS sing-box, " +
                "чтобы цепочка прокси показывала самый конечный публичный выход.",
        )
    }
}
