package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarpPreflightTest {

    @Test
    fun `WarpPreflight class exists`() {
        val source = javaClass.classLoader!!
            .getResource("ru/ozero/enginewarp/WarpPreflight.class")
        assertTrue(source != null, "Класс должен существовать")
    }

    @Test
    fun `EngineWarp возвращает WarpPreflight как preflight с peerEndpointProvider`() {
        val source = java.io.File("src/main/java/ru/ozero/enginewarp/EngineWarp.kt").readText()
        assertTrue(
            source.contains("override fun preflight()") &&
                source.contains("WarpPreflight(peerEndpointProvider"),
            "EngineWarp.preflight() должен возвращать WarpPreflight(peerEndpointProvider=…)",
        )
    }

    @Test
    fun `WarpPreflight не должен резолвить engage cloudflareclient com по DNS`() {
        val source = java.io.File("src/main/java/ru/ozero/enginewarp/WarpPreflight.kt").readText()
        assertFalse(
            source.contains("engage.cloudflareclient.com"),
            "engage.cloudflareclient.com — DNS-зависимый host, preflight ломается на DNS-блоке",
        )
    }

    @Test
    fun `WarpPreflight использует 1_1_1_1 fallback`() {
        val source = java.io.File("src/main/java/ru/ozero/enginewarp/WarpPreflight.kt").readText()
        assertTrue(source.contains("1.1.1.1"), "fallback host = 1.1.1.1 (Cloudflare anycast IP, без DNS)")
        assertTrue(source.contains("443"), "TCP/443 — стандартный порт probe")
    }
}
