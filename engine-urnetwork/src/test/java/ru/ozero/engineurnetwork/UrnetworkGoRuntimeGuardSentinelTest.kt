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

    @Test
    fun `runStartOnMain early-return paths освобождают guard через cleanupOnFailure`() {
        val runStartBody = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop()")

        val ensureFailIdx = runStartBody.indexOf("runtime ensure failed")
        assertTrue(ensureFailIdx >= 0, "Сообщение 'runtime ensure failed' не найдено в runStartOnMain")
        val ensureCleanupIdx = runStartBody.indexOf("cleanupOnFailure", ensureFailIdx)
        val ensureReturnIdx = runStartBody.indexOf("return UrnetworkSdkBridge.StartResult.Failed", ensureFailIdx)
        assertTrue(
            ensureCleanupIdx in (ensureFailIdx + 1) until ensureReturnIdx,
            "ensure-fail ветка обязана вызывать cleanupOnFailure() ДО return — иначе " +
                "GoRuntimeGuard sticky остаётся URNETWORK навсегда.",
        )

        val nullStateIdx = runStartBody.indexOf("localState is null")
        assertTrue(nullStateIdx >= 0, "Сообщение 'localState is null' не найдено в runStartOnMain")
        val nullCleanupIdx = runStartBody.indexOf("cleanupOnFailure", nullStateIdx)
        val nullReturnIdx = runStartBody.indexOf("return UrnetworkSdkBridge.StartResult.Failed", nullStateIdx)
        assertTrue(
            nullCleanupIdx in (nullStateIdx + 1) until nullReturnIdx,
            "localState=null ветка обязана вызывать cleanupOnFailure() ДО return — иначе " +
                "guard sticky URNETWORK блокирует переключение на WARP.",
        )
    }
}
