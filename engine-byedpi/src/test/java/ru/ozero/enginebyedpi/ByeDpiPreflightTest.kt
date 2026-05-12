package ru.ozero.enginebyedpi

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ByeDpiPreflightTest {

    @Test
    fun `ByeDpiEngine возвращает ByeDpiPreflight как preflight`() {
        val proxy = mockk<ByeDpiProxy>(relaxed = true)
        val engine = ByeDpiEngine(proxy = proxy, socksProbe = { _, _, _ -> 1L })
        assertIs<ByeDpiPreflight>(engine.preflight())
    }

    @Test
    fun `ByeDpiPreflight нацелен на 1 1 1 1 443`() {
        assertEquals("1.1.1.1", ByeDpiPreflight.HOST)
        assertEquals(443, ByeDpiPreflight.PORT)
    }
}
