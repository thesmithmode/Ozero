package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WarpPreflightTest {

    @Test
    fun `WarpPreflight нацелен на Cloudflare endpoint`() {
        val source = javaClass.classLoader!!
            .getResource("ru/ozero/enginewarp/WarpPreflight.class")
        assertTrue(source != null, "Класс должен существовать")
    }

    @Test
    fun `EngineWarp возвращает WarpPreflight как preflight`() {
        val source = java.io.File("src/main/java/ru/ozero/enginewarp/EngineWarp.kt").readText()
        assertTrue(
            source.contains("override fun preflight()") && source.contains("WarpPreflight()"),
            "EngineWarp.preflight() должен возвращать WarpPreflight",
        )
    }

    @Test
    fun `WarpPreflight содержит engage cloudflareclient com 443`() {
        val source = java.io.File("src/main/java/ru/ozero/enginewarp/WarpPreflight.kt").readText()
        assertTrue(source.contains("engage.cloudflareclient.com"), "host должен быть engage.cloudflareclient.com")
        assertTrue(source.contains("443"), "port 443")
    }
}
