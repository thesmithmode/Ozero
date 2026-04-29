package ru.ozero.coreapi

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ByeDpiDefaultArgsTest {

    @Test
    fun `EngineConfig ByeDpi default args = РФ ТСПУ preset из ByeByeDPI v1_7_4`() {
        val expected = "-Ku -a1 -An -o1 -At,r,s -d1"
        assertEquals(
            expected,
            EngineConfig.ByeDpi().args,
            "Дефолт args для ByeDPI должен быть РФ ТСПУ preset из ByeByeDPI v1.7.4 wrapper: " +
                "-Ku (UDP) -a1 (fake count=1) -An (allow no server) -o1 (OOB byte 1) -At,r,s (split,replace,split) -d1. " +
                "Это эффективный default против российской ТСПУ DPI без пользовательского override.",
        )
    }

    @Test
    fun `EngineConfig ByeDpi default socks port = 1080`() {
        assertEquals(1080, EngineConfig.ByeDpi().socksPort)
    }
}
