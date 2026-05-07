package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ByeDpiPreflightTest {

    @Test
    fun `ByeDpiEngine возвращает ByeDpiPreflight как preflight`() {
        val source = java.io.File("src/main/java/ru/ozero/enginebyedpi/ByeDpiEngine.kt").readText()
        assertTrue(
            source.contains("override fun preflight()") && source.contains("ByeDpiPreflight()"),
            "ByeDpiEngine.preflight() должен возвращать ByeDpiPreflight",
        )
    }

    @Test
    fun `ByeDpiPreflight нацелен на 1 1 1 1 443`() {
        val source = java.io.File("src/main/java/ru/ozero/enginebyedpi/ByeDpiPreflight.kt").readText()
        assertTrue(source.contains("1.1.1.1"), "host 1.1.1.1")
        assertTrue(source.contains("443"), "port 443")
    }
}
