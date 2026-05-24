package ru.ozero.app.singbox

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxProcessLoggingSentinelTest {

    @Test
    fun `SingboxEngineService использует PersistentLoggers вместо Log-e для ошибок`() {
        val src = locateFile("singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt")
        val text = src.readText()
        assertFalse(
            text.contains("Log.e(") || text.contains("Log.w(") || text.contains("Log.i("),
            "SingboxEngineService НЕ должен использовать Log.e/w/i — логи идут только в logcat, " +
                "но не в файл UnifiedLogger. Сервис работает в :engine_singbox процессе, " +
                "LogcatReader фильтрует по --pid=mainPid → ошибки невидимы в UI. " +
                "Использовать PersistentLoggers.error/warn/info.",
        )
        assertTrue(
            text.contains("PersistentLoggers."),
            "SingboxEngineService обязан использовать PersistentLoggers для логирования",
        )
    }

    @Test
    fun `SingboxRuntime использует PersistentLoggers вместо Log-i`() {
        val src = locateFile("singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxRuntime.kt")
        val text = src.readText()
        assertFalse(
            text.contains("Log.e(") || text.contains("Log.w(") || text.contains("Log.i("),
            "SingboxRuntime НЕ должен использовать Log.* — используй PersistentLoggers",
        )
        assertTrue(
            text.contains("PersistentLoggers."),
            "SingboxRuntime обязан использовать PersistentLoggers для логирования",
        )
    }

    @Test
    fun `SingboxEngineService activeConnections не захардкожен в 0`() {
        val src = locateFile("singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt")
        val text = src.readText()
        assertFalse(
            text.contains("activeConnections = 0"),
            "SingboxEngineService НЕ должен захардкодить activeConnections = 0 — " +
                "это приводит к постоянному currentEngineDegraded=true (жёлтая кнопка) даже когда движок работает. " +
                "Использовать SingboxRuntime.isRunning() или трафик как индикатор.",
        )
    }

    private fun locateFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) {
                return File(dir, relativePath).also {
                    assertTrue(it.isFile, "Файл не найден: ${it.absolutePath}")
                }
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root не найден")
    }
}
