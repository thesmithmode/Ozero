package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkGoRuntimeGuardSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `start acquires GoRuntimeGuard перед инициализацией Sdk`() {
        assertTrue(
            source.contains("GoRuntimeGuard.acquire"),
            "RealUrnetworkSdkBridge обязан вызывать GoRuntimeGuard.acquire — без него " +
                "libgojni может стартовать после libam-go (WARP) и упасть в gcWriteBarrier.",
        )
        assertTrue(
            source.contains("Owner.URNETWORK"),
            "URnetwork должен acquire'ить slot URNETWORK — отличаемого от AMNEZIA_WG.",
        )
    }

    @Test
    fun `start отказывает при Conflict — не вызывает Sdk newDeviceLocalWithDefaults`() {
        val startBody = source.substringAfter("override suspend fun start")
            .substringBefore("private suspend fun runStartOnMain")
        val acquireIdx = startBody.indexOf("GoRuntimeGuard.acquire")
        assertTrue(acquireIdx >= 0, "GoRuntimeGuard.acquire не найден в start: \n$startBody")
        assertTrue(
            startBody.contains("Conflict"),
            "start обязан обрабатывать Result.Conflict — без guard'а libgojni JNI_OnLoad " +
                "конфликтует с libam-go signal handlers process-wide.",
        )
    }
}
