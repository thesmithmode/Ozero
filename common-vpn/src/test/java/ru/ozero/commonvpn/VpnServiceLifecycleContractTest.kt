package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnServiceLifecycleContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `runBlocking не вызывается без обёртки в отдельный поток`() {
                                                val hasRunBlocking = source.contains("runBlocking")
        if (!hasRunBlocking) return
        assertTrue(
            source.contains("Thread(") && source.contains(".join("),
            "runBlocking присутствует в OzeroVpnService — обязательно обернуть в отдельный Thread с join+timeout, " +
                "иначе ANR на Main thread (особенно в onDestroy при kill-switch). См. предыдущий регресс.",
        )
    }

    @Test
    fun `startForeground на API 34+ передаёт правильный тип FGS`() {
        val mentionsApi34 = source.contains("UPSIDE_DOWN_CAKE")
        val mentionsType = source.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE") ||
            source.contains("FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED")
        assertTrue(
            mentionsApi34 && mentionsType,
            "На Android 14+ (UPSIDE_DOWN_CAKE) startForeground обязан получить " +
                "ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE — иначе MissingForegroundServiceTypeException.",
        )
    }

    @Test
    fun `tunFd close в onDestroy безусловный`() {
                        val hasOnDestroy = source.contains("override fun onDestroy")
        assertTrue(hasOnDestroy, "onDestroy должен быть переопределён.")
                val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("private companion object")
        assertTrue(
            onDestroyBody.contains("tunFd?.close()") || onDestroyBody.contains("tunFd!!.close()"),
            "В onDestroy должно быть `tunFd?.close()` после остановки pipeline (kill-switch).",
        )
    }

    @Test
    fun `pipeline_stop защищён timeout-ом в shutdown path`() {
                        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("private companion object")
        assertFalse(
            onDestroyBody.contains("runBlocking { pipeline.stop()") ||
                Regex("runBlocking\\s*\\{\\s*pipeline\\.stop\\(\\)\\s*\\}").containsMatchIn(onDestroyBody),
            "Нельзя `runBlocking { pipeline.stop() }` без timeout — ANR на Main thread. " +
                "Используй withTimeoutOrNull или Thread+join+timeout.",
        )
    }
}
