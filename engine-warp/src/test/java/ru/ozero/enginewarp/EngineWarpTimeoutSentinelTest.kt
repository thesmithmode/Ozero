package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EngineWarpTimeoutSentinelTest {

    @Test
    fun `WARP_READY_TIMEOUT_MS = 5s — fast-fail handshake (v0_1_5)`() {
        assertEquals(
            5_000L,
            EngineWarp.WARP_READY_TIMEOUT_MS,
            "10s заставляет юзера ждать 10s каждый failed start (manual WARP) — visual slowdown. " +
                "Возврат к 10_000L → 'переключение модулей очень долго' (репорт юзера 2026-05-19).",
        )
    }
}
