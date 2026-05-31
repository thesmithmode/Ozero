package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EngineWarpTimeoutSentinelTest {

    @Test
    fun `WARP_READY_TIMEOUT_MS = 30s because startup must prove real handshake`() {
        assertEquals(
            30_000L,
            EngineWarp.WARP_READY_TIMEOUT_MS,
            "WARP must not publish Connected without WireGuard handshake. Slow networks need more than 10s, " +
                "but timeout must still fail startup instead of creating a false-connected tunnel.",
        )
    }

    @Test
    fun `awaitReady timeout log must not claim startup proceeds`() {
        val source = java.io.File(
            "src/main/java/ru/ozero/enginewarp/EngineWarp.kt",
        ).readText()

        assertFalse(
            source.contains("awaitReady timeout - \$reason - " + "proceed" + "ing"),
            "WARP timeout diagnostics must match the StartSequence fast-fail contract.",
        )
    }
}
