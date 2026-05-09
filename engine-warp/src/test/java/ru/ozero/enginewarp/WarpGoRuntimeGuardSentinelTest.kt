package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class WarpGoRuntimeGuardSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginewarp/RealWarpSdkBridge.kt")
        assertTrue(f.exists(), "RealWarpSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `attachTun acquires GoRuntimeGuard перед awgTurnOn`() {
        assertTrue(
            source.contains("GoRuntimeGuard.acquire"),
            "RealWarpSdkBridge обязан вызывать GoRuntimeGuard.acquire — без него libgojni " +
                "(URnetwork) и libam-go (AmneziaWG) могут оказаться в одном процессе → SIGABRT в " +
                "gcWriteBarrier (подтверждено pid=16101 timeline 2026-05-09).",
        )
        assertTrue(
            source.contains("Owner.AMNEZIA_WG"),
            "WARP должен acquire'ить slot AMNEZIA_WG — отличаемого от URNETWORK для конфликт-детекции.",
        )
    }

    @Test
    fun `attachTun отказывает при Conflict — не вызывает awgTurnOn`() {
        val attachBody = source.substringAfter("override suspend fun attachTun")
            .substringBefore("override suspend fun detachTun")
        val acquireIdx = attachBody.indexOf("GoRuntimeGuard.acquire")
        val turnOnIdx = attachBody.indexOf("awgRuntime.turnOn")
        assertTrue(acquireIdx >= 0, "GoRuntimeGuard.acquire не найден в attachTun")
        assertTrue(turnOnIdx >= 0, "awgRuntime.turnOn не найден в attachTun")
        assertTrue(
            acquireIdx < turnOnIdx,
            "GoRuntimeGuard.acquire обязан быть РАНЬШЕ awgRuntime.turnOn — иначе libam-go " +
                "может стартовать одновременно с активным libgojni и упасть. " +
                "acquireIdx=$acquireIdx turnOnIdx=$turnOnIdx",
        )
        assertTrue(
            attachBody.contains("Conflict"),
            "attachTun обязан обрабатывать Result.Conflict — иначе compile-time не enforce'ит " +
                "проверку и при Conflict код пройдёт дальше → краш.",
        )
    }
}
