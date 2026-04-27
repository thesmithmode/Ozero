package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Static contract: проверяет что в OzeroVpnService.kt нет известных
 * runtime-ловушек:
 *  - `runBlocking` на Main thread в onCreate/onDestroy/onStartCommand
 *    (ANR при cleanup → tunFd не закрывается → следующий VPN не стартует);
 *  - Foreground вызов на API 34+ передаёт ServiceInfo тип, совпадающий с
 *    манифестом (ManifestContractTest проверяет manifest-side, тут — service-side).
 */
class VpnServiceLifecycleContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `runBlocking не вызывается без обёртки в отдельный поток`() {
        // Допустимо: `runBlocking { ... }` внутри `Thread { ... }.start(); shutdown.join(timeout)`
        // (см. onDestroy). Запретно: голый runBlocking на Main thread.
        // Эвристика: каждый runBlocking должен быть либо внутри Thread{}-блока,
        // либо внутри корутины (suspend fun, scope.launch). Простой способ — если
        // в файле есть runBlocking, рядом должно быть Thread + join.
        val hasRunBlocking = source.contains("runBlocking")
        if (!hasRunBlocking) return
        assertTrue(
            source.contains("Thread(") && source.contains(".join("),
            "runBlocking присутствует в OzeroVpnService — обязательно обернуть в отдельный Thread с join+timeout, иначе ANR на Main thread (особенно в onDestroy при kill-switch). См. предыдущий регресс.",
        )
    }

    @Test
    fun `startForeground на API 34+ передаёт правильный тип FGS`() {
        val mentionsApi34 = source.contains("UPSIDE_DOWN_CAKE")
        val mentionsType = source.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE") ||
            source.contains("FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED")
        assertTrue(
            mentionsApi34 && mentionsType,
            "На Android 14+ (UPSIDE_DOWN_CAKE) startForeground обязан получить ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE — иначе MissingForegroundServiceTypeException.",
        )
    }

    @Test
    fun `tunFd close в onDestroy безусловный`() {
        // tunFd должен быть закрыт даже если pipeline.stop падает или висит —
        // иначе TUN-устройство в kernel блокирует следующий VPN.
        val hasOnDestroy = source.contains("override fun onDestroy")
        assertTrue(hasOnDestroy, "onDestroy должен быть переопределён.")
        // Простая эвристика: после onDestroy{ есть `tunFd?.close()` или `tunFd!!.close()`.
        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("private companion object")
        assertTrue(
            onDestroyBody.contains("tunFd?.close()") || onDestroyBody.contains("tunFd!!.close()"),
            "В onDestroy должно быть `tunFd?.close()` после остановки pipeline (kill-switch).",
        )
    }

    @Test
    fun `pipeline_stop защищён timeout-ом в shutdown path`() {
        // Если pipeline.stop виснет, в onDestroy нужен timeout — иначе main thread
        // блокирован, ANR, force-stop системы → tunFd может остаться открытым.
        val onDestroyBody = source.substringAfter("override fun onDestroy")
            .substringBefore("private companion object")
        assertFalse(
            onDestroyBody.contains("runBlocking { pipeline.stop()") ||
                Regex("runBlocking\\s*\\{\\s*pipeline\\.stop\\(\\)\\s*\\}").containsMatchIn(onDestroyBody),
            "Нельзя `runBlocking { pipeline.stop() }` без timeout — ANR на Main thread. Используй withTimeoutOrNull или Thread+join+timeout.",
        )
    }
}
