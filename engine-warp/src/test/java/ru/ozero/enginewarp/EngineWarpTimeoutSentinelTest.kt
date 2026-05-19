package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EngineWarpTimeoutSentinelTest {

    @Test
    fun `WARP_READY_TIMEOUT_MS = 10s — handshake требует запаса по сети v0_1_5_1`() {
        assertEquals(
            10_000L,
            EngineWarp.WARP_READY_TIMEOUT_MS,
            "5s не хватает Cloudflare WARP handshake на медленной сети — fast-fail ломал WARP. " +
                "10s — достаточно, при этом не блокирует UX надолго.",
        )
    }
}
