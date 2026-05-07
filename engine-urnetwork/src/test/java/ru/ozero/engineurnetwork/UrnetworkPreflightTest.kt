package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UrnetworkPreflightTest {

    @Test
    fun `EngineUrnetwork возвращает UrnetworkPreflight как preflight`() {
        val source = java.io.File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        assertTrue(
            source.contains("override fun preflight()") && source.contains("UrnetworkPreflight()"),
            "EngineUrnetwork.preflight() должен возвращать UrnetworkPreflight",
        )
    }

    @Test
    fun `UrnetworkPreflight нацелен на ssl bringyour com 443`() {
        val source = java.io.File("src/main/java/ru/ozero/engineurnetwork/UrnetworkPreflight.kt").readText()
        assertTrue(source.contains("ssl.bringyour.com"), "host ssl.bringyour.com")
        assertTrue(source.contains("443"), "port 443")
    }
}
